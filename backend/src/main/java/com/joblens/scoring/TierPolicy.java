package com.joblens.scoring;

import com.joblens.scoring.model.ApplicationTier;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Decides how much of a stretch the role is.
 *
 * <p>Deliberately not a function of the total. A junior role a strong candidate clears easily
 * scores highly and is a {@link ApplicationTier#SAFETY}; a senior role with one core gap is a
 * {@link ApplicationTier#REACH} however good the average looks. Deriving this from the score would
 * make it a second name for the score and tell the user nothing new.
 */
@Component
public class TierPolicy {

    private static final BigDecimal SAFETY_COVERAGE = new BigDecimal("0.90");
    private static final BigDecimal TARGET_COVERAGE = new BigDecimal("0.75");

    /** Comfortably above the level the role is pitched at. */
    private static final BigDecimal SAFETY_SENIORITY_SCORE = new BigDecimal("4.5");

    /**
     * @param requiredCoverage share of the weighted required load the resume answers
     * @param seniorityScore the seniority category score, standing in for level relative to the role
     */
    public ApplicationTier decide(int coreGapCount, BigDecimal requiredCoverage, BigDecimal seniorityScore) {
        if (coreGapCount > 0) {
            return ApplicationTier.REACH;
        }
        if (requiredCoverage.compareTo(SAFETY_COVERAGE) >= 0
                && seniorityScore.compareTo(SAFETY_SENIORITY_SCORE) >= 0) {
            return ApplicationTier.SAFETY;
        }
        return requiredCoverage.compareTo(TARGET_COVERAGE) >= 0
                ? ApplicationTier.TARGET
                : ApplicationTier.REACH;
    }
}
