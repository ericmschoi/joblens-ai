# JobLens — Claude Code Master Implementation Prompt

Copy this entire prompt into Claude Code from the root of the JobLens repository.

---

You are the principal software engineer and product-minded technical lead for **JobLens**. Your job is to design and implement the product described below with production-quality engineering discipline while keeping the first release deliberately small, locally testable, explainable, and secure.

## 1. Mandatory working protocol

This first instruction is a **planning-only request**.

1. Inspect the current repository, including any `CLAUDE.md`, `AGENTS.md`, README, build files, package files, source code, tests, and existing conventions.
2. Do not create, modify, or delete code or project files in your first response.
3. Do not initialize external services, deploy infrastructure, create cloud resources, or make irreversible changes.
4. Return a detailed implementation plan using the exact response structure requested in Section 20.
5. Identify conflicts between this brief and the existing repository. Existing user work must be preserved.
6. Identify only genuinely decision-changing questions. For ordinary implementation details, make a recommendation and explain it rather than blocking on a question.
7. Wait for explicit approval before implementation.
8. After approval, implement one coherent phase at a time. Before each phase, state its scope and files. After each phase, report what changed, the commands run, test results, remaining risks, and the next proposed phase.
9. Use the normal implementation model for routine coding and tests. Escalate to the strongest reasoning model only for important architecture decisions or genuinely difficult debugging, then return to the normal implementation model.
10. Never claim that a test, build, extraction, AI call, or deployment succeeded unless you actually ran and verified it.

The intended Claude Code workflow is:

- Start planning with `claude --model opusplan --effort extra`.
- Produce a plan before writing code.
- After the plan is approved, use Sonnet for normal file-by-file implementation and testing.
- Switch to Opus only for difficult design or debugging problems.
- Return to Sonnet after the difficult problem is resolved.

The model used by Claude Code to build the application is **not** the same decision as the AI model that the finished JobLens product will call at runtime. Do not conflate them.

## 2. Product vision

JobLens is a general-purpose, public, English-language AI-assisted web application that helps job seekers compare a resume with a job posting.

The product should answer four practical questions:

1. How well does the candidate's documented experience match this role?
2. What direct matches, transferable experiences, gaps, and unknowns support that conclusion?
3. How should the candidate position the existing resume for this specific role without inventing experience?
4. What should the candidate prepare to explain in an interview?

JobLens must not be a superficial keyword-overlap tool. It must distinguish required qualifications from preferred qualifications, direct professional evidence from merely listed keywords, transferable experience from true direct matches, unknown information from genuine gaps, and job fit from the attractiveness of the opportunity.

The core product promise is:

> Upload a PDF resume, provide a job-posting URL or pasted job description, review and correct the extracted content, and receive an evidence-based, explainable fit analysis with detailed 5-star ratings.

## 3. Confirmed technology and delivery sequence

The following decisions are fixed unless an existing repository makes one impossible. If there is a conflict, explain it in the plan before changing anything.

### Frontend

- React
- TypeScript
- Responsive web UI
- English user-facing copy for the MVP

### Backend

- Java 21
- Spring Boot 3.x
- REST APIs
- Apache PDFBox for text-based PDF extraction
- Jsoup for HTML parsing and sanitization
- Java Playwright only as a controlled JavaScript-rendering fallback for job pages that cannot be extracted from static HTML

### Delivery order

1. Build and debug locally.
2. Calibrate extraction, prompts, scoring, and analysis quality locally using repeatable fixtures and evaluation cases.
3. Add Docker packaging after the local application is stable.
4. Add GitHub Actions CI/CD after local build and test commands are reliable.
5. Design and deploy to AWS only after local validation.

AWS deployment does **not** imply using Amazon Bedrock. Runtime AI provider selection must remain independent from infrastructure hosting.

### Intentionally undecided implementation choices

Do not silently lock in the following decisions. Recommend them in the planning response with tradeoffs and wait for approval where the choice meaningfully affects the architecture:

- Maven versus Gradle
- Frontend build tooling and component library
- Frontend state-management approach beyond ordinary React state
- Runtime AI provider and model
- Exact numerical caps for critical-gap scoring rules
- The final AWS topology

Prefer the smallest maintainable choice that supports the MVP. Do not add technology merely to make the architecture look sophisticated.

## 4. MVP user journey

Design the product around this sequence:

