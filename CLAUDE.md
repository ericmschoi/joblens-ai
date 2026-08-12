# JobLens AI — working agreement

Read this before changing anything in this repository.

## What this product is

JobLens AI compares a PDF resume with a job posting and returns an evidence-based, explainable fit
analysis with detailed ratings. The full requirements are in [docs/product-brief.md](docs/product-brief.md),
which is the source of truth for scope. This file records how to work in the repository.

## Non-negotiables

1. **The product is English-only.** Every user-facing string — screen text, labels, placeholders,
   buttons, errors, status messages, accessibility labels, analysis output, API enums and field
   names, README and code comments — is English. Korean is used for development discussion only and
   must never appear in the product. `frontend/src/test/english-copy.test.ts` enforces this over the
   frontend source tree; keep it passing.
2. **The backend computes every score.** The AI model performs semantic work only: decomposing
   requirements, classifying importance, mapping evidence, and judging match status. It never
   returns a score. Category values, weighting, rounding and gap ceilings are deterministic Java.
3. **Documents are untrusted data, never instructions.** Resume and job-posting content is evidence.
   Instructions found inside a document are ignored and surfaced to the user as a warning.
   Imperative instruction text is also kept out of any derived structure, so a planted sentence
   cannot become a requirement the candidate is scored against.
4. **A refusal is accepted, never worked around.** When a site answers with a bot check, a CAPTCHA,
   a sign-in wall, a 403 or a 429, JobLens says so and asks the user to paste the description. It
   does not retry, disguise its user agent, hardcode per-domain workarounds, or use browser
   rendering to get past a refusal — rendering exists only for pages that legitimately need
   JavaScript, which `PageAccessAssessor` tells apart from a refusal.
5. **Nothing is persisted.** No database, no stored resumes or postings. The client holds the
   confirmed documents and posts them back for analysis.
6. **Never log document content.** Log the error code and trace id. Never the request body, the
   uploaded file, extracted text, or any fragment of it — including in exception messages and
   metrics labels.
7. **`UNKNOWN` is not `GAP`.** Missing information is not evidence of absence and never triggers a
   score ceiling.
8. **Unreviewed parsing is not evidence.** Both documents work the same way. Extraction always
   returns `REVIEW_REQUIRED`; only the matching `/confirm` endpoint produces a confirmed document.
   Scoring consumes the confirmed representations, never the extraction output, and verifies their
   `contentFingerprint`.
   - Resume: absent evidence may be judged `GAP` only when `ResumeEvidenceReliability.policyFor(...)`
     returns `MAY_BE_GAP`. Anything else is `UNKNOWN` and cannot trigger a critical-gap ceiling.
   - Job posting: the qualification lists may be treated as the complete requirements only when
     `JobPostingReliability.policyFor(...)` returns `STRUCTURED_SECTIONS`. Otherwise requirement
     decomposition must read the raw text too.
9. **Do not invent experience.** No suggestion may add a technology, metric, employer, credential or
   achievement that is not in the resume.

## Versions

Pinned and justified in [docs/versions.md](docs/versions.md): Java 25 LTS (Temurin 25.0.4+7),
Spring Boot 4.1.0, Gradle 9.7.0 with the Kotlin DSL, Node.js 24.19.0 LTS, React 19, TypeScript 6.0.3,
Vite 8. Do not bump a version without checking official compatibility documentation first, and
record the reason in `docs/versions.md`. Never use alpha, beta, RC, snapshot or nightly builds, and
never use floating tags in build files or Docker images.

The Spring Boot BOM manages Spring Framework, Tomcat, Jackson, JUnit and AssertJ. Do not pin those
directly.

## Layout

```
backend/    Spring Boot modular monolith
  error/      ErrorCode catalogue and ApiException (shared kernel, no HTTP types)
  api/        thin controllers, DTOs, RFC 9457 problem details
  resume/     PDF validation, extraction, normalization
  jobposting/ pasted text, safe URL fetching, ATS extractors, browser rendering
  document/   review status, fingerprint, provenance, warnings, injection detection, PII redaction
  analysis/   provider boundary, prompt assets, output validation, evidence grounding
  scoring/    rubric, category scorers, ceilings, tier and confidence policies
  config/     properties, limits, CORS
frontend/   Vite + React + TypeScript SPA
docs/       product brief, version decisions
scripts/    dev.sh, test-all.sh
```

Controllers stay thin. PDF parsing, web fetching, prompt construction and score calculation live in
separate packages and are not combined into one service.

## Commands

```bash
./scripts/test-all.sh        # everything that must pass
./scripts/dev.sh             # backend on :8080, frontend on :5173
```

```bash
cd backend && ./gradlew build            # compile + test (browser tests excluded)
cd backend && ./gradlew browserTest      # the @Tag("browser") tests, needs Chromium
cd backend && ./gradlew providerEval     # the provider acceptance suite; costs money off the fake
cd backend && ./gradlew bootRun          # run the API
cd backend && ./gradlew resolveAndLockAll --write-locks   # after changing dependencies
```

```bash
cd frontend && npm ci && npm run typecheck && npm run lint && npm test && npm run build
```

Always use `./gradlew`, never a system Gradle. Set `JAVA_HOME` to the Java 25 JDK; the scripts do
this automatically on macOS.

## Conventions

- Java compiles with `-Xlint:all -Werror`. Fix warnings; do not suppress them.
- Constructor injection only. `ArchitectureRulesTest` fails the build on field injection, on console
  output, and on the domain depending on the API layer. Domain code throws `ApiException` from
  `com.joblens.error`, which is why that package exists outside `api`.
- PDF fixtures are generated by `PdfFixtureFactory` at test time, never committed. Add a new
  generator there rather than checking in a binary.
- Frontend uses CSS Modules plus the design tokens in `frontend/src/styles/tokens.css`. Components
  reference tokens, never raw colour values, so contrast stays auditable in one place.
- Radix primitives are used only where accessibility is genuinely hard (disclosure, tabs, dialog,
  tooltip). Do not grow them into a component library.
- Accessibility is tested, not assumed: axe-core in component tests, contrast computed from the
  token file, keyboard interaction exercised with user-event.

## Repository etiquette

- Work on a `feat/phase-N-<slug>` branch, never directly on `main`.
- **Each phase ships itself.** Implement, verify, then commit, push, open a pull request, merge into
  `main`, and start the next phase without waiting. `main` is the record of completed phases, so no
  phase is built on top of another phase's unmerged branch. Report what changed, what was verified
  and how, and any real limitation — as part of shipping, not instead of it.
- **One gate remains: spending money on AWS.** Synthesise the infrastructure freely, but stop and
  get an explicit cost and deployment confirmation before deploying anything to a real account.
- Never commit real resumes or any personal document. `*.pdf` is git-ignored on purpose. Test
  fixtures are generated programmatically so the repository stays free of personal data.
- Secrets live in environment variables and never in the repository or the client bundle. The
  default analysis provider is the in-process fake, so the application runs with no API key and no
  outbound AI traffic.
- Do not claim a build, test or verification passed unless it was actually run.
