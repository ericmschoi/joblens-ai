package com.joblens.analysis.model;

/**
 * How a requirement fares against the resume.
 *
 * <p>{@link #GAP} and {@link #UNKNOWN} are different claims and must never be collapsed. A gap says
 * the resume shows the candidate does not have this. An unknown says the resume does not say. Only
 * a gap may lower a score.
 */
public enum MatchStatus {
    STRONG_MATCH,
    PARTIAL_MATCH,
    GAP,
    UNKNOWN
}
