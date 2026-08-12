import { postFormData, postJson } from './client';
import type {
  AnalysisResponse,
  CandidateProfile,
  ConfirmedJobPosting,
  ConfirmedResume,
  ExtractionWarning,
  JobExtraction,
  JobPosting,
  ResumeExtraction,
} from './types';

/** One function per endpoint, so no component ever builds a URL or a payload itself. */

export function extractResume(file: File, signal?: AbortSignal): Promise<ResumeExtraction> {
  const form = new FormData();
  form.append('file', file);
  return postFormData<ResumeExtraction>('/resumes/extract', form, signal ? { signal } : {});
}

export function confirmResume(
  input: {
    rawText: string;
    candidateProfile: CandidateProfile;
    carriedWarnings: readonly ExtractionWarning[];
  },
  signal?: AbortSignal,
): Promise<ConfirmedResume> {
  return postJson<ConfirmedResume>('/resumes/confirm', { ...input, confirmed: true },
    signal ? { signal } : {});
}

export function extractJobDescription(
  source: { url: string } | { text: string },
  signal?: AbortSignal,
): Promise<JobExtraction> {
  return postJson<JobExtraction>('/job-descriptions/extract', source, signal ? { signal } : {});
}

export function confirmJobDescription(
  input: {
    rawText: string;
    jobPosting: JobPosting;
    carriedWarnings: readonly ExtractionWarning[];
  },
  signal?: AbortSignal,
): Promise<ConfirmedJobPosting> {
  return postJson<ConfirmedJobPosting>('/job-descriptions/confirm', { ...input, confirmed: true },
    signal ? { signal } : {});
}

export function runAnalysis(
  resume: ConfirmedResume,
  job: ConfirmedJobPosting,
  signal?: AbortSignal,
): Promise<AnalysisResponse> {
  return postJson<AnalysisResponse>('/analyses', {
    resume: {
      reviewStatus: resume.reviewStatus,
      contentFingerprint: resume.contentFingerprint,
      rawText: resume.rawText,
      candidateProfile: resume.candidateProfile,
      extractionWarnings: resume.extractionWarnings,
    },
    job: {
      reviewStatus: job.reviewStatus,
      contentFingerprint: job.contentFingerprint,
      rawText: job.rawText,
      jobPosting: job.jobPosting,
      extractionWarnings: job.extractionWarnings,
    },
  }, signal ? { signal } : {});
}
