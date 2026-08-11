package com.joblens.jobposting;

/**
 * How much the structured qualification lists can be relied on.
 *
 * <p>The counterpart of the resume's evidence-absence rule. A requirement the parser failed to
 * recognise is not a requirement the employer does not have, and an analysis that scored only the
 * recognised lists would quietly reward a badly formatted posting.
 */
public enum RequirementSourcePolicy {

    /**
     * The qualification sections were recognised cleanly. Requirement decomposition may work from
     * {@code requiredQualifications} and {@code preferredQualifications}.
     */
    STRUCTURED_SECTIONS,

    /**
     * The posting is unreviewed, or its sections could not be separated reliably. Requirement
     * decomposition must read the full text, and the structured lists are a hint at best.
     */
    FULL_TEXT_FALLBACK
}