1. The user opens JobLens and sees a concise explanation of what it does and what the score means.
2. The user uploads a PDF resume.
3. The user provides the job description through one of two mutually exclusive input modes:
   - **Job URL**
   - **Paste Job Description**
4. The backend extracts and normalizes both inputs.
5. Before any fit analysis, the user sees separate extraction previews for the resume and job posting.
6. The user can correct the extracted text and important structured fields. The corrected version becomes the authoritative analysis input.
7. The user starts the analysis.
8. The UI shows a clear progress/loading state without pretending that completion is immediate.
9. The results page first explains the scoring scale, then displays the total fit result, category ratings, evidence, gaps, resume-positioning advice, and interview preparation.
10. In the MVP, data is ephemeral. Refreshing or closing the session may discard the extracted documents and result.

The extraction-review step is mandatory. PDF layouts and job pages can be parsed incorrectly, and the user must be able to identify and correct extraction mistakes before they affect the analysis.

## 5. MVP scope

### Required in the local MVP

- PDF resume upload
- Validation and text extraction for text-based PDFs
- Job description input through either URL or pasted text
- Public job-page fetching and content extraction
- Extraction warnings
- Editable extraction preview for both documents
- Normalization into structured candidate and job-posting models
- Evidence-based AI analysis
- Deterministic backend calculation of the final weighted rating
- Detailed category-level ratings and explanations
- Score-interpretation guide shown above the score
- Strongest matches, transferable matches, critical gaps, minor gaps, and unknowns
- Resume-positioning recommendations that never fabricate experience
- Interview-preparation guidance grounded in the resume and job description
- Loading, empty, validation, unsupported-file, extraction-failure, network-failure, AI-failure, and partial-result states
- Automated tests with representative PDF and ATS-page fixtures
- Local developer documentation and repeatable run/test commands

### Explicitly deferred

- OCR for scanned or image-only PDFs
- DOCX and other resume formats
- User accounts and authentication
- Database persistence
- Analysis history
- Comparing several jobs or resumes at once
- Resume or analysis export to Markdown/PDF
- Payments or subscriptions
- Direct job application, application tracking, or write access to third-party ATS systems
- AWS S3 upload flows
- Cloud malware-scanning pipeline
- Production AWS infrastructure
- Live market or company-research integrations
- User preference profiles such as desired role, preferred stack, avoided career paths, minimum salary, location, or maximum seniority
- Localization beyond English

An encrypted PDF, corrupt PDF, or PDF with no usable text must produce a clear, actionable message. For an image-only PDF, explain that OCR is not yet supported. Do not silently return an empty resume.

## 6. Resume extraction and normalization

The resume workflow must:

1. Receive a PDF through `multipart/form-data`.
2. Validate more than the filename extension:
   - actual file signature
   - allowed MIME type
   - configurable file-size limit
   - configurable page-count limit
   - encrypted/password-protected state
   - malformed or unreadable content
3. Extract text using PDFBox.
4. Preserve meaningful ordering, page boundaries, sections, bullets, and line breaks as far as practical.
5. Detect likely extraction-quality problems such as suspiciously little text, repeated headers/footers, broken words, and multi-column reading-order issues.
6. Return raw extracted text, a structured candidate profile, and extraction warnings.
7. Allow the user to edit the extraction before analysis.
8. Avoid permanent storage. If a temporary file is unavoidable, use a narrowly scoped temporary location and guarantee cleanup on success and failure.
9. Never write the raw resume, email address, phone number, street address, or full AI prompt to application logs.

Use a normalized model conceptually equivalent to:

```ts
type CandidateProfile = {
  summary: string;
  skills: string[];
  workExperiences: WorkExperience[];
  education: Education[];
  projects: Project[];
  certifications: string[];
  extractionWarnings: string[];
};
```

Define the nested types and provenance fields in the plan. Preserve enough source provenance to show where analysis evidence came from.

Before sending resume content to an external AI provider, remove or mask unnecessary personal identifiers such as email, phone number, and street address. Preserve career evidence needed for the analysis. Show the user what is being analyzed and explain that unlisted experience cannot be assessed.

## 7. Job-description ingestion and extraction

The job-description input must support:

### A. Pasted text

- Validate a sensible minimum and maximum length.
- Normalize whitespace and headings without destroying list semantics.
- Treat pasted content as untrusted data.

### B. Public job URL

Use this extraction order:

