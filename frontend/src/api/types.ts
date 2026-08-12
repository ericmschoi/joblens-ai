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

// --- analysis result -----------------------------------------------------------------------------

export type ApplicationTier = 'REACH' | 'TARGET' | 'SAFETY';
export type Recommendation = 'STRONG_APPLY' | 'APPLY' | 'CONDITIONAL' | 'LOW_PRIORITY';
export type ScoreConfidence = 'HIGH' | 'MEDIUM' | 'LOW';
export type Eligibility = 'ELIGIBLE' | 'NOT_ELIGIBLE' | 'UNKNOWN';

export interface EvidenceMatch {
  readonly requirementId: string;
  readonly importance: 'REQUIRED' | 'PREFERRED';
  readonly status: MatchStatus;
  readonly relation: 'DIRECT' | 'TRANSFERABLE' | 'NONE';
  readonly strength: 'STRONG' | 'MODERATE' | 'WEAK' | 'NONE';
  readonly resumeQuote: string;
  readonly sourceLocator: string | null;
  readonly rationale: string;
  readonly grounded: boolean;
}

export interface RequirementGap {
  readonly requirementId: string;
  readonly requirementText: string;
  readonly importance: 'REQUIRED' | 'PREFERRED';
  readonly criticality: 'CORE' | 'SUPPORTING';
  readonly whyItMatters: string;
  readonly suggestedMitigation: string;
}

export interface CategoryResult {
  readonly category: string;
  readonly displayName: string;
  readonly rated: boolean;
  readonly nominalWeight: string;
  readonly appliedWeight: string;
  readonly score: string;
  readonly label: string;
  readonly summary: string;
  readonly directEvidence: readonly EvidenceMatch[];
  readonly transferableEvidence: readonly EvidenceMatch[];
  readonly gaps: readonly RequirementGap[];
  readonly unknowns: readonly RequirementGap[];
  readonly coverageRatio: string;
  readonly scoreImpactExplanation: string;
  readonly improvementSuggestions: readonly string[];
}

export interface ScoringAdjustment {
  readonly ruleId: string;
  readonly description: string;
  readonly scoreBeforeAdjustment: string;
  readonly scoreAfterAdjustment: string;
  readonly triggeringRequirementIds: readonly string[];
}

export interface NarrativeAssessment {
  readonly headline: string;
  readonly detail: string;
  readonly supportingEvidenceIds: readonly string[];
  readonly concerns: readonly string[];
}

export interface OpportunityAssessment {
  readonly rating: 'STRONG' | 'MODERATE' | 'LIMITED' | 'UNKNOWN';
  readonly basis: 'STATED_IN_POSTING' | 'INFERRED_FROM_POSTING' | 'NOT_AVAILABLE';
  readonly explanation: string;
}

export interface FitAnalysis {
  readonly schemaVersion: string;
  readonly totalMatchScore: string;
  readonly totalMatchLabel: string;
  readonly scoreConfidence: ScoreConfidence;
  readonly applicationTier: ApplicationTier;
  readonly recommendation: Recommendation;
  readonly eligibility: Eligibility;
  readonly scoringAdjustments: readonly ScoringAdjustment[];
  readonly categoryResults: readonly CategoryResult[];
  readonly strongestMatches: readonly EvidenceMatch[];
  readonly transferableMatches: readonly EvidenceMatch[];
  readonly criticalGaps: readonly RequirementGap[];
  readonly minorGaps: readonly RequirementGap[];
  readonly unknownRequirements: readonly RequirementGap[];
  readonly roleAlignment: NarrativeAssessment;
  readonly seniorityAlignment: NarrativeAssessment;
  readonly realisticCompetitiveness: NarrativeAssessment;
  readonly opportunityValue: {
    readonly careerGrowth: OpportunityAssessment;
    readonly compensation: OpportunityAssessment;
    readonly companyOutlook: OpportunityAssessment;
  };
  readonly resumePositioning: {
    readonly reorderSuggestions: readonly string[];
    readonly emphasisSuggestions: readonly string[];
    readonly terminologyAlignment: readonly {
      resumeTerm: string; postingTerm: string; rationale: string;
    }[];
    readonly deemphasizeSuggestions: readonly string[];
    readonly faithfulRewrites: readonly { before: string; after: string }[];
  };
  readonly interviewPreparation: {
    readonly likelyQuestions: readonly {
      question: string; whyAsked: string; evidenceToUse: readonly string[];
    }[];
    readonly talkingPoints: readonly string[];
    readonly gapsToExplain: readonly { gap: string; suggestedFraming: string }[];
    readonly questionsToAsk: readonly string[];
  };
  readonly finalRationale: string;
  readonly limitations: readonly string[];
}

export interface AnalysisResponse {
  readonly schemaVersion: string;
  readonly scoreInterpretationGuide: readonly {
    label: string; from: string; to: string; meaning: string;
  }[];
  readonly scoreCaveat: string;
  readonly analysis: FitAnalysis;
  readonly analysisMetadata: {
    providerId: string;
    promptVersion: string;
    groundedEvidenceCount: number;
    droppedEvidenceCount: number;
    analysisMs: number;
  };
}
