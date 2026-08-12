import { vi } from 'vitest';

/**
 * Stubs the one network call the client makes.
 *
 * A mock-server library would be more faithful, but the client is a thin fetch wrapper and every
 * request goes through it, so stubbing fetch keeps the tests fast and the dependency list short.
 */

export interface StubbedCall {
  readonly path: string;
  readonly body: unknown;
}

export function stubApi(responses: Record<string, { status?: number; body: unknown }>) {
  const calls: StubbedCall[] = [];

  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const path = typeof input === 'string' ? input
      : input instanceof URL ? input.href
        : input.url;
    const match = Object.keys(responses).find((key) => path.endsWith(key));
    if (!match) {
      throw new Error(`No stubbed response for ${path}`);
    }

    let parsed: unknown = null;
    if (typeof init?.body === 'string') {
      parsed = JSON.parse(init.body);
    } else if (init?.body instanceof FormData) {
      parsed = { file: (init.body.get('file') as File | null)?.name ?? null };
    }
    calls.push({ path: match, body: parsed });

    const stubbed = responses[match];
    return Promise.resolve(new Response(JSON.stringify(stubbed?.body ?? {}), {
      status: stubbed?.status ?? 200,
      headers: { 'Content-Type': 'application/json' },
    }));
  }));

  return calls;
}

export const RESUME_EXTRACTION = {
  schemaVersion: 'resume-extraction/v1',
  extractionId: 'abc12345',
  reviewStatus: 'REVIEW_REQUIRED',
  evidenceAbsencePolicy: 'MUST_BE_UNKNOWN',
  rawText: 'Alex Morgan\nSenior Software Engineer, Northwind Systems',
  pages: [{ pageNumber: 1, charCount: 52, hasImages: false }],
  candidateProfile: {
    summary: 'Software engineer with six years of experience.',
    skills: [],
    workExperiences: [{
      id: 'exp-1',
      company: 'Northwind Systems',
      title: 'Senior Software Engineer',
      location: null,
      dates: { rawText: 'Mar 2021 - Present', startYearMonth: '2021-03', endYearMonth: null, current: true, parseConfidence: 'HIGH' },
      bullets: ['Shipped a payments service.'],
      provenance: null,
    }],
    education: [],
    projects: [],
    certifications: [],
  },
  extractionWarnings: [
    { code: 'POSSIBLE_MULTI_COLUMN', severity: 'HIGH', message: 'This layout looks like it uses columns.', page: null, count: null },
  ],
  stats: { pageCount: 1, charCount: 52, extractionMs: 12 },
};

export const CONFIRMED_RESUME = {
  schemaVersion: 'resume-confirmation/v1',
  reviewStatus: 'CONFIRMED',
  confirmedAt: '2026-08-11T12:00:00Z',
  contentFingerprint: 'f'.repeat(64),
  evidenceAbsencePolicy: 'MUST_BE_UNKNOWN',
  rawText: RESUME_EXTRACTION.rawText,
  candidateProfile: RESUME_EXTRACTION.candidateProfile,
  extractionWarnings: RESUME_EXTRACTION.extractionWarnings,
};

export const JOB_EXTRACTION = {
  schemaVersion: 'job-extraction/v1',
  extractionId: 'def67890',
  sourceType: 'TEXT',
  reviewStatus: 'REVIEW_REQUIRED',
  requirementSourcePolicy: 'FULL_TEXT_FALLBACK',
  rawText: 'Senior Backend Engineer at Acme Corp',
  jobPosting: {
    title: 'Senior Backend Engineer',
    company: 'Acme Corp',
    location: 'Toronto, ON',
    employmentType: 'Full-time',
    compensationText: null,
    responsibilities: ['Build backend services'],
    requiredQualifications: ['Strong Java', 'Hands-on AWS'],
    preferredQualifications: ['Kafka'],
    otherSections: [],
    sourceUrl: null,
  },
  extractionWarnings: [],
  stats: { charCount: 36, requiredCount: 2, preferredCount: 1, responsibilityCount: 1, extractionMs: 4 },
};

export const CONFIRMED_JOB = {
  schemaVersion: 'job-confirmation/v1',
  reviewStatus: 'CONFIRMED',
  confirmedAt: '2026-08-11T12:00:00Z',
  contentFingerprint: 'a'.repeat(64),
  requirementSourcePolicy: 'STRUCTURED_SECTIONS',
  rawText: JOB_EXTRACTION.rawText,
  jobPosting: JOB_EXTRACTION.jobPosting,
  extractionWarnings: [],
};