1. Parse `JobPosting` JSON-LD or other reliable structured job data when present.
2. Extract and sanitize ordinary HTML with Jsoup.
3. Apply isolated, testable ATS-specific extraction strategies for representative providers such as Greenhouse, Lever, Ashby, and Workday.
4. Use Java Playwright as a time-limited, resource-limited fallback only when JavaScript rendering is genuinely required.

Return a normalized model conceptually equivalent to:

```ts
type JobPosting = {
  title: string;
  company: string;
  location: string;
  employmentType?: string;
  compensationText?: string;
  responsibilities: string[];
  requiredQualifications: string[];
  preferredQualifications: string[];
  sourceUrl?: string;
  extractionWarnings: string[];
};
```

The extractor must never claim universal URL support. When a page requires login, presents CAPTCHA, blocks automated access, is no longer available, or cannot be extracted reliably, return an actionable fallback asking the user to paste the job description. Do not bypass access controls.

Never render fetched raw HTML in the frontend. Convert it to sanitized text and structured data.

## 8. URL-fetching security requirements

Treat arbitrary URL fetching as a high-risk server-side feature. The implementation plan must include a dedicated URL-validation and safe-fetch boundary.

At minimum:

- Allow only `http` and `https` URLs, preferring `https`.
- Reject embedded credentials and malformed URLs.
- Block localhost, loopback, link-local, private, reserved, multicast, and cloud-metadata addresses, including IPv4 and IPv6 forms.
- Resolve DNS safely and validate all resolved addresses before connecting.
- Revalidate every redirect target and enforce a small redirect limit.
- Protect against DNS rebinding as far as the chosen HTTP client allows.
- Block known metadata endpoints such as `169.254.169.254`.
- Set strict connection, response, and total timeouts.
- Enforce maximum response size before buffering the entire response.
- Restrict accepted content types.
- Limit concurrent fetches and browser-rendering work.
- Do not forward user cookies, authorization headers, or browser session state.
- Do not execute downloaded files.
- Provide safe error messages that do not expose internal network details.
- Add automated SSRF tests for direct URLs, redirects, IPv4 variants, IPv6, encoded hosts, and DNS-resolution cases.

## 9. Document normalization and trust boundaries

Resume and job-posting content are untrusted data, not instructions.

- Separate extraction from semantic normalization.
- Preserve the original extracted text alongside the editable normalized representation for user review, but only in ephemeral state.
- Track extraction warnings and provenance.
- Ignore instructions embedded in resumes or job descriptions that attempt to alter the model's behavior, reveal prompts, call tools, or change the output schema.
- Clearly delimit documents in model requests and state that their contents are evidence only.
- Use structured output/schema validation.
- Reject or safely repair malformed AI output; never pass unchecked output directly to the UI.
- Treat external AI responses as untrusted input at the backend boundary.

## 10. Fit-analysis principles

The analysis priority is:

1. Direct match to the core technical stack
2. Alignment with the actual role and responsibilities
3. Appropriate seniority and ownership level
4. Concrete evidence in the resume
5. Coverage of required qualifications and identification of material gaps
6. Transferability of adjacent experience
7. Domain and operating-environment alignment
8. Realistic competitiveness based only on available evidence

Company prestige, compensation, and general opportunity attractiveness must not inflate the resume-to-job fit score.

### Requirement interpretation

- Give substantially more weight to `required`, `must have`, and minimum qualifications than to `preferred`, `nice to have`, or `asset` qualifications.
- Correctly interpret alternatives such as “Java, C#, or Go”; satisfying a valid alternative must not be treated as multiple missing requirements.
- Respect phrases such as “or equivalent experience.”
- Do not mark a requirement as satisfied merely because a technology appears in a skills list. Prefer project or work evidence that shows actual use.
- Do not convert missing resume information into a confirmed deficiency.

Each atomic job requirement must receive exactly one status:

- `STRONG_MATCH`: direct, credible resume evidence supports the requirement
- `PARTIAL_MATCH`: adjacent or transferable evidence supports part of the requirement
- `GAP`: the requirement is clear and the resume provides no relevant evidence
- `UNKNOWN`: the resume does not contain enough information to decide

`UNKNOWN` must remain separate from `GAP` and must not automatically be penalized as though the candidate lacks the qualification.

### Seniority interpretation

Do not compare years mechanically. Evaluate:

- difference between requested and documented years
- complexity of delivered systems
- end-to-end delivery from requirements through release and production support
- ownership scope
- measurable outcomes
- leadership, mentoring, and cross-team decision-making when the role actually requires them
- whether a title such as Staff or Lead represents a genuinely different responsibility level

