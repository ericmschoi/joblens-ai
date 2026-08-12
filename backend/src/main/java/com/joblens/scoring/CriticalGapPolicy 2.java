package com.joblens.scoring;

import com.joblens.analysis.model.MatchStatus;
import com.joblens.analysis.model.RequirementAssessment;
import com.joblens.config.JoblensProperties;
import com.joblens.scoring.model.Eligibility;
import com.joblens.scoring.model.ScoringAdjustment;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Stops unrelated strengths from hiding a missing must-have.
 *
 * <p>A weighted average is exactly the kind of arithmetic that lets a candidate score 4.1 while
 * lacking something the employer called non-negotiable. The ceilings exist so the headline number
 * cannot say "strong match" when a core required qualification is simply absent.
 *
 * <p>Three rules constrain them. Only requirements marked both core and required can trigger one.
 * Unknowns never can — a silence is not a gap. And every ceiling that fires is reported with the
 * requirement that caused it, so a capped score is explainable rather than mysterious.
 */
@Component
public class CriticalGapPolicy {

    private final JoblensProperties.Scoring limits;

    public CriticalGapPolicy(JoblensProperties properties) {
        this.limits = properties.scoring();
    }

    /** @param cappedScore the total after any ceiling, ready for display */
    public record Outcome(BigDecimal cappedScore, List<ScoringAdjustment> adjustments, int coreGapCount) {

        public Outcome {
            adjustments = List.copyOf(adjustments);
        }
    }

    public Outcome apply(BigDecimal total, List<RequirementAssessment> assessments, Eligibility eligibility) {
        List<RequirementAssessment> coreGaps = assessments.stream()
                .filter(RequirementAssessment::isCoreRequirement)
                .filter(assessment -> assessment.status() == MatchStatus.GAP)
                .toList();

        List<ScoringAdjustment> adjustments = new ArrayList<>();
        BigDecimal score = total;

        if (eligibility == Eligibility.NOT_ELIGIBLE) {
            score = capTo(score, limits.notEligibleCeiling(), "NOT_ELIGIBLE",
                    "The posting states a requirement to hold the role at all that the documents show "
                            + "is not met, so the score is capped regardless of the rest of the fit.",
                    List.of(), adjustments);
        }

        if (!coreGaps.isEmpty()) {
            List<String> ids = coreGaps.stream().map(RequirementAssessment::id).toList();
            String names = coreGaps.stream()
                    .map(RequirementAssessment::requirementText)
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("");

            if (coreGaps.size() == 1) {
                score = capTo(score, limits.oneCoreGapCeiling(), "CORE_GAP_SINGLE",
                        ("A core required qualification has no supporting evidence in the resume (%s), "
                                + "so the total cannot be presented as a good match.").formatted(names),
                        ids, adjustments);
            } else if (coreGaps.size() == 2) {
                score = capTo(score, limits.twoCoreGapsCeiling(), "CORE_GAP_MULTIPLE",
                        "Two core required qualifications have no supporting evidence (%s)."
                                .formatted(names),
                        ids, adjustments);
            } else {
                score = capTo(score, limits.threeOrMoreCoreGapsCeiling(), "CORE_GAP_SEVERE",
                        "%d core required qualifications have no supporting evidence (%s)."
                                .formatted(coreGaps.size(), names),
                        ids, adjustments);
            }
        }

        return new Outcome(score, adjustments, coreGaps.size());
    }

    /** A ceiling only ever lowers. Applying one to an already lower score would be a bug, not a cap. */
    private static BigDecimal capTo(BigDecimal score, BigDecimal ceiling, String ruleId,
            String description, List<String> triggeringIds, List<ScoringAdjustment> adjustments) {

        if (score.compareTo(ceiling) <= 0) {
            return score;
        }
        adjustments.add(new ScoringAdjustment(ruleId, description, score, ceiling, triggeringIds));
        return ceiling;
    }
}
