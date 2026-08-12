package com.joblens.scoring.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * A cap that was applied, and why.
 *
 * <p>A capped score with no explanation is just a number the user cannot argue with. Every
 * adjustment names the requirements that triggered it and shows the score before and after.
 */
public record ScoringAdjustment(
        String ruleId,
        String description,
        BigDecimal scoreBeforeAdjustment,
        BigDecimal scoreAfterAdjustment,
        List<String> triggeringRequirementIds) {

    public ScoringAdjustment {
        triggeringRequirementIds = List.copyOf(triggeringRequirementIds);
    }
}