A candidate slightly below a stated year count can still be a partial or strong match when the documented scope supports it. A role centered on organization-wide architecture or team leadership must not receive a high seniority score from individual feature ownership alone.

### Transferable experience

Distinguish exact stack matches from transferable problem-solving evidence. Examples include authentication, REST API design, external partner integration, transactional systems, performance optimization, customer-facing production ownership, and incident/support experience. Do not equate “high transaction volume” with proven distributed-systems or cloud-native ownership unless the resume actually provides that evidence.

## 11. Rating categories and weights

Every category and the total must be displayed on a 0.0-to-5.0 scale in 0.1 increments.

| Category | Weight | What it measures |
|---|---:|---|
| Core Technical Stack | 25% | Direct professional alignment with the role's core technologies |
| Role & Responsibility Alignment | 20% | Alignment with backend, frontend, full-stack, or other actual work and responsibilities |
| Experience Evidence | 15% | Strength, specificity, and credibility of projects and outcomes supporting the requirements |
| Seniority & Ownership | 15% | Requested level, years, scope, end-to-end ownership, leadership, and autonomy |
| Required Qualification Coverage | 15% | Coverage of non-negotiable technical and non-technical minimum qualifications |
| Domain & Operating Environment | 10% | Domain relevance, cloud/production context, scale, reliability, and transferable environment experience |

The base total is:

```text
Total Match =
  Core Technical Stack × 0.25
  + Role & Responsibility Alignment × 0.20
  + Experience Evidence × 0.15
  + Seniority & Ownership × 0.15
  + Required Qualification Coverage × 0.15
  + Domain & Operating Environment × 0.10
```

Round the displayed result to one decimal place using one documented rounding rule. Preserve higher precision internally until the final display rounding.

The AI must not invent a total score by intuition. Prefer this responsibility split:

- The AI performs semantic work: decomposes requirements, classifies importance, maps evidence, identifies direct and transferable matches, identifies gaps and unknowns, and provides structured subfactor judgments with rationale.
- A backend scoring service validates the structured output, applies the approved rubric, calculates category values, applies weights, rounds the display value, and enforces critical-gap rules.

If some category scoring still requires an AI-supplied judgment, the model must follow explicit score anchors and return the evidence before the score. Validate every score and explanation server-side.

### Score interpretation shown to every user

This exact conceptual guide must appear above the total score and category results:

| Rating | Label | Meaning |
|---:|---|---|
| 4.5–5.0 | Excellent Match | Strong direct experience supports nearly all core requirements; this is a highly competitive documented fit |
| 4.0–4.4 | Strong Match | The candidate aligns well overall, with only limited or realistically addressable gaps |
| 3.5–3.9 | Good Match | Worth applying, but there are meaningful weaknesses to prepare for or explain |
| 3.0–3.4 | Moderate Match | A reach based on transferable experience; tailored positioning is important |
| 2.0–2.9 | Weak Match | Several core gaps materially limit competitiveness |
| 0.0–1.9 | Poor Match | The role direction is substantially different or critical minimum requirements are not met |

Also display this caveat near the guide:

> Ratings compare the job posting with experience explicitly documented in the resume. Unlisted experience may not be reflected, and required qualifications are weighted more heavily than preferred qualifications.

Do not present any rating as a hiring probability or guarantee.

### Category score explanation

Every category card must explain why its exact decimal value was assigned. A difference such as 4.2 versus 4.3 must be traceable to documented evidence and the scoring rubric, not arbitrary stylistic variation.

Use explicit score anchors and interpolation rules. Propose the detailed rubric in the planning response and include calibration tests. A reasonable anchor philosophy is:

- 5.0: exceptionally complete direct evidence with no material category gap
- around 4.0: strong alignment with a limited, non-critical gap
- around 3.0: meaningful transferable alignment but one or more material weaknesses
- around 2.0: limited partial alignment and several important gaps
- around 1.0: very little relevant evidence
- 0.0: no meaningful alignment for the category

Do not award false precision. The 0.1 display increment must be supported by a stable rubric and a score-impact explanation.

## 12. Critical-gap and eligibility rules

A weighted average must not allow unrelated strengths to hide a disqualifying requirement.

The system must support:

