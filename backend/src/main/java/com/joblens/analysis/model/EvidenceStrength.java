package com.joblens.analysis.model;

/**
 * How convincing a piece of evidence is.
 *
 * <p>A technology named only in a skills list is capped at {@link #WEAK} by the scoring layer,
 * whatever the model claims, because a list is an assertion and a project is a demonstration.
 */
public enum EvidenceStrength {
    STRONG,
    MODERATE,
    WEAK,
    NONE
}
