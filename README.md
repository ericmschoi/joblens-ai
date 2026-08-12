# JobLens AI

Compare a resume with a job posting and get an evidence-based, explainable fit analysis.

> **Status: Phase 9 of 13.** The product works end to end locally with no API key: upload a PDF
> resume, add a job description by URL or paste, review and correct both, and read a scored,
> evidence-backed analysis. Packaging, CI and deployment are what remain. Nothing in this README
> describes behaviour that does not exist; unbuilt work is marked as planned.

## What it does

JobLens AI answers four questions about one candidate and one role:

1. How well does the candidate's documented experience match this role?
2. What direct matches, transferable experience, gaps and unknowns support that conclusion?
3. How should the candidate position their existing resume for this role, without inventing
   experience?
4. What should they prepare to explain in an interview?

It is deliberately not a keyword-overlap tool. It separates required from preferred qualifications,
a skill listed in a skills section from a skill demonstrated in real work, a direct match from
transferable experience, a confirmed **gap** from an **unknown**, and how well the candidate fits
the role from how attractive the opportunity is.

## The user flow

1. Upload a text-based PDF resume.
2. Provide the job description as **either** a job URL **or** pasted text.
3. Review and correct what was extracted from both documents. The version you confirm is what gets
   analysed.
4. Run the analysis.
5. Read the score interpretation guide, then the overall match, six category ratings with evidence,
   gaps, resume positioning advice and interview preparation.

The review step is mandatory by design, and enforced in the API rather than only in the UI. PDF
layouts and job pages are extracted imperfectly, and you should be able to see and fix that before
it distorts the result.

Extraction always returns `reviewStatus: REVIEW_REQUIRED`. A `200` means the file was read, not that
the reading was correct. Only `POST /api/v1/resumes/confirm`, with an explicit `confirmed: true`,
produces a confirmed resume — and that is the only representation analysis and scoring may consume.

Because structural parsing is heuristic, each result also states how far its structure can be
trusted.

A **resume** carries an `evidenceAbsencePolicy`:

- `MUST_BE_UNKNOWN` — unreviewed, or structurally uncertain. A requirement with no matching evidence
  must be reported as `UNKNOWN`. It cannot be judged a gap and cannot trigger a score ceiling.
- `MAY_BE_GAP` — confirmed and structurally clean, so absent evidence can mean what it appears to
  mean.

A **job posting** carries a `requirementSourcePolicy`:

- `FULL_TEXT_FALLBACK` — unreviewed, or its sections could not be separated reliably. Requirement
  decomposition must read the full text; the structured lists are a hint at best.
- `STRUCTURED_SECTIONS` — confirmed and cleanly sectioned.

A parser that misses a role produces the same observable state as a candidate who never held it, and
a posting whose headings went unrecognised looks the same as one with no requirements. Keeping those
apart is what stops a parsing bug from quietly capping someone's score, or from making a demanding
role look easy. Raw text is always returned alongside the structure, whatever happened to the parse,
and travels with the confirmed document so downstream analysis can quote the source rather than only
the derived structure.

## Ratings

Six weighted categories, each scored `0.0`–`5.0` to one decimal place.

| Category | Weight |
|---|---:|
| Core Technical Stack | 25% |
| Role & Responsibility Alignment | 20% |
| Experience Evidence | 15% |
| Seniority & Ownership | 15% |
| Required Qualification Coverage | 15% |
| Domain & Operating Environment | 10% |

The total is the weighted sum of the six displayed category values, so the number on screen can
always be recomputed by hand. A genuine gap in a core required qualification caps the total (3.4 for
one, 2.4 for two, 1.9 for three or more), and every applied cap is explained with the requirement
that triggered it. An `UNKNOWN` never counts as a gap and never triggers a cap.

Two silences are treated the same way. A requirement the resume does not answer leaves the
denominator rather than scoring zero, and a category the *posting* never mentioned is marked unrated
and given no weight, with the remaining categories renormalised. Each result carries both its
published weight and the weight actually applied, so the arithmetic stays checkable.

The model does semantic work only — decomposing requirements, classifying importance, mapping
evidence, judging match status. It never produces a score. All arithmetic, rounding and cap
enforcement happens in the backend.