- `NOT_ELIGIBLE` for an explicit condition that makes application impossible, such as mandatory legal work eligibility or a mandatory license, but only when the job states it clearly and the candidate information supports a definite conclusion
- a configurable total-score ceiling when a truly core required skill has no relevant evidence
- a stricter configurable ceiling when two or more critical requirements are genuine gaps
- no automatic critical penalty for `UNKNOWN`
- a visible explanation of every cap or eligibility rule applied

The exact numerical ceilings were not previously finalized. In your planning response, propose a conservative, configurable policy with example calculations and test cases. Do not bury fixed cap values inside prompts or UI code, and do not implement them until the policy is approved.

## 13. Application tier, recommendation, and confidence

Keep these outputs separate from the star rating:

```text
Total Match: 4.4 / 5.0
Application Tier: TARGET
Recommendation: STRONG_APPLY
Analysis Confidence: HIGH
```

### Application tier

- `REACH`: core experience is transferable, but meaningful gaps exist in years, scale, cloud environment, seniority, or a specific technology; the opportunity may still be worth pursuing
- `TARGET`: the candidate's documented stack, responsibilities, and level align well with the role
- `SAFETY`: the candidate meets or exceeds most core requirements and the role appears at or below the documented capability level

Do not derive the tier from score thresholds alone. A lower-level role can be `SAFETY` even with a high score, while a high-value role with a specific gap can be `REACH`. Never imply that `SAFETY` guarantees an interview or offer.

### Recommendation

Use:

- `STRONG_APPLY`
- `APPLY`
- `CONDITIONAL`
- `LOW_PRIORITY`

Explain the recommendation with the strongest evidence, material risks, and any condition the user should verify before applying.

### Confidence

Use `HIGH`, `MEDIUM`, or `LOW`, based on extraction quality, document completeness, ambiguity in the job requirements, and the amount and specificity of resume evidence. Confidence is not another fit score.

## 14. Opportunity value must remain separate

Fit asks, “How well does this documented candidate match this job?” Opportunity value asks, “How attractive might this opportunity be?” Never include opportunity value in the weighted total.

Represent opportunity context separately, for example:

```ts
type OpportunityValue = {
  careerGrowth: Assessment;
  compensation: Assessment;
  companyOutlook: Assessment;
};
```

For the MVP:

- Assess compensation only from compensation data present in the job posting.
- Career-growth observations may be cautious inferences from responsibilities and scope and must be labeled as such.
- Without a reliable external source, company outlook must be `UNKNOWN`; do not fabricate live company knowledge.
- Live company research is deferred and must not be silently simulated by model memory.

## 15. Required analysis output

Design a versioned, structured contract conceptually equivalent to:

```ts
type FitAnalysis = {
  schemaVersion: string;
  totalMatchScore: number;
  totalMatchLabel: string;
  scoreConfidence: "HIGH" | "MEDIUM" | "LOW";
  applicationTier: "REACH" | "TARGET" | "SAFETY";
  recommendation: "STRONG_APPLY" | "APPLY" | "CONDITIONAL" | "LOW_PRIORITY";
  eligibility: "ELIGIBLE" | "NOT_ELIGIBLE" | "UNKNOWN";
  scoringAdjustments: ScoringAdjustment[];
  categoryResults: CategoryResult[];
  requirementAssessments: RequirementAssessment[];
  strongestMatches: EvidenceMatch[];
  transferableMatches: EvidenceMatch[];
  criticalGaps: RequirementGap[];
  minorGaps: RequirementGap[];
  unknownRequirements: RequirementGap[];
  roleAlignment: NarrativeAssessment;
  seniorityAlignment: NarrativeAssessment;
  realisticCompetitiveness: NarrativeAssessment;
  opportunityValue: OpportunityValue;
  resumePositioning: ResumePositioningAdvice;
  interviewPreparation: InterviewPreparation;
  finalRationale: string;
  limitations: string[];
};
```

Define the nested contracts during planning. Every evidence item should include:

- the related job requirement
- whether the requirement is required or preferred
- the status
- a concise rationale
- exact or tightly faithful resume evidence
- a source locator such as resume section, role, or project
- evidence strength
- whether the relationship is direct or transferable

Every category result must include:

- category name
- numeric score out of 5.0
- interpretation label
- short evaluation summary
- direct evidence
- transferable evidence
- gaps
- unknowns
- explicit explanation of score impact
- truthful resume-improvement suggestions

The model must never invent a resume quote, skill, metric, employer, project, degree, certification, work authorization, or preference.

## 16. Results-page information architecture

Use progressive disclosure while keeping the reasoning accessible. The order is mandatory:

