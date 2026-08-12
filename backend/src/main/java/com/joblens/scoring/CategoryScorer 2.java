package com.joblens.scoring;

import com.joblens.analysis.model.CategoryName;
import com.joblens.analysis.model.Importance;
import com.joblens.analysis.model.RequirementAssessment;
import com.joblens.analysis.model.SubfactorJudgement;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Turns judgements into a coverage ratio, and a coverage ratio into a category score.
 *
 * <p>Four categories are coverage-driven: they are made of requirements, and the score is how much
 * of the weighted requirement load the resume answers. Two are not — experience quality and
 * seniority are properties of the whole resume rather than a checklist — so those come from ordinal
 * subfactor judgements run through the same anchor curve.
 *
 * <p>Unknown requirements leave the denominator entirely. Counting them as zeros would let a
 * silence about the candidate lower their score, which is the thing the product most wants to avoid.
 */
@Component
public class CategoryScorer {

    /** Below this many contributing items, a coverage ratio is noise dressed up as precision. */
    static final int MINIMUM_ITEMS_FOR_A_SCORE = 2;

    private static final Map<String, BigDecimal> EXPERIENCE_SUBFACTOR_WEIGHTS = Map.of(
            "specificity", new BigDecimal("0.25"),
            "outcomes", new BigDecimal("0.20"),
            "depth", new BigDecimal("0.25"),
            "recency", new BigDecimal("0.15"),
            "consistency", new BigDecimal("0.15"));

    private static final Map<String, BigDecimal> SENIORITY_SUBFACTOR_WEIGHTS = Map.of(
            "yearsAlignment", new BigDecimal("0.20"),
            "systemComplexity", new BigDecimal("0.20"),
            "endToEndDelivery", new BigDecimal("0.20"),
            "ownershipScope", new BigDecimal("0.20"),
            "measurableOutcomes", new BigDecimal("0.10"),
            "leadership", new BigDecimal("0.10"));

    /**
     * @param coverage 0 to 1, or empty when too little evidence exists to say anything honest
     */
    public record Coverage(Optional<BigDecimal> ratio, int contributingItems, int unknownItems) {

        public boolean isScoreable() {
            return ratio.isPresent();
        }
    }

    /** Requirements whose primary category is this one, with alternative groups collapsed. */
    public Coverage coverageFor(CategoryName category, List<RequirementAssessment> assessments) {
        return coverageOf(assessments.stream()
                .filter(assessment -> assessment.primaryCategory() == category)
                .toList());
    }

    /**
     * Required coverage spans every required requirement, whatever category it belongs to. It is
     * the answer to "how much of what this employer insists on is actually there".
     */
    public Coverage requiredCoverage(List<RequirementAssessment> assessments) {
        return coverageOf(assessments.stream()
                .filter(assessment -> assessment.importance() == Importance.REQUIRED)
                .toList());
    }

    private Coverage coverageOf(List<RequirementAssessment> assessments) {
        List<RequirementAssessment> collapsed = collapseAlternatives(assessments);

        BigDecimal weightedCredit = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        int contributing = 0;
        int unknown = 0;

        for (RequirementAssessment assessment : collapsed) {
            BigDecimal credit = ScoringRubric.creditFor(assessment);
            if (credit == null) {
                unknown++;
                continue;
            }
            BigDecimal weight = ScoringRubric.weightOf(assessment);
            weightedCredit = weightedCredit.add(credit.multiply(weight));
            totalWeight = totalWeight.add(weight);
            contributing++;
        }

        if (contributing < MINIMUM_ITEMS_FOR_A_SCORE || totalWeight.signum() == 0) {
            return new Coverage(Optional.empty(), contributing, unknown);
        }
        return new Coverage(Optional.of(weightedCredit.divide(totalWeight, 10, RoundingMode.HALF_UP)),
                contributing, unknown);
    }

    /**
     * "Java, C# or Go" is one requirement met by any of the three. Keeping the best-answered member
     * stops a candidate who knows Java from being charged for two gaps.
     */
    private static List<RequirementAssessment> collapseAlternatives(List<RequirementAssessment> assessments) {
        Map<String, RequirementAssessment> best = new LinkedHashMap<>();
        List<RequirementAssessment> ungrouped = new java.util.ArrayList<>();

        for (RequirementAssessment assessment : assessments) {
            String group = assessment.alternativeGroupId();
            if (group == null || group.isBlank()) {
                ungrouped.add(assessment);
                continue;
            }
            best.merge(group, assessment, CategoryScorer::betterOf);
        }
        List<RequirementAssessment> collapsed = new java.util.ArrayList<>(ungrouped);
        collapsed.addAll(best.values());
        return collapsed;
    }

    private static RequirementAssessment betterOf(RequirementAssessment left, RequirementAssessment right) {
        BigDecimal leftCredit = Optional.ofNullable(ScoringRubric.creditFor(left)).orElse(BigDecimal.valueOf(-1));
        BigDecimal rightCredit = Optional.ofNullable(ScoringRubric.creditFor(right)).orElse(BigDecimal.valueOf(-1));
        return rightCredit.compareTo(leftCredit) > 0 ? right : left;
    }

    /**
     * @param roleRequiresLeadership when false the leadership subfactor is dropped and the remaining
     *        weights are renormalised, so a role that never asked for it cannot be scored down for
     *        its absence
     */
    public Coverage subfactorCoverage(CategoryName category, List<SubfactorJudgement> judgements,
            boolean roleRequiresLeadership) {

        Map<String, BigDecimal> weights = new HashMap<>(category == CategoryName.EXPERIENCE_EVIDENCE
                ? EXPERIENCE_SUBFACTOR_WEIGHTS
                : SENIORITY_SUBFACTOR_WEIGHTS);
        if (!roleRequiresLeadership) {
            weights.remove("leadership");
        }

        BigDecimal weighted = BigDecimal.ZERO;
        BigDecimal applied = BigDecimal.ZERO;
        int contributing = 0;

        for (SubfactorJudgement judgement : judgements) {
            if (judgement.category() != category) {
                continue;
            }
            BigDecimal weight = weights.get(judgement.subfactor());
            if (weight == null) {
                continue;
            }
            BigDecimal normalised = BigDecimal.valueOf(judgement.value())
                    .divide(BigDecimal.valueOf(SubfactorJudgement.MAX_VALUE), 10, RoundingMode.HALF_UP);
            weighted = weighted.add(normalised.multiply(weight));
            applied = applied.add(weight);
            contributing++;
        }

        if (contributing < MINIMUM_ITEMS_FOR_A_SCORE || applied.signum() == 0) {
            return new Coverage(Optional.empty(), contributing, 0);
        }
        return new Coverage(Optional.of(weighted.divide(applied, 10, RoundingMode.HALF_UP)),
                contributing, 0);
    }
}