Ratings describe documented fit. They are not predictions of whether you will be hired.

## Scope

**In scope for the local MVP:** PDF resume upload and validation, text extraction with quality
warnings, job description by URL or paste, public job page fetching behind a hardened URL boundary,
editable extraction previews, structured normalization, evidence-based analysis behind a
provider-neutral boundary, deterministic scoring, detailed category feedback, resume positioning,
interview preparation, and full error and empty states.

**Deferred:** OCR for scanned PDFs, DOCX and other formats, accounts, persistence, analysis history,
comparing several jobs at once, Markdown or PDF export, payments, applying to jobs, live company
research, user preference profiles, and any language other than English.

## Tech stack

| Layer | Choice |
|---|---|
| Backend | Java 25 LTS (Temurin 25.0.4+7), Spring Boot 4.1.0, Gradle 9.7.0 (Kotlin DSL) |
| Frontend | Node.js 24.19.0 LTS, React 19.2.8, TypeScript 6.0.3, Vite 8.2.1, CSS Modules + design tokens |
| Extraction | Apache PDFBox 3.0.8, jsoup 1.23.1, Apache HttpClient 5, Playwright for Java 1.62.0 |
| AI | Provider-neutral boundary; deterministic in-process fake is the default. No provider chosen yet. |
| Testing | JUnit 5, ArchUnit, Vitest, Testing Library, axe-core |

Every version is pinned and justified in [docs/versions.md](docs/versions.md), including the one
deliberate deviation from "newest stable" (TypeScript 6.0.3 rather than 7.0.2, because
typescript-eslint does not support TypeScript 7 yet).

Choosing a runtime AI provider and choosing hosting infrastructure are independent decisions. Both
are deferred until the relevant phase.

## Getting started

### Prerequisites

- **JDK 25** — Eclipse Temurin 25.0.4+7 or newer 25.x. Check with `java -version`.
- **Node.js 24.19.0 LTS or newer 24.x** with npm 11.17.0. Check with `node --version`.
- macOS, Linux or Windows. The helper scripts assume a POSIX shell.

Gradle itself is not required; the committed wrapper downloads the correct version.

### Run it

```bash
./scripts/dev.sh
```

The backend starts on <http://localhost:8080> and the frontend dev server on
<http://localhost:5173>, which proxies `/api` to the backend.

Run them separately if you prefer:

```bash
cd backend && ./gradlew bootRun
```

```bash
cd frontend && npm ci && npm run dev
```

### Try the API directly

The endpoint contract, the error catalogue and the operational limits are in
[docs/api.md](docs/api.md). A one-line example that needs nothing but a running backend:

```bash
curl -s -X POST http://localhost:8080/api/v1/job-descriptions/extract -H 'Content-Type: application/json' -d '{"text":"Senior Backend Engineer at Acme Corp. Responsibilities: build services in Java. Required Qualifications: 5+ years of backend development, strong Java and Spring Boot, experience designing REST APIs, hands-on experience with AWS. Preferred Qualifications: Kafka."}'
```

### Verify a change

```bash
./scripts/test-all.sh
```

That runs the backend build and tests, then the frontend typecheck, lint, tests and production
build. Individually:

```bash
cd backend && ./gradlew build
```

```bash
cd frontend && npm run typecheck && npm run lint && npm test && npm run build
```

Tests that drive a real browser are excluded from the default run because they download Chromium
and are far slower:

```bash
cd backend && ./gradlew browserTest
```

### Environment variables

None are required today. The default analysis provider is an in-process fake, so the application
runs end to end with no API key and no outbound AI traffic. When a real provider is added, its
credentials will come from environment variables or a secret manager, never from the repository and
never from the client bundle.

## Testing approach

Tests use fixtures rather than live third-party pages, so the suite is repeatable. Resume PDF
fixtures are generated programmatically at test time by `PdfFixtureFactory` — one-column,
two-column, design-heavy, multi-page with repeated headers, password protected, image-only, corrupt,
letter-spaced, and size and page-count boundaries — which keeps real personal documents out of the
repository entirely. Job page fixtures will be saved, version-tagged HTML for representative
applicant tracking systems. Any test that needs the live internet is tagged separately and excluded
from the default run.

