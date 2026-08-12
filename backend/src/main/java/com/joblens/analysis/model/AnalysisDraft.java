package com.joblens.analysis.model;

import java.util.List;

/**
 * Everything the model produces, and nothing it must not.
 *
 * <p>There is no score anywhere in this type, by design. The model decomposes requirements, judges
 * evidence and writes prose; the backend turns that into numbers. A provider that tried to return a
 * score would fail deserialization, because unknown fields are rejected.
 *
 * @param schemaVersion the contract this draft claims to satisfy, checked on the way in
 * @param limitations what the analysis could not determine, in the model's own words
 */
public record AnalysisDraft(
        String schemaVersion,
        List<RequirementAssessment> requirementAssessments,
        List<SubfactorJudgement> subfactorJudgements,
        NarrativeAssessment roleAlignment,
        NarrativeAssessment seniorityAlignment,
        NarrativeAssessment realisticCompetitiveness,
        OpportunityValue opportunityValue,
        ResumePositioningAdvice resumePositioning,
        InterviewPreparation interviewPreparation,
        String finalRationale,
        List<String> limitations) {

    public static final String SCHEMA_VERSION = "analysis-draft/v1";

    public AnalysisDraft {
        requirementAssessments = List.copyOf(requirementAssessments);
        subfactorJudgements = List.copyOf(subfactorJudgements);
        limitations = List.copyOf(limitations);
    }

    public AnalysisDraft withRequirementAssessments(List<RequirementAssessment> replacement) {
        return new AnalysisDraft(schemaVersion, replacement, subfactorJudgements, roleAlignment,
                seniorityAlignment, realisticCompetitiveness, opportunityValue, resumePositioning,
                interviewPreparation, finalRationale, limitations);
    }

    public AnalysisDraft withLimitations(List<String> replacement) {
        return new AnalysisDraft(schemaVersion, requirementAssessments, subfactorJudgements, roleAlignment,
                seniorityAlignment, realisticCompetitiveness, opportunityValue, resumePositioning,
                interviewPreparation, finalRationale, replacement);
    }
}