1. **Score Interpretation Guide**
   - Show the full 0.0–5.0 interpretation table before any personal score.
   - Show the caveat that only documented resume experience is assessed.
2. **Overall Match**
   - Large, accessible star visualization
   - Exact numeric value such as `4.4 / 5.0`
   - Label such as `Strong Match`
   - Application tier
   - Recommendation
   - Confidence
   - Eligibility or critical-cap notice when applicable
3. **Detailed Overall Feedback**
   - why the role matches
   - strongest competitive evidence
   - most important gap
   - whether the gap appears addressable
   - whether applying is recommended
   - how the resume should be positioned
4. **Six Category Cards** in the fixed weighted order
5. **Strongest Matches**
6. **Transferable Matches**
7. **Critical Gaps**
8. **Minor Gaps**
9. **Unknowns**
10. **Resume Positioning**
11. **Interview Preparation**
12. **Opportunity Value**, clearly separated from fit
13. **Limitations and extraction warnings**

### Stars

- The numeric rating is authoritative; stars are a scanning aid.
- Render decimal fill accurately enough for a value such as 4.7. Do not reduce all decimals to a small set of Unicode half-star approximations.
- Use an accessible SVG/CSS implementation or another robust solution with an `aria-label` such as “4.7 out of 5.”
- Never rely on color or star fill alone.

### Detailed feedback behavior

For each category, users must be able to see:

- what matched directly
- what is transferable
- what is missing
- what is unknown
- why those findings produced the displayed score
- what truthful resume changes could improve positioning

Resume-positioning advice may recommend reordering, emphasis, terminology alignment, removal of irrelevant detail, or a faithful rewrite of existing evidence. It may not advise adding a technology or achievement the candidate does not have.

Interview preparation should include likely questions, evidence-backed talking points, gaps the candidate should be ready to address, and questions to ask the employer. It must not script fabricated stories.

## 17. Backend boundaries and API surface

Start from these public endpoints:

```text
POST /api/v1/resumes/extract
POST /api/v1/job-descriptions/extract
POST /api/v1/analyses
```

Expected responsibilities:

- `POST /api/v1/resumes/extract`
  - accepts `multipart/form-data`
  - validates the file
  - extracts text
  - normalizes the candidate profile
  - returns preview data and warnings
- `POST /api/v1/job-descriptions/extract`
  - accepts exactly one of `url` or `text`
  - safely fetches/extracts when URL mode is used
  - normalizes the job posting
  - returns preview data and warnings
- `POST /api/v1/analyses`
  - accepts the user-confirmed candidate profile and job posting
  - sanitizes/minimizes the payload
  - invokes the configured analysis provider
  - validates structured output
  - calculates category and total scores in the backend
  - applies approved cap rules
  - returns the complete analysis

Use clear, stable error codes and a consistent problem-details format. Distinguish validation errors, unsupported documents, extraction failures, blocked/unsafe URLs, upstream fetch failures, AI-provider failures, malformed AI output, timeout, and internal errors.

The backend design should have focused boundaries equivalent to:

- `ResumeExtractionService`
- `JobPostingFetchService`
- `JobContentExtractor`
- ATS-specific extractor strategies
- `DocumentNormalizationService`
- `PiiRedactionService`
- `FitAnalysisService`
- `RequirementAssessmentService`
- `FitScoreCalculator`
- `CriticalGapPolicy`
- `PromptTemplateService`
- `AnalysisProvider`
- output-schema validator

Names may change to fit repository conventions, but responsibilities must stay separated. Keep controllers thin. Do not put PDF parsing, web fetching, prompt construction, and score calculation into one service.

Version prompts and response schemas. Store prompt templates outside controller code and test them as application assets.

## 18. Runtime AI provider architecture

The runtime AI provider is deliberately undecided.

Requirements:

- Define a provider-neutral `AnalysisProvider` boundary.
- Provide a deterministic fake/stub provider for local UI development and automated tests.
- Keep provider credentials in environment variables or a secret manager, never source control.
- Do not couple domain contracts to one vendor's SDK types.
- Do not assume Bedrock merely because AWS deployment is planned.
- Do not implement multiple expensive production adapters before evaluation just to demonstrate abstraction.
- Prepare a small repeatable evaluation harness so representative resumes and job postings can compare candidate models on:
  - requirement extraction accuracy
  - evidence faithfulness
  - gap versus unknown classification
  - score stability
  - structured-output reliability
  - latency
  - token usage and estimated cost
