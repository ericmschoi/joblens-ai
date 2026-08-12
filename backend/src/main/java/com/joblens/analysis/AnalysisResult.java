package com.joblens.analysis;

import com.joblens.analysis.model.AnalysisDraft;

/**
 * A validated, grounded analysis draft plus how it was produced.
 *
 * <p>No scores yet: the deterministic scorer turns this into a rated result. Keeping the two apart
 * means the model's judgement and the product's arithmetic can be reviewed independently.
 *
 * @param groundingFailureRatio share of quoted evidence that was not found in the resume, which
 *        lowers the confidence the user is shown
 */
public record AnalysisResult(
        AnalysisDraft draft,
        String providerId,
        String promptVersion,
        int groundedEvidenceCount,
        int droppedEvidenceCount,
        double groundingFailureRatio,
        long analysisMs) {}
