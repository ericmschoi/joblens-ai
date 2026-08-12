package com.joblens.scoring;

import com.joblens.scoring.model.Eligibility;
import com.joblens.scoring.model.Recommendation;
import com.joblens.scoring.model.ScoreConfidence;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Turns the numbers into advice about what to do next.
 *
 * <p>Evaluated in priority order, so a disqualifying condition or a missing must-have wins over a
 * flattering average. Nothing here is a statement about the odds of being hired.
 */
@Component
public class RecommendationPolicy {

    private static final BigDecimal STRONG_APPLY_SCORE = new BigDecimal("4.2");
    private static final BigDecimal APPLY_SCORE = new BigDecimal("3.5");
    private static final BigDecimal LOW_PRIORITY_SCORE = new BigDecimal("2.5");

    public Recommendation decide(BigDecimal total, int coreGapCount, Eligibility eligibility,
            ScoreConfidence confidence) {

        if (eligibility == Eligibility.NOT_ELIGIBLE || total.compareTo(LOW_PRIORITY_SCORE) < 0) {
            return Recommendation.LOW_PRIORITY;
        }
        if (coreGapCount > 0 || confidence == ScoreConfidence.LOW
                || total.compareTo(APPLY_SCORE) < 0) {
            return Recommendation.CONDITIONAL;
        }
        return total.compareTo(STRONG_APPLY_SCORE) >= 0
                ? Recommendation.STRONG_APPLY
                : Recommendation.APPLY;
    }
}
