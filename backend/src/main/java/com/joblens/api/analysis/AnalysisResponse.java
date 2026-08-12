package com.joblens.api.analysis;

import com.joblens.analysis.AnalysisResult;
import com.joblens.scoring.model.FitAnalysis;
import com.joblens.scoring.model.ScoreLabel;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * The complete analysis, with the interpretation guide alongside it.
 *
 * <p>The guide travels with the result rather than living only in the UI, so any client showing a
 * score has the means to show what the score means, and the caveat that goes with it.
 */
public record AnalysisResponse(
        String schemaVersion,
        List<ScoreBand> scoreInterpretationGuide,
        String scoreCaveat,
        FitAnalysis analysis,
        Metadata analysisMetadata) {

    public static final String SCHEMA_VERSION = "analysis/v1";

    public record ScoreBand(String label, BigDecimal from, BigDecimal to, String meaning) {}

    public record Metadata(
            String providerId,
            String promptVersion,
            int groundedEvidenceCount,
            int droppedEvidenceCount,
            long analysisMs) {}

    public static AnalysisResponse from(AnalysisResult result, FitAnalysis analysis) {
        return new AnalysisResponse(
                SCHEMA_VERSION,
                Arrays.stream(ScoreLabel.values())
                        .map(label -> new ScoreBand(label.displayName(), label.lowerBound(),
                                label.upperBound(), label.meaning()))
                        .toList(),
                ScoreLabel.CAVEAT,
                analysis,
                new Metadata(result.providerId(), result.promptVersion(), result.groundedEvidenceCount(),
                        result.droppedEvidenceCount(), result.analysisMs()));
    }
}
