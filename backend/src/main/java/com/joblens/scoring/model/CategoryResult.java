package com.joblens.scoring.model;

import com.joblens.analysis.model.CategoryName;
import com.joblens.analysis.model.EvidenceMatch;
import java.math.BigDecimal;
import java.util.List;

/**
 * One rated category, with everything needed to see where the number came from.
 *
 * @param score 0.0 to 5.0, one decimal place, and the value the total is computed from
 * @param rated false when the documents said too little about this category to rate it
 * @param nominalWeight the category's published share of the total
 * @param appliedWeight the share actually used. Zero for an unrated category, and the remaining
 *        categories are renormalised, so an employer's silence about a category cannot cost the
 *        candidate anything. The total is still the sum of score times applied weight, so a reader
 *        can recompute it from what is on screen.
 * @param coverageRatio the input to the anchor curve, exposed so the arithmetic is checkable
 * @param scoreImpactExplanation why this score and not the next one along
 */
public record CategoryResult(
        CategoryName category,
        String displayName,
        boolean rated,
        BigDecimal nominalWeight,
        BigDecimal appliedWeight,
        BigDecimal score,
        String label,
        String summary,
        List<EvidenceMatch> directEvidence,
        List<EvidenceMatch> transferableEvidence,
        List<RequirementGap> gaps,
        List<RequirementGap> unknowns,
        BigDecimal coverageRatio,
        String scoreImpactExplanation,
        List<String> improvementSuggestions) {

    public CategoryResult {
        directEvidence = List.copyOf(directEvidence);
        transferableEvidence = List.copyOf(transferableEvidence);
        gaps = List.copyOf(gaps);
        unknowns = List.copyOf(unknowns);
        improvementSuggestions = List.copyOf(improvementSuggestions);
    }
}
