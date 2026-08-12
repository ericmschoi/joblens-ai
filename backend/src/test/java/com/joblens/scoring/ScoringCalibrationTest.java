package com.joblens.scoring;

import static com.joblens.testsupport.AnalysisDrafts.draft;
import static com.joblens.testsupport.AnalysisDrafts.requirement;
import static com.joblens.testsupport.AnalysisDrafts.subfactors;
import static org.assertj.core.api.Assertions.assertThat;

import com.joblens.analysis.model.CategoryName;
import com.joblens.analysis.model.EvidenceRelation;
import com.joblens.analysis.model.EvidenceStrength;
import com.joblens.analysis.model.MatchStatus;
import com.joblens.analysis.model.RequirementAssessment;
import com.joblens.analysis.model.RequirementKind;
import com.joblens.resume.model.CandidateProfile;
import com.joblens.scoring.model.ApplicationTier;
import com.joblens.scoring.model.FitAnalysis;
import com.joblens.testsupport.TestProperties;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Calibration fixtures: whole candidates, not individual rules.
 *
 * <p>{@link FitScoreCalculatorTest} pins each rule where it lives. This suite pins what a reader
 * actually experiences — that a well-matched candidate lands in the top band, that a candidate
 * missing a core must-have cannot get there however good the rest is, and that the ordering between
 * recognisable kinds of candidate never inverts.
 *
 * <p>The exact totals below are regression anchors taken from the published rubric. A change to one
 * of them is a product decision about what a rating means, and should be made deliberately here
 * before it is made in {@link ScoringRubric}.
 */
class ScoringCalibrationTest {

    private final FitScoreCalculator calculator = new FitScoreCalculator(
            new CategoryScorer(),
            new CriticalGapPolicy(TestProperties.defaults()),
            new TierPolicy(),
            new RecommendationPolicy(),
            new ConfidencePolicy());

    /** The six categories a real posting touches, so nothing is unrated by accident. */
    private static final List<CategoryName> COVERAGE_CATEGORIES = List.of(
            CategoryName.CORE_TECHNICAL_STACK,
            CategoryName.ROLE_AND_RESPONSIBILITY_ALIGNMENT,
            CategoryName.REQUIRED_QUALIFICATION_COVERAGE,
            CategoryName.DOMAIN_AND_OPERATING_ENVIRONMENT);

    /**
     * A posting-shaped set of requirements: two per coverage-driven category, all required, half of
     * them core, judged by the status the scenario is about.
     */
    private static List<RequirementAssessment> candidate(MatchStatus status, EvidenceRelation relation,
            EvidenceStrength strength) {

        List<RequirementAssessment> assessments = new ArrayList<>();
        int index = 0;
        for (CategoryName category : COVERAGE_CATEGORIES) {
            for (int i = 0; i < 2; i++) {
                index++;
                var builder = requirement("r" + index, "Requirement " + index)
                        .category(category)
                        .status(status)
                        .relation(relation)
                        .strength(strength);
                if (i == 0) {
                    builder = builder.core();
                }
                assessments.add(builder.build());
            }
        }
        return assessments;
    }

    private FitAnalysis score(List<RequirementAssessment> assessments, int subfactorValue) {
        return calculator.calculate(draft(assessments, subfactors(subfactorValue, subfactorValue)),
                CandidateProfile.empty(), List.of(), 3000, 0.0);
    }

    /** Replaces the first {@code count} core requirements with genuine gaps. */
    private static List<RequirementAssessment> withCoreGaps(List<RequirementAssessment> base, int count) {
        List<RequirementAssessment> result = new ArrayList<>(base);
        int replaced = 0;
        for (int i = 0; i < result.size() && replaced < count; i++) {
            RequirementAssessment assessment = result.get(i);
            if (!assessment.isCoreRequirement()) {
                continue;
            }
            result.set(i, requirement(assessment.id(), assessment.requirementText())
                    .category(assessment.primaryCategory())
                    .core()
                    .status(MatchStatus.GAP)
                    .strength(EvidenceStrength.NONE)
                    .build());
            replaced++;
        }
        return result;
    }

    // --- the anchors ------------------------------------------------------------------------------

    @Test
    void aCandidateWhoDirectlyMeetsEverythingScoresAtTheTopOfTheScale() {
        FitAnalysis analysis = score(
                candidate(MatchStatus.STRONG_MATCH, EvidenceRelation.DIRECT, EvidenceStrength.STRONG), 4);

        assertThat(analysis.totalMatchScore()).isEqualByComparingTo("5.0");
        assertThat(analysis.totalMatchLabel()).isEqualTo("Excellent Match");
        assertThat(analysis.applicationTier()).isEqualTo(ApplicationTier.SAFETY);
        assertThat(analysis.scoringAdjustments()).isEmpty();
    }

    @Test
    void aCandidateWhoseEvidenceIsOnlyTransferableRatesWellBelowADirectMatch() {
        FitAnalysis analysis = score(
                candidate(MatchStatus.PARTIAL_MATCH, EvidenceRelation.TRANSFERABLE,
                        EvidenceStrength.MODERATE), 3);

        assertThat(analysis.totalMatchScore()).isEqualByComparingTo("2.5");
        assertThat(analysis.totalMatchLabel()).isEqualTo("Weak Match");
        assertThat(analysis.criticalGaps())
                .as("transferable evidence is not a gap")
                .isEmpty();
    }

