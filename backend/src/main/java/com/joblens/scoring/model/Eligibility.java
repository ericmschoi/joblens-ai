package com.joblens.scoring.model;

/**
 * Whether the candidate can hold the role at all.
 *
 * <p>{@link #NOT_ELIGIBLE} requires two things together: the posting states a hard legal or licence
 * condition, and the documents settle it. A resume that simply does not mention work authorisation
 * leaves this {@link #UNKNOWN}, which is the usual case and carries no penalty.
 */
public enum Eligibility {
    ELIGIBLE,
    NOT_ELIGIBLE,
    UNKNOWN
}
