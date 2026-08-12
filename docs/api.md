# JobLens API

Version `v1`, base path `/api/v1`. JSON in, JSON out; the one exception is the resume upload, which
is `multipart/form-data`. Nothing is persisted, so there are no resource URLs to fetch later: each
response carries everything the next call needs, and the client holds it.

Every response carries `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`,
`Referrer-Policy: no-referrer`, `X-Frame-Options: DENY` and a `Content-Security-Policy` that permits
nothing. Cross-origin access is restricted to the origins in `joblens.cors.allowed-origins` and is
never credentialed.

## The flow

```
POST /resumes/extract          →  REVIEW_REQUIRED  ─┐
POST /resumes/confirm          →  CONFIRMED         │
                                                    ├→  POST /analyses  →  scored analysis
POST /job-descriptions/extract →  REVIEW_REQUIRED   │
POST /job-descriptions/confirm →  CONFIRMED        ─┘
```

Extraction never produces something that can be analysed. `200` means the document was read, not
that it was read correctly, which is why every extraction response is `REVIEW_REQUIRED` and carries
the raw text alongside the parsed structure. Only `/confirm`, with an explicit `confirmed: true`,
produces a confirmed document, and only a confirmed document may be analysed.

## Endpoints

### `POST /api/v1/resumes/extract`

`multipart/form-data` with one part, `file`: a text-based PDF, at most 10 MiB and 15 pages.

Returns `extractionId`, `reviewStatus: REVIEW_REQUIRED`, `evidenceAbsencePolicy`, `rawText`, a
per-page summary, a heuristic `candidateProfile`, `extractionWarnings` and `stats`.

`evidenceAbsencePolicy` is the important field. `MUST_BE_UNKNOWN` means absent evidence may only ever
be reported as `UNKNOWN`; `MAY_BE_GAP` means the parse is clean enough that a silence can be read as
an absence. It is recomputed server-side on every call and is never taken from the client.

### `POST /api/v1/resumes/confirm`

```json
{ "rawText": "…", "candidateProfile": { … }, "carriedWarnings": [ … ], "confirmed": true }
```

Returns `reviewStatus: CONFIRMED`, `confirmedAt`, a `contentFingerprint` (SHA-256 over the confirmed
content) and the confirmed representation. The corrections the user made are what gets analysed.

### `POST /api/v1/job-descriptions/extract`

Exactly one of:

```json
{ "text": "…" }          // 200–40,000 characters
{ "url": "https://…" }   // http/https, public address, ports 80 and 443
```

Returns `sourceType`, `reviewStatus: REVIEW_REQUIRED`, `requirementSourcePolicy`, `rawText`, a parsed
`jobPosting`, `extractionWarnings` and `stats`.

`requirementSourcePolicy` mirrors the resume's policy field: `STRUCTURED_SECTIONS` means the
qualification lists can be treated as the requirements; `FULL_TEXT_FALLBACK` means requirement
decomposition has to read the raw text as well.

When a site refuses automated access — a bot check, a CAPTCHA, a sign-in wall, a 403 or a 429 — the
API says so and asks for the description to be pasted. It does not retry, change its user agent, or
render the page to get around the refusal.

### `POST /api/v1/job-descriptions/confirm`

```json
{ "rawText": "…", "jobPosting": { … }, "carriedWarnings": [ … ], "confirmed": true }
```

Same shape of answer as the resume: `CONFIRMED`, `confirmedAt`, `contentFingerprint`, and the
confirmed representation.

### `POST /api/v1/analyses`

```json
{
  "resume": { "reviewStatus": "CONFIRMED", "contentFingerprint": "…", "rawText": "…",
              "candidateProfile": { … }, "extractionWarnings": [ … ] },
  "job":    { "reviewStatus": "CONFIRMED", "contentFingerprint": "…", "rawText": "…",
              "jobPosting": { … }, "extractionWarnings": [ … ] }
}
```

Both fingerprints are recomputed and compared. An unconfirmed document is rejected with
`REVIEW_NOT_CONFIRMED`; content that changed after confirmation is rejected with
`ANALYSIS_CONTENT_MISMATCH`.

The response carries `scoreInterpretationGuide`, `scoreCaveat`, the `analysis` itself and
`analysisMetadata` (`providerId`, `promptVersion`, `groundedEvidenceCount`, `droppedEvidenceCount`,
`analysisMs`).

Inside `analysis`: `totalMatchScore` and `totalMatchLabel`, `scoreConfidence`, `applicationTier`,
`recommendation`, `eligibility`, `scoringAdjustments`, six `categoryResults`, the requirement
assessments, `strongestMatches`, `transferableMatches`, `criticalGaps`, `minorGaps`,
`unknownRequirements`, three narrative assessments, `opportunityValue`, `resumePositioning`,
`interviewPreparation`, `finalRationale` and `limitations`.

