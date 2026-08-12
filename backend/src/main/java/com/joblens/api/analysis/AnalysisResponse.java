package com.joblens.api.analysis;

import com.joblens.analysis.AnalysisResult;
import com.joblens.analysis.model.AnalysisDraft;

/**
 * The analysis as it stands today: the model's validated, grounded judgement.
 *
 * <p>Scores are not here yet. They are computed deterministically from this draft in the next
 * phase, and the response will grow to carry them.
 *
 * @param analysisMetadata how the result was produced, which is privacy-safe and useful for support
 */
public record AnalysisResponse(
        String schemaVersion,
        AnalysisDraft draft,
        Metadata analysisMetadata) {

    public static final String SCHEMA_VERSION = "analysis/v1";

    public record Metadata(
            String providerId,
            String promptVersion,
            int groundedEvidenceCount,
            int droppedEvidenceCount,
            long analysisMs) {}

    public static AnalysisResponse from(AnalysisResult result) {
        return new AnalysisResponse(
                SCHEMA_VERSION,
                result.draft(),
                new Metadata(result.providerId(), result.promptVersion(), result.groundedEvidenceCount(),
                        result.droppedEvidenceCount(), result.analysisMs()));
    }
}
