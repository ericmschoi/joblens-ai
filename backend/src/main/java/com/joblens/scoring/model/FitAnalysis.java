package com.joblens.scoring.model;

import com.joblens.analysis.model.EvidenceMatch;
import com.joblens.analysis.model.InterviewPreparation;
import com.joblens.analysis.model.NarrativeAssessment;
import com.joblens.analysis.model.OpportunityValue;
import com.joblens.analysis.model.RequirementAssessment;
import com.joblens.analysis.model.ResumePositioningAdvice;
import com.joblens.scoring.ScoringRubric;
import java.math.BigDecimal;
import java.util.List;

/**
 * The complete result: what was judged, what it scored, and why.
 *
 * <p>{@code totalMatchScore} is the weighted sum of the six displayed category scores, so a reader
 * can recompute it from what is on screen. Any cap was applied after that sum and is described in
 * {@code scoringAdjustments}.
 *
 * <p>{@code opportunityValue} sits here but is not an input to any score. How attractive a job is
 * has nothing to do with whether this candidate fits it.
 */
public record FitAnalysis(
        String schemaVersion,
        BigDecimal totalMatchScore,
        String totalMatchLabel,
        ScoreConfidence scoreConfidence,
        ApplicationTier applicationTier,
        Recommendation recommendation,
        Eligibility eligibility,
        List<ScoringAdjustment> scoringAdjustments,
        List<CategoryResult> categoryResults,
        List<RequirementAssessment> requirementAssessments,
        List<EvidenceMatch> strongestMatches,
        List<EvidenceMatch> transferableMatches,
        List<RequirementGap> criticalGaps,
        List<RequirementGap> minorGaps,
        List<RequirementGap> unknownRequirements,
        NarrativeAssessment roleAlignment,
        NarrativeAssessment seniorityAlignment,
        NarrativeAssessment realisticCompetitiveness,
        OpportunityValue opportunityValue,
        ResumePositioningAdvice resumePositioning,
        InterviewPreparation interviewPreparation,
        String finalRationale,
        List<String> limitations) {

    public static final String SCHEMA_VERSION = "fit-analysis/v1";

    public FitAnalysis {
        scoringAdjustments = List.copyOf(scoringAdjustments);
        categoryResults = List.copyOf(categoryResults);
        requirementAssessments = List.copyOf(requirementAssessments);
        strongestMatches = List.copyOf(strongestMatches);
        transferableMatches = List.copyOf(transferableMatches);
        criticalGaps = List.copyOf(criticalGaps);
        minorGaps = List.copyOf(minorGaps);
        unknownRequirements = List.copyOf(unknownRequirements);
        limitations = List.copyOf(limitations);
        totalMatchScore = ScoringRubric.round(totalMatchScore);
    }
}