Security tests are first-class: SSRF cases, unsafe redirect chains, malicious HTML, PDF edge cases,
prompt-injection strings embedded in documents, adversarial model output, and checks that document
content never reaches the logs.

Accessibility is tested rather than assumed. Component tests run axe-core, colour contrast is
computed directly from the design tokens for both light and dark schemes, and keyboard interaction
is exercised with user-event.

## Privacy and security

- No database. Resumes and job postings are never written to permanent storage.
- Document content never appears in logs, metrics labels or exception traces.
- Personal identifiers that analysis does not need — email, phone, street address — are redacted
  before any external AI call. Career evidence is preserved.
- Fetching a URL the user supplies is the highest-risk thing this product does, so it runs behind a
  dedicated boundary: `http` and `https` only, no credentials in the URL, ports restricted to 80 and
  443, DNS resolved and **every** returned address checked before connecting, and loopback, private,
  link-local, carrier-grade NAT, reserved, multicast and cloud-metadata addresses blocked for IPv4
  and IPv6 — including addresses smuggled inside IPv6 as IPv4-mapped, 6to4 or NAT64. The HTTP
  client's own resolver applies the same rules, so a name re-pointed after validation still cannot
  be connected to. Redirects are followed by hand and re-validated at every hop. Strict connect,
  response and total timeouts apply, the response size cap is enforced while streaming, and content
  types are restricted. No cookies, authorization headers or session state are ever forwarded, and
  a refusal never tells the client which address it refused.
- JobLens does not work around login walls, CAPTCHAs, bot checks or any other refusal. When a site
  declines automated access, the answer is to ask you to paste the job description, never to try
  harder. The crawler identifies itself honestly and is never disguised as a browser.
- Model output is validated against a schema before use, and quoted resume evidence is checked
  against the submitted resume text so fabricated quotes cannot reach the results page.
- Every response is `no-store`, `nosniff`, `DENY`-framed, `no-referrer`, and declares a
  content-security policy that permits nothing. A response holding extracted resume text has no
  business in a browser cache after the tab is closed.
- Cross-origin access is limited to configured origins and is never credentialed; the API issues and
  reads no cookies.
- JSON bodies are capped before they are parsed, by declared length and again while being read, so
  an oversized or length-less body cannot be buffered into memory first.
- That document content stays out of the logs is checked end to end, across the whole request path
  and its failure branches, not assumed from a code-review convention.

## Roadmap

| Phase | Work | State |
|---|---|---|
| 1 | Repository foundation, error contract, provider boundary, local scripts | **Done** |
| 2 | Secure PDF validation and extraction, resume preview contract | **Done** |
| 3 | Pasted job description extraction and normalization | **Done** |
| 4a | Safe URL fetching, JSON-LD and generic HTML extraction | **Done** |
| 4b | Applicant-tracking-system extractors and a controlled browser fallback | **Done** |
| 5 | Versioned analysis schema, provider boundary, prompt assets, output validation | **Done** |
| 6 | Evidence mapping and the deterministic score calculator | **Done** |
| 7 | Upload, job input and extraction-review UI | **Done** |
| 8 | Results UI: score guide, accessible decimal stars, evidence, guidance | **Done** |
| 9 | End-to-end hardening, calibration fixtures, documentation | **Done** |
| 10 | Real AI provider evaluation, then a single adapter | Planned |
| 11 | Docker packaging | Planned |
| 12 | GitHub Actions CI/CD | Planned |
| 13 | AWS architecture and deployment | Planned |

## Why I am building this

Job descriptions are long, repetitive and hard to interpret, and the same experience should be
presented differently depending on whether a role is backend, full-stack, platform, product or
reliability focused. This project came out of preparing for software engineering roles in Canada and
needing to map existing experience onto specific postings more clearly.

It is also a way to work through AI-assisted product engineering properly: structured model output,
explainable scoring, and security boundaries around file upload, arbitrary URL fetching and external
model calls.

The goal is never to fabricate experience. It is to understand and present real experience clearly.

## Notes on data

Testing uses synthetic resumes and job descriptions plus publicly available postings. A personal
resume may be used as a manual validation sample locally, but is never committed. No private company
data, confidential documents or personal information belongs in this repository.
