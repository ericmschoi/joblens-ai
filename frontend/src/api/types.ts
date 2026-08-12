/**
 * The API contracts, mirrored on the client.
 *
 * Written by hand rather than generated: the surface is small, and a hand-written mirror makes it
 * obvious when the backend contract changes, because the compiler stops here first.
 */

export type ReviewStatus = 'REVIEW_REQUIRED' | 'CONFIRMED';
export type WarningSeverity = 'INFO' | 'WARNING' | 'HIGH';
export type EvidenceAbsencePolicy = 'MUST_BE_UNKNOWN' | 'MAY_BE_GAP';
export type RequirementSourcePolicy = 'FULL_TEXT_FALLBACK' | 'STRUCTURED_SECTIONS';
export type MatchStatus = 'STRONG_MATCH' | 'PARTIAL_MATCH' | 'GAP' | 'UNKNOWN';

export interface ExtractionWarning {
  readonly code: string;
  readonly severity: WarningSeverity;
  readonly message: string;
  readonly page: number | null;
  readonly count: number | null;
}

export interface Provenance {
  readonly page: number | null;
  readonly section: string | null;
  readonly lineStart: number;
  readonly lineEnd: number;
  readonly sourceQuote: string | null;
}

export interface DateRange {
  readonly rawText: string;
  readonly startYearMonth: string | null;
  readonly endYearMonth: string | null;
  readonly current: boolean;
  readonly parseConfidence: 'HIGH' | 'LOW';
}

export interface WorkExperience {
  readonly id: string;
  readonly company: string | null;
  readonly title: string | null;
  readonly location: string | null;
  readonly dates: DateRange;
  readonly bullets: readonly string[];
  readonly provenance: Provenance | null;
}

export interface SkillMention {
  readonly id: string;
  readonly name: string;
  readonly origin: 'SKILLS_LIST' | 'SUMMARY' | 'WORK_EXPERIENCE' | 'PROJECT' | 'CERTIFICATION';
  readonly provenance: Provenance | null;
}

export interface CandidateProfile {
  readonly summary: string;
  readonly skills: readonly SkillMention[];
  readonly workExperiences: readonly WorkExperience[];
  readonly education: readonly {
    readonly id: string;
    readonly institution: string | null;
    readonly credential: string | null;
    readonly dates: DateRange;
    readonly provenance: Provenance | null;
  }[];
  readonly projects: readonly {
    readonly id: string;
    readonly name: string;
    readonly bullets: readonly string[];
    readonly provenance: Provenance | null;
  }[];
  readonly certifications: readonly {
    readonly id: string;
    readonly name: string;
    readonly provenance: Provenance | null;
  }[];
}

export interface JobPosting {
  readonly title: string | null;
  readonly company: string | null;
  readonly location: string | null;
  readonly employmentType: string | null;
  readonly compensationText: string | null;
  readonly responsibilities: readonly string[];
  readonly requiredQualifications: readonly string[];
  readonly preferredQualifications: readonly string[];
  readonly otherSections: readonly { readonly heading: string; readonly lines: readonly string[] }[];
  readonly sourceUrl: string | null;
}

export interface ResumeExtraction {
  readonly schemaVersion: string;
  readonly extractionId: string;
  readonly reviewStatus: ReviewStatus;
  readonly evidenceAbsencePolicy: EvidenceAbsencePolicy;
  readonly rawText: string;
  readonly pages: readonly { pageNumber: number; charCount: number; hasImages: boolean }[];
  readonly candidateProfile: CandidateProfile;
  readonly extractionWarnings: readonly ExtractionWarning[];
  readonly stats: { pageCount: number; charCount: number; extractionMs: number };
}

export interface ConfirmedResume {
  readonly schemaVersion: string;
  readonly reviewStatus: ReviewStatus;
  readonly confirmedAt: string;
  readonly contentFingerprint: string;
  readonly evidenceAbsencePolicy: EvidenceAbsencePolicy;
  readonly rawText: string;
  readonly candidateProfile: CandidateProfile;
  readonly extractionWarnings: readonly ExtractionWarning[];
}

export interface JobExtraction {
  readonly schemaVersion: string;
  readonly extractionId: string;
  readonly sourceType: 'TEXT' | 'URL';
  readonly reviewStatus: ReviewStatus;
  readonly requirementSourcePolicy: RequirementSourcePolicy;
  readonly rawText: string;
  readonly jobPosting: JobPosting;
  readonly extractionWarnings: readonly ExtractionWarning[];
  readonly fetchMetadata: {
    readonly finalUrl: string;
    readonly httpStatus: number;
    readonly strategy: string;
    readonly renderedWithBrowser: boolean;
    readonly redirectCount: number;
    readonly fetchMs: number;
  } | null;
  readonly stats: {
    charCount: number;
    requiredCount: number;
    preferredCount: number;
    responsibilityCount: number;
    extractionMs: number;
  };
}

export interface ConfirmedJobPosting {
  readonly schemaVersion: string;
  readonly reviewStatus: ReviewStatus;
  readonly confirmedAt: string;
  readonly contentFingerprint: string;
  readonly requirementSourcePolicy: RequirementSourcePolicy;
  readonly rawText: string;
  readonly jobPosting: JobPosting;
  readonly extractionWarnings: readonly ExtractionWarning[];
}