- Select the first real provider only after local quality/cost evaluation and explicit approval.

The provider prompt must defend against prompt injection, demand evidence-first structured output, prohibit unsupported claims, and keep opportunity value separate from fit.

## 19. Frontend, quality, security, and testing requirements

### Frontend

- Use clear components for resume upload, JD mode selection, extraction preview/editing, analysis progress, scoring guide, overall result, category cards, evidence groups, and actionable guidance.
- Preserve corrected preview data when moving from extraction to analysis within the current session.
- Prevent double submission.
- Provide cancel/retry behavior where practical.
- Keep error messages actionable and avoid exposing internal implementation details.
- Make the interface responsive and keyboard accessible.
- Use semantic headings, labels, focus management, visible focus states, and sufficient contrast.
- Add reduced-motion support if animation is used.
- Do not render model output as unchecked HTML.

### Privacy and data minimization

- No database for the MVP.
- No permanent resume or job-description storage.
- No raw document content in logs, analytics, exception traces, or metrics labels.
- Redact unnecessary PII before external AI calls.
- Document what leaves the local application when a real AI provider is enabled.
- Use safe, bounded temporary-file handling and cleanup.
- Add secure defaults for CORS and file upload limits.
- Keep secrets out of client bundles.

### AI cost and reliability controls

- Set input-size and output-token limits.
- Avoid sending duplicated raw and normalized content when one is sufficient.
- Use timeouts and bounded retries only for retryable failures.
- Validate structured output and return a clear recovery path.
- Do not cache or persist resumes without an explicit future product decision.
- Record privacy-safe operational metrics such as duration, success/failure category, provider/model identifier, and token counts, but never document content.

### Required test coverage

#### Resume extraction fixtures

- simple one-column text PDF
- two-column PDF
- design-heavy text PDF
- multi-page PDF with repeated header/footer
- encrypted PDF
- image-only/scanned PDF
- corrupt or non-PDF upload with a `.pdf` filename
- file-size and page-count boundaries

#### Job extraction fixtures

- valid `JobPosting` JSON-LD
- generic HTML page
- representative saved Greenhouse page
- representative saved Lever page
- representative saved Ashby page
- representative saved Workday page
- dynamic page fallback behavior
- deleted page, login wall, CAPTCHA/block page, unsupported content type, oversized response, and timeout

Do not make ordinary automated tests depend on live third-party pages. Use versioned HTML fixtures and a small, separately marked manual/live smoke-test suite when appropriate.

#### Security tests

- SSRF cases listed in Section 8
- unsafe redirect chains
- malicious HTML/script content
- PDF edge cases and decompression/resource abuse protections where supported
- prompt-injection strings embedded in both resume and job description
- malformed or adversarial AI JSON
- overlong input and output
- log-safety checks where practical

#### Analysis and scoring tests

- required versus preferred weighting
- alternative requirements such as “Java or Go”
- `STRONG_MATCH`, `PARTIAL_MATCH`, `GAP`, and `UNKNOWN`
- unknowns not treated as confirmed gaps
- direct evidence versus skills-list-only mentions
- transferable experience
- seniority slightly below requested years with strong ownership
- Staff/Lead responsibility gap
- exact weighted-total calculations
- one-decimal rounding boundaries
- every approved critical-gap cap
- eligibility states
- score-label boundaries at 1.9/2.0, 2.9/3.0, 3.4/3.5, 3.9/4.0, and 4.4/4.5
- application tier not being a simple alias for total score
- opportunity value not affecting total fit
- repeated analysis stability on a calibration set

#### Frontend tests

- upload and validation states
- switching JD modes without accidental stale input
- preview editing
- loading, retry, and failure states
- score guide displayed before personal scores
- exact numeric ratings and accessible star labels
- category detail rendering
- cap/eligibility explanation
- keyboard navigation and core accessibility checks
- responsive layouts at representative viewport sizes

### Documentation

After implementation, the repository should explain:

- prerequisites
- local frontend and backend setup
- environment variables
- fake-provider mode
- real-provider setup only after one is approved
- how to run all tests
- fixture strategy
- scoring formula and critical-gap configuration
- privacy and security limitations
- Docker usage when that phase is reached
- future AWS deployment notes without pretending deployment exists

## 20. Required first response — no code

Your first response must contain these sections and nothing that writes or modifies files:

1. **Repository Assessment**
   - what currently exists
   - relevant conventions and constraints
   - conflicts with this brief
