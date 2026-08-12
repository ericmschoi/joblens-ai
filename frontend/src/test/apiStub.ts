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

const NARRATIVE = { headline: 'Headline', detail: 'Detail sentence.', supportingEvidenceIds: [], concerns: [] };

function category(name: string, display: string, score: string, weight: string, rated = true) {
  return {
    category: name,
    displayName: display,
    rated,
    nominalWeight: weight,
    appliedWeight: rated ? weight : '0.0000',
    score,
    label: 'Strong Match',
    summary: `${display} was rated from 3 requirements.`,
    directEvidence: rated
      ? [{
        requirementId: 'req-1', importance: 'REQUIRED', status: 'STRONG_MATCH', relation: 'DIRECT',
        strength: 'STRONG', resumeQuote: 'Shipped a payments service in Java.',
        sourceLocator: 'EXPERIENCE', rationale: 'Directly stated.', grounded: true,
      }]
      : [],
    transferableEvidence: [],
    gaps: [],
    unknowns: rated
      ? [{
        requirementId: 'req-9', requirementText: 'Kubernetes in production',
        importance: 'REQUIRED', criticality: 'SUPPORTING',
        whyItMatters: 'The resume does not say either way, so this has not counted against you.',
        suggestedMitigation: 'Add a line if you have this experience.',
      }]
      : [],
    coverageRatio: '0.8000',
    scoreImpactExplanation: `The resume answers 80% of the weighted requirements, which maps to ${score}.`,
    improvementSuggestions: ['Say so explicitly if you have done this.'],
  };
}

export const ANALYSIS_RESPONSE = {
  schemaVersion: 'analysis/v1',
  scoreInterpretationGuide: [
    { label: 'Excellent Match', from: '4.5', to: '5.0', meaning: 'Strong direct experience.' },
    { label: 'Strong Match', from: '4.0', to: '4.4', meaning: 'Aligns well overall.' },
    { label: 'Good Match', from: '3.5', to: '3.9', meaning: 'Worth applying.' },
    { label: 'Moderate Match', from: '3.0', to: '3.4', meaning: 'A reach.' },
    { label: 'Weak Match', from: '2.0', to: '2.9', meaning: 'Several core gaps.' },
    { label: 'Poor Match', from: '0.0', to: '1.9', meaning: 'Substantially different.' },
  ],
  scoreCaveat: 'Ratings compare the job posting with experience explicitly documented in the resume.',
  analysis: {
    schemaVersion: 'fit-analysis/v1',
    totalMatchScore: '3.4',
    totalMatchLabel: 'Moderate Match',
    scoreConfidence: 'MEDIUM',
    applicationTier: 'REACH',
    recommendation: 'CONDITIONAL',
    eligibility: 'UNKNOWN',
    scoringAdjustments: [{
      ruleId: 'CORE_GAP_SINGLE',
      description: 'A core required qualification has no supporting evidence in the resume (AWS).',
      scoreBeforeAdjustment: '3.8',
      scoreAfterAdjustment: '3.4',
      triggeringRequirementIds: ['req-4'],
    }],
    categoryResults: [
      category('CORE_TECHNICAL_STACK', 'Core Technical Stack', '4.2', '0.2500'),
      category('ROLE_AND_RESPONSIBILITY_ALIGNMENT', 'Role & Responsibility Alignment', '4.0', '0.2000'),
      category('EXPERIENCE_EVIDENCE', 'Experience Evidence', '3.5', '0.1500'),
      category('SENIORITY_AND_OWNERSHIP', 'Seniority & Ownership', '3.2', '0.1500'),
      category('REQUIRED_QUALIFICATION_COVERAGE', 'Required Qualification Coverage', '3.8', '0.1500'),
      category('DOMAIN_AND_OPERATING_ENVIRONMENT', 'Domain & Operating Environment', '0.0', '0.1000', false),
    ],
    strongestMatches: [{
      requirementId: 'req-1', importance: 'REQUIRED', status: 'STRONG_MATCH', relation: 'DIRECT',
      strength: 'STRONG', resumeQuote: 'Shipped a payments service in Java.',
      sourceLocator: 'EXPERIENCE', rationale: 'Directly stated.', grounded: true,
    }],
    transferableMatches: [],
    criticalGaps: [{
      requirementId: 'req-4', requirementText: 'Hands-on experience with AWS',
      importance: 'REQUIRED', criticality: 'CORE',
      whyItMatters: 'The posting asks for this and the resume shows no supporting evidence.',
      suggestedMitigation: 'Be ready to explain how you would cover this.',
    }],
    minorGaps: [],
    unknownRequirements: [{
      requirementId: 'req-9', requirementText: 'Kubernetes in production',
      importance: 'REQUIRED', criticality: 'SUPPORTING',
      whyItMatters: 'The resume does not say either way, so this has not counted against you.',
      suggestedMitigation: 'Add a line if you have this experience.',
    }],
    roleAlignment: NARRATIVE,
    seniorityAlignment: NARRATIVE,
    realisticCompetitiveness: NARRATIVE,
    opportunityValue: {
      careerGrowth: { rating: 'MODERATE', basis: 'INFERRED_FROM_POSTING', explanation: 'Inferred from scope.' },
      compensation: { rating: 'UNKNOWN', basis: 'NOT_AVAILABLE', explanation: 'The posting does not state pay.' },
      companyOutlook: { rating: 'UNKNOWN', basis: 'NOT_AVAILABLE', explanation: 'JobLens does not research companies.' },
    },
    resumePositioning: {
      reorderSuggestions: ['Lead with the roles closest to this posting.'],
      emphasisSuggestions: ['Bring the matching work to the top of each role.'],
      terminologyAlignment: [{ resumeTerm: 'services', postingTerm: 'backend services', rationale: 'Match the wording.' }],
      deemphasizeSuggestions: [],
      faithfulRewrites: [],
    },
    interviewPreparation: {
      likelyQuestions: [{ question: 'Walk me through a project you owned.', whyAsked: 'Ownership matters here.', evidenceToUse: [] }],
      talkingPoints: ['Have one story per required qualification.'],
      gapsToExplain: [{ gap: 'AWS', suggestedFraming: 'Say plainly what your exposure is.' }],
      questionsToAsk: ['What does the first ninety days look like?'],
    },
    finalRationale: 'A reasonable application with one gap to prepare for.',
    limitations: ['A score ceiling was applied.', 'Ratings compare the job posting with experience explicitly documented in the resume.'],
  },
  analysisMetadata: {
    providerId: 'fake', promptVersion: 'v1',
    groundedEvidenceCount: 4, droppedEvidenceCount: 1, analysisMs: 42,
  },
};
