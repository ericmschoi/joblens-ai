package com.joblens.scoring.model;

/**
 * How much the analysis itself should be trusted.
 *
 * <p>Not a second fit score. It reflects extraction quality, how complete the documents were, how
 * much came back unknown, and how much quoted evidence failed verification.
 */
public enum ScoreConfidence {
    HIGH,
    MEDIUM,
    LOW
}