    @Test
    void aCandidateWithPartialDirectEvidenceEverywhereIsAReach() {
        FitAnalysis analysis = score(
                candidate(MatchStatus.PARTIAL_MATCH, EvidenceRelation.DIRECT, EvidenceStrength.MODERATE), 3);

        assertThat(analysis.totalMatchScore()).isEqualByComparingTo("2.9");
        assertThat(analysis.applicationTier()).isEqualTo(ApplicationTier.REACH);
    }

    @Test
    void oneCoreGapCannotBeAveragedAwayByEverythingElseBeingPerfect() {
        List<RequirementAssessment> assessments = withCoreGaps(
                candidate(MatchStatus.STRONG_MATCH, EvidenceRelation.DIRECT, EvidenceStrength.STRONG), 1);

        FitAnalysis analysis = score(assessments, 4);

        assertThat(analysis.totalMatchScore()).isEqualByComparingTo("3.4");
        assertThat(analysis.scoringAdjustments()).singleElement()
                .satisfies(adjustment -> {
                    assertThat(adjustment.ruleId()).isEqualTo("CORE_GAP_SINGLE");
                    assertThat(adjustment.scoreBeforeAdjustment()).isGreaterThan(new BigDecimal("3.4"));
                    assertThat(adjustment.triggeringRequirementIds()).isNotEmpty();
                });
    }

    @Test
    void twoCoreGapsCapLowerThanOne() {
        BigDecimal one = score(withCoreGaps(
                candidate(MatchStatus.STRONG_MATCH, EvidenceRelation.DIRECT, EvidenceStrength.STRONG), 1), 4)
                .totalMatchScore();
        BigDecimal two = score(withCoreGaps(
                candidate(MatchStatus.STRONG_MATCH, EvidenceRelation.DIRECT, EvidenceStrength.STRONG), 2), 4)
                .totalMatchScore();

        assertThat(one).isEqualByComparingTo("3.4");
        assertThat(two).isEqualByComparingTo("2.4");
    }

    @Test
    void aResumeThatSaysNothingEitherWayIsNotScoredAsAFailure() {
        FitAnalysis analysis = score(
                candidate(MatchStatus.UNKNOWN, EvidenceRelation.DIRECT, EvidenceStrength.NONE), 3);

        assertThat(analysis.criticalGaps()).isEmpty();
        assertThat(analysis.unknownRequirements()).hasSize(8);
        assertThat(analysis.scoringAdjustments())
                .as("an unknown must never trigger a ceiling")
                .isEmpty();
        assertThat(analysis.categoryResults())
                .filteredOn(result -> COVERAGE_CATEGORIES.contains(result.category()))
                .allSatisfy(result -> {
                    assertThat(result.rated()).isFalse();
                    assertThat(result.appliedWeight()).isEqualByComparingTo("0");
                });
    }

    @Test
    void anIneligibleCandidateIsCappedWhateverTheirExperienceLooksLike() {
        List<RequirementAssessment> assessments = new ArrayList<>(
                candidate(MatchStatus.STRONG_MATCH, EvidenceRelation.DIRECT, EvidenceStrength.STRONG));
        assessments.add(requirement("legal-1", "Authorised to work in Canada")
                .kind(RequirementKind.LEGAL_ELIGIBILITY)
                .status(MatchStatus.GAP)
                .strength(EvidenceStrength.NONE)
                .build());

        FitAnalysis analysis = score(assessments, 4);

        assertThat(analysis.totalMatchScore()).isLessThanOrEqualTo(new BigDecimal("1.5"));
        assertThat(analysis.scoringAdjustments())
                .extracting(adjustment -> adjustment.ruleId())
                .contains("NOT_ELIGIBLE");
    }

    @Test
    void theOrderingBetweenRecognisableCandidatesNeverInverts() {
        Map<String, BigDecimal> totals = Map.of(
                "direct", score(candidate(MatchStatus.STRONG_MATCH, EvidenceRelation.DIRECT,
                        EvidenceStrength.STRONG), 4).totalMatchScore(),
                "partialDirect", score(candidate(MatchStatus.PARTIAL_MATCH, EvidenceRelation.DIRECT,
                        EvidenceStrength.MODERATE), 3).totalMatchScore(),
                "transferable", score(candidate(MatchStatus.PARTIAL_MATCH, EvidenceRelation.TRANSFERABLE,
                        EvidenceStrength.MODERATE), 3).totalMatchScore(),
                "gaps", score(candidate(MatchStatus.GAP, EvidenceRelation.DIRECT,
                        EvidenceStrength.NONE), 2).totalMatchScore());

        assertThat(totals.get("direct")).isGreaterThan(totals.get("partialDirect"));
        assertThat(totals.get("partialDirect")).isGreaterThan(totals.get("transferable"));
        assertThat(totals.get("transferable")).isGreaterThan(totals.get("gaps"));
    }

    @Test
    void everyHeadlineCanBeRecomputedFromTheCategoriesTheReaderCanSee() {
        for (MatchStatus status : List.of(MatchStatus.STRONG_MATCH, MatchStatus.PARTIAL_MATCH)) {
            FitAnalysis analysis = score(
                    candidate(status, EvidenceRelation.DIRECT, EvidenceStrength.STRONG), 4);

            BigDecimal recomputed = analysis.categoryResults().stream()
                    .map(result -> result.score().multiply(result.appliedWeight()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(1, java.math.RoundingMode.HALF_UP);

            assertThat(analysis.totalMatchScore())
                    .as("the number on screen must be reproducible by hand for %s", status)
                    .isEqualByComparingTo(recomputed);
        }
    }
}