Each `categoryResult` carries both `nominalWeight` and `appliedWeight`. A category the posting never
touched is `rated: false` with an applied weight of zero, and the remaining weights are
renormalised, so `totalMatchScore` is always the sum of `score × appliedWeight` over the six
categories on screen — rounded once, half up, to one decimal place.

The model never returns a number. Category values, the weighted total, rounding and every ceiling
are computed in Java, and a draft that contains a score is rejected by the output validator.

### `GET /actuator/health`

`{"status":"UP"}`. Details are never exposed.

## Errors

Every failure is an RFC 9457 problem detail with four extensions:

```json
{
  "type": "https://joblens.local/problems/pdf-image-only",
  "title": "This PDF has no readable text",
  "status": 422,
  "detail": "This looks like a scanned or image-only PDF…",
  "code": "PDF_IMAGE_ONLY",
  "recoveryAction": "Upload a text-based PDF exported from your word processor.",
  "fieldErrors": [],
  "traceId": "8f2a1c04"
}
```

Branch on `code`, never on `title` or `detail`. `traceId` is a correlation handle: it is safe to show
and safe to log, and it is never derived from document content.

| Group | Codes |
|---|---|
| Request shape | `VALIDATION_FAILED`, `REQUEST_NOT_READABLE`, `REQUEST_TOO_LARGE`, `UNSUPPORTED_MEDIA_TYPE`, `REQUEST_INVALID`, `RESOURCE_NOT_FOUND`, `METHOD_NOT_ALLOWED` |
| Resume | `FILE_MISSING`, `FILE_TOO_LARGE`, `FILE_TYPE_NOT_SUPPORTED`, `PDF_ENCRYPTED`, `PDF_CORRUPT`, `PDF_IMAGE_ONLY`, `PDF_TOO_MANY_PAGES`, `RESUME_TEXT_TOO_SHORT`, `REVIEW_NOT_CONFIRMED` |
| Job description | `JD_INPUT_AMBIGUOUS`, `JD_TEXT_TOO_SHORT`, `JD_TEXT_TOO_LONG`, `JD_EXTRACTION_INSUFFICIENT` |
| Job URL | `URL_INVALID`, `URL_SCHEME_NOT_ALLOWED`, `URL_BLOCKED`, `URL_TIMEOUT`, `URL_TOO_MANY_REDIRECTS`, `URL_RESPONSE_TOO_LARGE`, `URL_CONTENT_TYPE_UNSUPPORTED`, `URL_LOGIN_REQUIRED`, `URL_BLOCKED_BY_SITE`, `URL_FETCH_FAILED` |
| Analysis | `ANALYSIS_INPUT_TOO_LARGE`, `ANALYSIS_CONTENT_MISMATCH`, `AI_PROVIDER_UNAVAILABLE`, `AI_TIMEOUT`, `AI_OUTPUT_INVALID` |
| Generic | `RATE_LIMITED`, `INTERNAL_ERROR` |

## Limits

| Limit | Default | Where |
|---|---:|---|
| Resume file size | 10 MiB | `joblens.resume.max-file-size-bytes` |
| Resume pages | 15 | `joblens.resume.max-page-count` |
| Extracted characters | 500,000 | `joblens.resume.max-extracted-characters` |
| Pasted posting | 200–40,000 chars | `joblens.job-posting.*` |
| JSON request body | 4 MiB | `joblens.api.max-json-request-bytes` |
| Multipart request | 13 MiB | `spring.servlet.multipart.max-request-size` |
| Fetched page | 2 MiB, 10 s total | `joblens.job-fetch.*` |

Per-client rate limiting is deliberately not implemented in the application. A single-instance,
stateless service cannot enforce it honestly across replicas, so it belongs at the edge — the
`RATE_LIMITED` code exists so that an edge that does enforce it can answer in the same shape.

## Trying it by hand

```bash
curl -s -X POST http://localhost:8080/api/v1/job-descriptions/extract \
  -H 'Content-Type: application/json' \
  -d '{"text":"Senior Backend Engineer at Acme Corp. Responsibilities: build services in Java. Required Qualifications: 5+ years of backend development, strong Java and Spring Boot, experience designing REST APIs, hands-on experience with AWS. Preferred Qualifications: Kafka."}'
```

The extraction response is the input to `/job-descriptions/confirm`; add `"confirmed": true` and
rename `extractionWarnings` to `carriedWarnings`.
