package com.joblens.scoring;

import com.joblens.analysis.model.AnalysisDraft;
import com.joblens.analysis.model.CategoryName;
import com.joblens.analysis.model.Criticality;
import com.joblens.analysis.model.EvidenceMatch;
import com.joblens.analysis.model.EvidenceRelation;
import com.joblens.analysis.model.EvidenceStrength;
import com.joblens.analysis.model.Importance;
import com.joblens.analysis.model.MatchStatus;
import com.joblens.analysis.model.RequirementAssessment;
import com.joblens.analysis.model.RequirementKind;
import com.joblens.document.ExtractionWarning;
import com.joblens.resume.model.CandidateProfile;
import com.joblens.resume.model.SkillMention;
import com.joblens.scoring.model.ApplicationTier;
import com.joblens.scoring.model.CategoryResult;
import com.joblens.scoring.model.Eligibility;
import com.joblens.scoring.model.FitAnalysis;
import com.joblens.scoring.model.RequirementGap;
import com.joblens.scoring.model.ScoreConfidence;
import com.joblens.scoring.model.ScoreLabel;
import com.joblens.scoring.model.ScoringAdjustment;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Every number the product shows is produced here, and nowhere else.
 *
 * <p>The order matters and is the documented rounding rule: each category is rounded to one decimal
 * first, the total is the weighted sum of those displayed values, the total is rounded, and only
 * then are ceilings applied. A reader can therefore recompute the headline from the six numbers on
 * screen — which is the whole point of preferring explainability to a black box.
 */
@Component
public class FitScoreCalculator {

    private static final Logger LOG = LoggerFactory.getLogger(FitScoreCalculator.class);

    private static final int STRONGEST_MATCHES_SHOWN = 8;
    private static final Set<CategoryName> SUBFACTOR_CATEGORIES =
            Set.of(CategoryName.EXPERIENCE_EVIDENCE, CategoryName.SENIORITY_AND_OWNERSHIP);

    private final CategoryScorer categoryScorer;
    private final CriticalGapPolicy criticalGapPolicy;
    private final TierPolicy tierPolicy;
    private final RecommendationPolicy recommendationPolicy;
    private final ConfidencePolicy confidencePolicy;

    public FitScoreCalculator(CategoryScorer categoryScorer, CriticalGapPolicy criticalGapPolicy,
            TierPolicy tierPolicy, RecommendationPolicy recommendationPolicy,
            ConfidencePolicy confidencePolicy) {
        this.categoryScorer = categoryScorer;
        this.criticalGapPolicy = criticalGapPolicy;
        this.tierPolicy = tierPolicy;
        this.recommendationPolicy = recommendationPolicy;
        this.confidencePolicy = confidencePolicy;
    }

