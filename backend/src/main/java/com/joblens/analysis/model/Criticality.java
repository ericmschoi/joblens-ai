package com.joblens.analysis.model;

/**
 * Whether a requirement is central to the role or supporting.
 *
 * <p>Only {@link #CORE} requirements that are also {@link Importance#REQUIRED} can ever cap a total
 * score, so this is the most consequential judgement the model makes about a requirement.
 */
public enum Criticality {
    CORE,
    SUPPORTING
}
