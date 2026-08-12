package com.joblens.analysis.model;

/**
 * An ordinal judgement about one aspect of a category that requirements alone cannot express.
 *
 * <p>Experience quality and seniority are not lists of requirements; they are properties of the
 * whole resume. The model rates each aspect on a small ordinal scale and says why. The scorer turns
 * the ordinals into a category value — the model never sees a score.
 *
 * @param value 0 to 4 inclusive, where 0 is no support and 4 is fully supported
 */
public record SubfactorJudgement(
        CategoryName category,
        String subfactor,
        int value,
        String rationale) {

    public static final int MAX_VALUE = 4;

    public SubfactorJudgement {
        if (value < 0 || value > MAX_VALUE) {
            throw new IllegalArgumentException("Subfactor value must be between 0 and " + MAX_VALUE);
        }
    }
}