    public FitAnalysis calculate(AnalysisDraft draft, CandidateProfile profile,
            List<ExtractionWarning> warnings, int resumeCharacters, double groundingFailureRatio) {

        List<RequirementAssessment> assessments = capSkillsListOnlyEvidence(
                draft.requirementAssessments(), profile);

        boolean roleAsksForLeadership = mentionsLeadership(assessments);
        List<Scored> scored = new ArrayList<>();
        for (CategoryName category : CategoryName.values()) {
            scored.add(scoreCategory(category, assessments, draft, roleAsksForLeadership));
        }

        List<CategoryResult> categories = withRenormalisedWeights(scored);
        BigDecimal total = ScoringRubric.round(categories.stream()
                .map(result -> result.score().multiply(result.appliedWeight()))
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        Eligibility eligibility = decideEligibility(assessments);
        CriticalGapPolicy.Outcome capped = criticalGapPolicy.apply(total, assessments, eligibility);

        BigDecimal requiredCoverage = categoryScorer.requiredCoverage(assessments).ratio()
                .orElse(BigDecimal.ZERO);
        BigDecimal seniorityScore = categories.stream()
                .filter(result -> result.category() == CategoryName.SENIORITY_AND_OWNERSHIP)
                .findFirst().map(CategoryResult::score).orElse(BigDecimal.ZERO);

        ScoreConfidence confidence = confidencePolicy.decide(assessments, warnings, resumeCharacters,
                groundingFailureRatio);

        LOG.info("scored total={} cappedTotal={} coreGaps={} requiredCoverage={} confidence={}",
                total, capped.cappedScore(), capped.coreGapCount(),
                requiredCoverage.setScale(2, java.math.RoundingMode.HALF_UP), confidence);

        return new FitAnalysis(
                FitAnalysis.SCHEMA_VERSION,
                capped.cappedScore(),
                ScoreLabel.forScore(capped.cappedScore()).displayName(),
                confidence,
                tierPolicy.decide(capped.coreGapCount(), requiredCoverage, seniorityScore),
                recommendationPolicy.decide(capped.cappedScore(), capped.coreGapCount(), eligibility,
                        confidence),
                eligibility,
                capped.adjustments(),
                categories,
                assessments,
                evidenceOf(assessments, EvidenceRelation.DIRECT),
                evidenceOf(assessments, EvidenceRelation.TRANSFERABLE),
                gapsOf(assessments, MatchStatus.GAP, true),
                gapsOf(assessments, MatchStatus.GAP, false),
                gapsOf(assessments, MatchStatus.UNKNOWN, null),
                draft.roleAlignment(),
                draft.seniorityAlignment(),
                draft.realisticCompetitiveness(),
                draft.opportunityValue(),
                draft.resumePositioning(),
                draft.interviewPreparation(),
                draft.finalRationale(),
                limitationsFor(draft, capped.adjustments(), confidence));
    }

    // --- categories -------------------------------------------------------------------------------

    private record Scored(CategoryResult result, boolean rated) {}

    /**
     * A category the documents said nothing about is not a zero.
     *
     * <p>Scoring it zero would mean a posting that never mentions cloud experience costs the
     * candidate ten percent of their total. Unrated categories are given no weight and the rest are
     * renormalised, which is the category-level version of the rule that an unknown is not a gap.
     */
    private static List<CategoryResult> withRenormalisedWeights(List<Scored> scored) {
        BigDecimal ratedWeight = scored.stream()
                .filter(Scored::rated)
                .map(entry -> entry.result().nominalWeight())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CategoryResult> results = new ArrayList<>();
        for (Scored entry : scored) {
            CategoryResult result = entry.result();
            BigDecimal applied = entry.rated() && ratedWeight.signum() > 0
                    ? result.nominalWeight().divide(ratedWeight, 4, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            results.add(new CategoryResult(result.category(), result.displayName(), entry.rated(),
                    result.nominalWeight(), applied, result.score(), result.label(), result.summary(),
                    result.directEvidence(), result.transferableEvidence(), result.gaps(),
                    result.unknowns(), result.coverageRatio(), result.scoreImpactExplanation(),
                    result.improvementSuggestions()));
        }
        return results;
    }

    private Scored scoreCategory(CategoryName category, List<RequirementAssessment> assessments,
            AnalysisDraft draft, boolean roleAsksForLeadership) {

        CategoryScorer.Coverage coverage = coverageFor(category, assessments, draft, roleAsksForLeadership);
        BigDecimal score = coverage.ratio().map(ScoringRubric::scoreForCoverage).orElse(BigDecimal.ZERO)
                .setScale(1, java.math.RoundingMode.HALF_UP);

        List<RequirementAssessment> inCategory = assessments.stream()
                .filter(assessment -> assessment.primaryCategory() == category)
                .toList();

        return new Scored(new CategoryResult(
                category,
                category.displayName(),
                coverage.isScoreable(),
                category.weight(),
                category.weight(),
                score,
                ScoreLabel.forScore(score).displayName(),
                summaryFor(category, coverage),
                evidenceOf(inCategory, EvidenceRelation.DIRECT),
                evidenceOf(inCategory, EvidenceRelation.TRANSFERABLE),
                gapsOf(inCategory, MatchStatus.GAP, null),
                gapsOf(inCategory, MatchStatus.UNKNOWN, null),
                coverage.ratio().orElse(BigDecimal.ZERO).setScale(4, java.math.RoundingMode.HALF_UP),
                impactExplanationFor(category, coverage, score),
                improvementsFor(inCategory)), coverage.isScoreable());
    }

    private CategoryScorer.Coverage coverageFor(CategoryName category,
            List<RequirementAssessment> assessments, AnalysisDraft draft, boolean roleAsksForLeadership) {

        if (SUBFACTOR_CATEGORIES.contains(category)) {
            return categoryScorer.subfactorCoverage(category, draft.subfactorJudgements(),
                    roleAsksForLeadership);
        }
        if (category == CategoryName.REQUIRED_QUALIFICATION_COVERAGE) {
            return categoryScorer.requiredCoverage(assessments);
        }
        return categoryScorer.coverageFor(category, assessments);
    }

    private static String summaryFor(CategoryName category, CategoryScorer.Coverage coverage) {
        if (!coverage.isScoreable()) {
            return "There was not enough information to rate %s. %d requirement%s could not be "
                    .formatted(category.displayName(), coverage.unknownItems(),
                            coverage.unknownItems() == 1 ? "" : "s")
                    + "judged from the resume, so this category is shown at zero rather than guessed at.";
        }
        return "%s was rated from %d requirement%s the resume could be judged against."
                .formatted(category.displayName(), coverage.contributingItems(),
                        coverage.contributingItems() == 1 ? "" : "s");
    }

    /** The answer to "why 4.2 and not 4.3", in terms a reader can check against the rubric. */
    private static String impactExplanationFor(CategoryName category, CategoryScorer.Coverage coverage,
            BigDecimal score) {

        if (!coverage.isScoreable()) {
            return "No score could be derived: fewer than %d requirements in this category could be "
                    .formatted(CategoryScorer.MINIMUM_ITEMS_FOR_A_SCORE)
                    + "judged. Unknown requirements are excluded from the calculation rather than "
                    + "counted against the candidate, so nothing here has lowered the total unfairly.";
        }
        BigDecimal percent = coverage.ratio().orElse(BigDecimal.ZERO)
                .multiply(BigDecimal.valueOf(100)).setScale(0, java.math.RoundingMode.HALF_UP);

        return ("The resume answers %s%% of the weighted requirements in this category, which maps to "
                + "%s on the published scale. Required qualifications count nearly three times what "
                + "preferred ones do, and core requirements count half again as much. %d requirement%s "
                + "could not be judged and %s excluded from the ratio.")
                .formatted(percent, score, coverage.unknownItems(),
                        coverage.unknownItems() == 1 ? "" : "s",
                        coverage.unknownItems() == 1 ? "was" : "were");
    }

    private static List<String> improvementsFor(List<RequirementAssessment> assessments) {
        return assessments.stream()
                .filter(assessment -> assessment.status() == MatchStatus.UNKNOWN
                        || assessment.status() == MatchStatus.PARTIAL_MATCH)
                .limit(3)
                .map(assessment -> "If you have done this, say so explicitly: \"%s\". Describe it inside "
                        .formatted(assessment.requirementText())
                        + "a role or project rather than only in a skills list.")
                .toList();
    }

    // --- evidence and gaps -------------------------------------------------------------------------

    private static List<EvidenceMatch> evidenceOf(List<RequirementAssessment> assessments,
            EvidenceRelation relation) {
        return assessments.stream()
                .filter(assessment -> assessment.relation() == relation)
                .flatMap(assessment -> assessment.evidence().stream())
                .filter(EvidenceMatch::grounded)
                .limit(STRONGEST_MATCHES_SHOWN)
                .toList();
    }

    /** @param core when null, both criticalities are included */
    private static List<RequirementGap> gapsOf(List<RequirementAssessment> assessments,
            MatchStatus status, Boolean core) {

        return assessments.stream()
                .filter(assessment -> assessment.status() == status)
                .filter(assessment -> core == null
                        || (assessment.criticality() == Criticality.CORE) == core)
                .map(assessment -> new RequirementGap(
                        assessment.id(),
                        assessment.requirementText(),
                        assessment.importance(),
                        assessment.criticality(),
                        status == MatchStatus.UNKNOWN
                                ? "The resume does not say either way, so this has not counted against you."
                                : "The posting asks for this and the resume shows no supporting evidence.",
                        status == MatchStatus.UNKNOWN
                                ? "Add a line to the relevant role if you have this experience."
                                : "Be ready to explain how you would cover this, or look for adjacent "
                                        + "experience already in your resume that speaks to it."))
                .toList();
    }

    // --- rules the model does not get to decide ------------------------------------------------------

    /**
     * A technology named only in a skills list is an assertion; the same technology described inside
     * a role or a project is a demonstration. The model can call that strong evidence, and the
     * server disagrees.
     */
    private static List<RequirementAssessment> capSkillsListOnlyEvidence(
            List<RequirementAssessment> assessments, CandidateProfile profile) {

        Set<String> demonstrated = profile.skills().stream()
                .filter(skill -> skill.origin() != SkillMention.Origin.SKILLS_LIST)
                .map(skill -> skill.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        Set<String> listedOnly = profile.skills().stream()
                .filter(skill -> skill.origin() == SkillMention.Origin.SKILLS_LIST)
                .map(skill -> skill.name().toLowerCase(Locale.ROOT))
                .filter(name -> !demonstrated.contains(name))
                .collect(Collectors.toSet());

        if (listedOnly.isEmpty()) {
            return assessments;
        }

        return assessments.stream()
                .map(assessment -> shouldCap(assessment, listedOnly)
                        ? withCappedStrength(assessment)
                        : assessment)
                .toList();
    }

    private static boolean shouldCap(RequirementAssessment assessment, Set<String> listedOnly) {
        if (assessment.kind() != RequirementKind.TECHNICAL
                || assessment.evidenceStrength() == EvidenceStrength.WEAK
                || assessment.evidenceStrength() == EvidenceStrength.NONE) {
            return false;
        }
        String requirement = assessment.requirementText().toLowerCase(Locale.ROOT);
        return listedOnly.stream().anyMatch(requirement::contains);
    }

    private static RequirementAssessment withCappedStrength(RequirementAssessment assessment) {
        return new RequirementAssessment(assessment.id(), assessment.requirementText(),
                assessment.kind(), assessment.importance(), assessment.criticality(),
                assessment.alternativeGroupId(), assessment.primaryCategory(), assessment.status(),
                assessment.relation(), EvidenceStrength.WEAK, assessment.evidence(),
                assessment.rationale() + " Evidence strength was reduced because this technology appears "
                        + "only in the skills list, not inside a role or project.");
    }

    /**
     * Only a stated hard condition that the documents settle. A resume that simply does not mention
     * work authorisation leaves this unknown, which is the usual case and costs nothing.
     */
    private static Eligibility decideEligibility(List<RequirementAssessment> assessments) {
        boolean blocked = assessments.stream()
                .filter(assessment -> assessment.kind() == RequirementKind.LEGAL_ELIGIBILITY)
                .filter(assessment -> assessment.importance() == Importance.REQUIRED)
                .anyMatch(assessment -> assessment.status() == MatchStatus.GAP);
        return blocked ? Eligibility.NOT_ELIGIBLE : Eligibility.UNKNOWN;
    }

    private static boolean mentionsLeadership(List<RequirementAssessment> assessments) {
        return assessments.stream()
                .map(assessment -> assessment.requirementText().toLowerCase(Locale.ROOT))
                .anyMatch(text -> text.contains("lead") || text.contains("mentor")
                        || text.contains("manage") || text.contains("staff") || text.contains("principal"));
    }

    private static List<String> limitationsFor(AnalysisDraft draft, List<ScoringAdjustment> adjustments,
            ScoreConfidence confidence) {

        List<String> limitations = new ArrayList<>(draft.limitations());
        if (!adjustments.isEmpty()) {
            limitations.add("A score ceiling was applied. See the scoring adjustments for which "
                    + "requirement caused it.");
        }
        if (confidence == ScoreConfidence.LOW) {
            limitations.add("Confidence in this analysis is low. Check the extraction warnings and "
                    + "confirm the documents again before relying on the result.");
        }
        limitations.add(ScoreLabel.CAVEAT);
        return limitations;
    }
}
