package com.joblens.scoring;

import com.joblens.analysis.model.EvidenceRelation;
import com.joblens.analysis.model.EvidenceStrength;
import com.joblens.analysis.model.Importance;
import com.joblens.analysis.model.MatchStatus;
import com.joblens.analysis.model.RequirementAssessment;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * The published rubric: how a judgement about a requirement becomes a number.
 *
 * <p>Every constant here is a product decision, not a tuning parameter. They are gathered in one
 * class so that "why 4.2 and not 4.3" has an answer a person can follow, and so that changing the
 * answer is a visible change.
 *
 * <p>The model never sees any of this. It says what it found; this decides what that is worth.
 */
public final class ScoringRubric {

    private ScoringRubric() {}

    // --- how much a requirement counts -----------------------------------------------------------

    /** A required qualification counts nearly three times what a preferred one does. */
    public static final BigDecimal WEIGHT_REQUIRED = new BigDecimal("1.00");
    public static final BigDecimal WEIGHT_PREFERRED = new BigDecimal("0.35");

    /** Core requirements are what the role is actually about, so they carry half again as much. */
    public static final BigDecimal MULTIPLIER_CORE = new BigDecimal("1.50");
    public static final BigDecimal MULTIPLIER_SUPPORTING = BigDecimal.ONE;

    // --- how much credit a judgement earns -------------------------------------------------------

    public static final BigDecimal CREDIT_STRONG_MATCH = BigDecimal.ONE;
    public static final BigDecimal CREDIT_PARTIAL_DIRECT = new BigDecimal("0.55");
    public static final BigDecimal CREDIT_PARTIAL_TRANSFERABLE = new BigDecimal("0.45");
    public static final BigDecimal CREDIT_GAP = BigDecimal.ZERO;

    public static final BigDecimal STRENGTH_STRONG = BigDecimal.ONE;
    public static final BigDecimal STRENGTH_MODERATE = new BigDecimal("0.90");
    public static final BigDecimal STRENGTH_WEAK = new BigDecimal("0.75");

    /**
     * Coverage to score, interpolated between anchors.
     *
     * <p>A straight multiplication by five is harsh in the middle and generous at the top. These
     * anchors say what the product means: near-total direct evidence is a five, a strong showing
     * with only minor gaps is a four, meaningful transferable alignment with a real weakness is a
     * three.
     */
    private static final List<Anchor> ANCHORS = List.of(
            new Anchor("0.00", "0.0"),
            new Anchor("0.20", "1.0"),
            new Anchor("0.40", "2.0"),
            new Anchor("0.60", "3.0"),
            new Anchor("0.80", "4.0"),
            new Anchor("0.95", "4.7"),
            new Anchor("1.00", "5.0"));

    private record Anchor(BigDecimal coverage, BigDecimal score) {
        Anchor(String coverage, String score) {
            this(new BigDecimal(coverage), new BigDecimal(score));
        }
    }

    /** @return the display score for a coverage ratio, rounded to one decimal place */
    public static BigDecimal scoreForCoverage(BigDecimal coverage) {
        BigDecimal clamped = coverage.max(BigDecimal.ZERO).min(BigDecimal.ONE);

        for (int i = 1; i < ANCHORS.size(); i++) {
            Anchor lower = ANCHORS.get(i - 1);
            Anchor upper = ANCHORS.get(i);
            if (clamped.compareTo(upper.coverage()) <= 0) {
                BigDecimal span = upper.coverage().subtract(lower.coverage());
                BigDecimal position = clamped.subtract(lower.coverage())
                        .divide(span, 10, RoundingMode.HALF_UP);
                BigDecimal rise = upper.score().subtract(lower.score());
                return round(lower.score().add(position.multiply(rise)));
            }
        }
        return new BigDecimal("5.0");
    }

    public static BigDecimal weightOf(RequirementAssessment assessment) {
        BigDecimal importance = assessment.importance() == Importance.REQUIRED
                ? WEIGHT_REQUIRED
                : WEIGHT_PREFERRED;
        return importance.multiply(assessment.isCoreRequirement()
                || assessment.criticality() == com.joblens.analysis.model.Criticality.CORE
                ? MULTIPLIER_CORE
                : MULTIPLIER_SUPPORTING);
    }

    /**
     * @return 0 to 1, or {@code null} when the requirement is unknown and therefore excluded from
     *         the ratio entirely rather than counted as a zero
     */
    public static BigDecimal creditFor(RequirementAssessment assessment) {
        if (assessment.status() == MatchStatus.UNKNOWN) {
            return null;
        }
        BigDecimal base = switch (assessment.status()) {
            case STRONG_MATCH -> CREDIT_STRONG_MATCH;
            case PARTIAL_MATCH -> assessment.relation() == EvidenceRelation.TRANSFERABLE
                    ? CREDIT_PARTIAL_TRANSFERABLE
                    : CREDIT_PARTIAL_DIRECT;
            case GAP -> CREDIT_GAP;
            case UNKNOWN -> BigDecimal.ZERO;
        };
        return base.multiply(strengthFactor(assessment.evidenceStrength()));
    }

    private static BigDecimal strengthFactor(EvidenceStrength strength) {
        return switch (strength) {
            case STRONG -> STRENGTH_STRONG;
            case MODERATE -> STRENGTH_MODERATE;
            case WEAK -> STRENGTH_WEAK;
            case NONE -> BigDecimal.ONE;
        };
    }

    /** One documented rounding rule, applied identically to every displayed value. */
    public static BigDecimal round(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP);
    }
}