2. **Proposed Architecture**
   - frontend, backend, extraction, analysis, scoring, and trust boundaries
   - a concise request/data-flow diagram in text or Mermaid
3. **Key Technical Recommendations**
   - build tools and libraries
   - options considered and why the recommendation is appropriate for this MVP
4. **Domain and API Contracts**
   - proposed request/response shapes
   - analysis schema and error model
5. **Scoring Design**
   - evidence-to-category rubric
   - 0.1 scoring method
   - proposed critical-gap caps with examples
   - deterministic responsibilities versus AI responsibilities
6. **Security and Privacy Design**
   - PDF, SSRF, prompt injection, PII, logs, temporary files, and secrets
7. **Phased Implementation Plan**
   - small, reviewable phases
   - exact files or directories expected to be created/changed in each phase
   - dependencies and exit criteria for every phase
8. **Testing and Evaluation Plan**
   - automated test layers
   - fixtures
   - runtime-model evaluation strategy
9. **Risks and Mitigations**
10. **Decisions Requiring Approval**
    - only choices that materially affect implementation
11. **Recommended First Implementation Phase**

Do not provide placeholder implementation code in this planning response.

## 21. Suggested phase boundaries

You may refine these after inspecting the repository, but preserve the local-first order:

1. Repository foundation, shared API conventions, local run scripts, and fake analysis provider
2. Secure PDF validation/extraction and resume preview contract
3. Pasted JD extraction and normalization
4. Safe URL fetching, JSON-LD/generic extraction, then ATS strategies and controlled Playwright fallback
5. Versioned analysis schema, provider boundary, prompt assets, and output validation
6. Evidence mapping and deterministic score calculator with approved critical-gap policy
7. Input and extraction-review frontend
8. Results UI with scoring guide, accessible decimal stars, detailed evidence, and action sections
9. End-to-end integration, error recovery, security hardening, calibration fixtures, and documentation
10. Real AI provider evaluation and one approved provider adapter
11. Docker packaging
12. GitHub Actions CI/CD
13. AWS architecture and deployment as a separately approved later phase

Each phase must leave the repository buildable and testable. Do not start cloud work while local extraction or scoring behavior remains unstable.

## 22. Definition of done for the local MVP

The local MVP is complete only when all of the following are true:

- A user can upload a valid text-based PDF and see extracted content and warnings.
- Unsupported, encrypted, scanned, corrupt, oversized, or fake PDFs fail safely with actionable messages.
- A user can provide exactly one JD source: URL or pasted text.
- Representative public job pages can be extracted safely, and failures offer the paste-text fallback.
- The user can review and edit both extracted documents before analysis.
- The backend can analyze confirmed input through a provider-neutral boundary.
- AI output is schema-validated before use.
- The backend, not the frontend or an unconstrained model response, calculates and validates the final weighted score.
- The score interpretation guide appears above the total score.
- All six category scores display to one decimal place out of 5.0 with detailed evidence, gaps, unknowns, score-impact explanation, and truthful improvement advice.
- The total displays an accessible decimal star visualization and exact numeric value.
- Application tier, recommendation, confidence, eligibility, and any score cap are clearly explained and separate from the total score.
- Opportunity value is separate and does not affect fit.
- Resume-positioning and interview-preparation advice is grounded in documented evidence.
- No resume or JD is permanently stored.
- Sensitive document content is absent from logs.
- SSRF, prompt-injection, extraction, scoring, API, frontend, and accessibility tests pass.
- The project can be started and tested locally from documented commands.
- Runtime AI provider selection and AWS deployment remain explicitly independent decisions.

## 23. Engineering principles

- Favor explainability over a deceptively precise black-box score.
- Favor user-correctable extraction over silent parser confidence.
- Favor a small cohesive modular monolith over premature distributed architecture.
- Favor stable structured contracts over passing free-form model prose through the system.
- Favor deterministic scoring and validation where possible; use AI for semantic comparison, not arithmetic.
- Favor truthful evidence over keyword stuffing.
- Favor explicit `UNKNOWN` over unsupported certainty.
- Favor fixture-based repeatability over fragile live integration tests.
- Favor privacy and security boundaries from the first local version.
- Do not overengineer, but do not defer security controls required by PDF upload, arbitrary URL fetching, or external AI calls.

Now inspect the repository and return only the planning response required by Section 20. Do not modify files until I explicitly approve the plan.

