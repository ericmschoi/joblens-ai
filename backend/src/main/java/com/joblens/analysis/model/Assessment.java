package com.joblens.analysis.model;

/**
 * A judgement about the opportunity rather than the candidate.
 *
 * <p>{@code basis} is what keeps this honest. Career growth can be inferred from the posting;
 * company outlook cannot be known from a posting at all and stays {@link Rating#UNKNOWN} until
 * JobLens actually researches companies.
 */
public record Assessment(Rating rating, Basis basis, String explanation) {

    public enum Rating {
        STRONG,
        MODERATE,
        LIMITED,
        UNKNOWN
    }

    public enum Basis {
        STATED_IN_POSTING,
        INFERRED_FROM_POSTING,
        NOT_AVAILABLE
    }

    public static Assessment unknown(String explanation) {
        return new Assessment(Rating.UNKNOWN, Basis.NOT_AVAILABLE, explanation);
    }
}
