package com.joblens.scoring.model;

/**
 * How much of a stretch the role is, which is not the same question as how good the score is.
 *
 * <p>A junior role can score highly and still be {@link #SAFETY}; a senior role with one core gap
 * is a {@link #REACH} whatever the average says. Deriving this from the total alone would make it a
 * second name for the total, so it is derived from coverage, core gaps and seniority standing.
 */
public enum ApplicationTier {

    /** Core experience connects, but years, scale, environment or a specific technology do not. */
    REACH,

    /** The documented stack, responsibilities and level line up with the role. */
    TARGET,

    /** Most core requirements are met or exceeded, and the role sits at or below documented level. */
    SAFETY
}
