package com.joblens.scoring.model;

import java.math.BigDecimal;

/**
 * The interpretation shown above every score.
 *
 * <p>Bands are inclusive at the bottom. The copy is the product's promise about what a number means
 * and is shown to the user before any individual score, so nobody reads 3.6 without knowing what
 * 3.6 is meant to convey.
 */
public enum ScoreLabel {

    EXCELLENT_MATCH("Excellent Match", "4.5", "5.0",
            "Strong direct experience supports nearly all core requirements; this is a highly "
                    + "competitive documented fit."),
    STRONG_MATCH("Strong Match", "4.0", "4.4",
            "The candidate aligns well overall, with only limited or realistically addressable gaps."),
    GOOD_MATCH("Good Match", "3.5", "3.9",
            "Worth applying, but there are meaningful weaknesses to prepare for or explain."),
    MODERATE_MATCH("Moderate Match", "3.0", "3.4",
            "A reach based on transferable experience; tailored positioning is important."),
    WEAK_MATCH("Weak Match", "2.0", "2.9",
            "Several core gaps materially limit competitiveness."),
    POOR_MATCH("Poor Match", "0.0", "1.9",
            "The role direction is substantially different or critical minimum requirements are not met.");

    /** Shown next to the guide wherever scores appear. */
    public static final String CAVEAT =
            "Ratings compare the job posting with experience explicitly documented in the resume. "
                    + "Unlisted experience may not be reflected, and required qualifications are "
                    + "weighted more heavily than preferred qualifications.";

    private final String displayName;
    private final BigDecimal lowerBound;
    private final BigDecimal upperBound;
    private final String meaning;

    ScoreLabel(String displayName, String lowerBound, String upperBound, String meaning) {
        this.displayName = displayName;
        this.lowerBound = new BigDecimal(lowerBound);
        this.upperBound = new BigDecimal(upperBound);
        this.meaning = meaning;
    }

    public static ScoreLabel forScore(BigDecimal score) {
        for (ScoreLabel label : values()) {
            if (score.compareTo(label.lowerBound) >= 0) {
                return label;
            }
        }
        return POOR_MATCH;
    }

    public String displayName() {
        return displayName;
    }

    public BigDecimal lowerBound() {
        return lowerBound;
    }

    public BigDecimal upperBound() {
        return upperBound;
    }

    public String meaning() {
        return meaning;
    }
}
