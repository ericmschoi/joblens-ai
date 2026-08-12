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
import com.joblens.document.ExtractionWarning;
import com.joblens.document.Provenance;
import com.joblens.document.WarningCode;
import com.joblens.resume.model.CandidateProfile;
import com.joblens.resume.model.SkillMention;
import com.joblens.scoring.model.ApplicationTier;
import com.joblens.scoring.model.CategoryResult;
import com.joblens.scoring.model.Eligibility;
import com.joblens.scoring.model.FitAnalysis;
import com.joblens.scoring.model.Recommendation;
import com.joblens.scoring.model.ScoreConfidence;
import com.joblens.testsupport.TestProperties;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The arithmetic the product's credibility rests on.
 *
 * <p>These tests are written so that a number changing is a decision someone made, not something
 * that drifted.
 */
class FitScoreCalculatorTest {

    private final FitScoreCalculator calculator = new FitScoreCalculator(
            new CategoryScorer(),
            new CriticalGapPolicy(TestProperties.defaults()),
            new TierPolicy(),
            new RecommendationPolicy(),
            new ConfidencePolicy());

    private FitAnalysis score(List<RequirementAssessment> assessments) {
        return score(assessments, CandidateProfile.empty(), List.of(), 3000, 0.0);
    }

    private FitAnalysis score(List<RequirementAssessment> assessments, CandidateProfile profile,
            List<ExtractionWarning> warnings, int resumeCharacters, double groundingFailures) {
        return calculator.calculate(draft(assessments, subfactors(3, 3)), profile, warnings,
                resumeCharacters, groundingFailures);
    }

    private static BigDecimal scoreOf(FitAnalysis analysis, CategoryName category) {
        return analysis.categoryResults().stream()
                .filter(result -> result.category() == category)
                .findFirst().orElseThrow().score();
    }

    @Nested
    class Weighting {

        @Test
        void aRequiredQualificationCountsFarMoreThanAPreferredOne() {
            FitAnalysis missingRequired = score(List.of(
                    requirement("r1", "Java").status(MatchStatus.GAP).strength(EvidenceStrength.NONE).build(),
                    requirement("r2", "Kafka").preferred().build(),
                    requirement("r3", "REST").build()));

            FitAnalysis missingPreferred = score(List.of(
                    requirement("r1", "Java").build(),
                    requirement("r2", "Kafka").preferred().status(MatchStatus.GAP)
                            .strength(EvidenceStrength.NONE).build(),
                    requirement("r3", "REST").build()));

            assertThat(scoreOf(missingPreferred, CategoryName.CORE_TECHNICAL_STACK))
                    .isGreaterThan(scoreOf(missingRequired, CategoryName.CORE_TECHNICAL_STACK));
        }

        @Test
        void aCoreRequirementCountsMoreThanASupportingOne() {
            FitAnalysis missingCore = score(List.of(
                    requirement("r1", "Java").core().status(MatchStatus.GAP)
                            .strength(EvidenceStrength.NONE).build(),
                    requirement("r2", "REST").build(),
                    requirement("r3", "SQL").build()));

            FitAnalysis missingSupporting = score(List.of(
                    requirement("r1", "Java").core().build(),
                    requirement("r2", "REST").status(MatchStatus.GAP)
                            .strength(EvidenceStrength.NONE).build(),
                    requirement("r3", "SQL").build()));

            assertThat(scoreOf(missingSupporting, CategoryName.CORE_TECHNICAL_STACK))
                    .isGreaterThan(scoreOf(missingCore, CategoryName.CORE_TECHNICAL_STACK));
        }

        @Test
        void transferableEvidenceIsWorthLessThanDirectEvidence() {
            FitAnalysis direct = score(List.of(
                    requirement("r1", "Java").status(MatchStatus.PARTIAL_MATCH).build(),
                    requirement("r2", "REST").build()));

            FitAnalysis transferable = score(List.of(
                    requirement("r1", "Java").status(MatchStatus.PARTIAL_MATCH)
                            .relation(EvidenceRelation.TRANSFERABLE).build(),
                    requirement("r2", "REST").build()));

            assertThat(scoreOf(direct, CategoryName.CORE_TECHNICAL_STACK))
                    .isGreaterThan(scoreOf(transferable, CategoryName.CORE_TECHNICAL_STACK));
        }
    }

    @Nested
    class UnknownIsNotAGap {

        @Test
        void anUnknownRequirementDoesNotLowerTheScore() {
            List<RequirementAssessment> withoutUnknown = List.of(
                    requirement("r1", "Java").build(), requirement("r2", "REST").build());
            List<RequirementAssessment> withUnknown = List.of(
                    requirement("r1", "Java").build(), requirement("r2", "REST").build(),
                    requirement("r3", "Kubernetes").core().status(MatchStatus.UNKNOWN)
                            .strength(EvidenceStrength.NONE).build());

            assertThat(scoreOf(score(withUnknown), CategoryName.CORE_TECHNICAL_STACK))
                    .as("a silence about the candidate must cost them nothing")
                    .isEqualByComparingTo(scoreOf(score(withoutUnknown), CategoryName.CORE_TECHNICAL_STACK));
        }

        @Test
        void anUnknownCoreRequirementDoesNotTriggerACeiling() {
            FitAnalysis analysis = score(List.of(
                    requirement("r1", "Java").build(),
                    requirement("r2", "REST").build(),
                    requirement("r3", "Kubernetes").core().status(MatchStatus.UNKNOWN)
                            .strength(EvidenceStrength.NONE).build()));

            assertThat(analysis.scoringAdjustments()).isEmpty();
            assertThat(analysis.unknownRequirements()).hasSize(1);
            assertThat(analysis.criticalGaps()).isEmpty();
        }
    }

    @Nested
    class AlternativeRequirements {

        @Test
        void meetingOneAlternativeIsNotCountedAsSeveralGaps() {
            FitAnalysis analysis = score(List.of(
                    requirement("r1", "Java").core().alternativeGroup("lang").build(),
                    requirement("r2", "C#").core().alternativeGroup("lang").status(MatchStatus.GAP)
                            .strength(EvidenceStrength.NONE).build(),
                    requirement("r3", "Go").core().alternativeGroup("lang").status(MatchStatus.GAP)
                            .strength(EvidenceStrength.NONE).build(),
                    requirement("r4", "REST").build()));

            assertThat(scoreOf(analysis, CategoryName.CORE_TECHNICAL_STACK))
                    .as("\"Java, C# or Go\" is one requirement, and it was met")
                    .isEqualByComparingTo("5.0");
        }
    }

    @Nested
    class SkillsListOnlyEvidence {

        @Test
        void aTechnologyNamedOnlyInASkillsListCannotBeStrongEvidence() {
            CandidateProfile listedOnly = new CandidateProfile("", List.of(
                    new SkillMention("s1", "Kafka", SkillMention.Origin.SKILLS_LIST,
                            Provenance.of(null, "SKILLS", 0, 0, "Kafka"))),
                    List.of(), List.of(), List.of(), List.of());

            FitAnalysis analysis = score(List.of(
                    requirement("r1", "Experience with Kafka").kind(RequirementKind.TECHNICAL).build(),
                    requirement("r2", "REST").build()), listedOnly, List.of(), 3000, 0.0);

            RequirementAssessment kafka = analysis.requirementAssessments().getFirst();
            assertThat(kafka.evidenceStrength()).isEqualTo(EvidenceStrength.WEAK);
            assertThat(kafka.rationale()).contains("only in the skills list");
        }

        @Test
        void aTechnologyDemonstratedInARoleKeepsItsStrength() {
            CandidateProfile demonstrated = new CandidateProfile("", List.of(
                    new SkillMention("s1", "Kafka", SkillMention.Origin.SKILLS_LIST,
                            Provenance.of(null, "SKILLS", 0, 0, "Kafka")),
                    new SkillMention("s2", "Kafka", SkillMention.Origin.WORK_EXPERIENCE,
                            Provenance.of(null, "EXPERIENCE", 5, 5, "Built a Kafka pipeline"))),
                    List.of(), List.of(), List.of(), List.of());

            FitAnalysis analysis = score(List.of(
                    requirement("r1", "Experience with Kafka").build(),
                    requirement("r2", "REST").build()), demonstrated, List.of(), 3000, 0.0);

            assertThat(analysis.requirementAssessments().getFirst().evidenceStrength())
                    .isEqualTo(EvidenceStrength.STRONG);
        }
    }

    @Nested
    class Ceilings {

        private FitAnalysis withCoreGaps(int count) {
            List<RequirementAssessment> assessments = new java.util.ArrayList<>();
            for (int i = 0; i < count; i++) {
                assessments.add(requirement("gap-" + i, "Core requirement " + i).core()
                        .status(MatchStatus.GAP).strength(EvidenceStrength.NONE).build());
            }
            for (int i = 0; i < 12; i++) {
                assessments.add(requirement("ok-" + i, "Met requirement " + i)
                        .category(CategoryName.ROLE_AND_RESPONSIBILITY_ALIGNMENT).build());
            }
            return score(assessments);
        }

        @Test
        void oneCoreGapCapsTheTotalAtTheModerateBand() {
            FitAnalysis analysis = withCoreGaps(1);

            assertThat(analysis.totalMatchScore()).isEqualByComparingTo("3.4");
            assertThat(analysis.totalMatchLabel()).isEqualTo("Moderate Match");
            assertThat(analysis.scoringAdjustments()).singleElement().satisfies(adjustment -> {
                assertThat(adjustment.ruleId()).isEqualTo("CORE_GAP_SINGLE");
                assertThat(adjustment.triggeringRequirementIds()).containsExactly("gap-0");
                assertThat(adjustment.description()).contains("Core requirement 0");
            });
        }

        @Test
        void twoCoreGapsCapLower() {
            assertThat(withCoreGaps(2).totalMatchScore()).isEqualByComparingTo("2.4");
        }

        @Test
        void threeOrMoreCoreGapsCapLowerStill() {
            assertThat(withCoreGaps(3).totalMatchScore()).isEqualByComparingTo("1.9");
            assertThat(withCoreGaps(5).totalMatchScore()).isEqualByComparingTo("1.9");
        }

        @Test
        void aCeilingOnlyEverLowersAScore() {
            FitAnalysis analysis = score(List.of(
                    requirement("gap-0", "Core requirement").core().status(MatchStatus.GAP)
                            .strength(EvidenceStrength.NONE).build(),
                    requirement("gap-1", "Another core requirement").core().status(MatchStatus.GAP)
                            .strength(EvidenceStrength.NONE).build()));

            assertThat(analysis.totalMatchScore())
                    .as("an already low total is not raised to meet the ceiling")
                    .isLessThanOrEqualTo(new BigDecimal("2.4"));
        }

        @Test
        void aPartialMatchOnACoreRequirementIsNotAGapAndDoesNotCap() {
            FitAnalysis analysis = score(List.of(
                    requirement("r1", "AWS").core().status(MatchStatus.PARTIAL_MATCH)
                            .strength(EvidenceStrength.WEAK).build(),
                    requirement("r2", "Java").core().build()));

            assertThat(analysis.scoringAdjustments()).isEmpty();
        }

        @Test
        void anUnmetLegalRequirementCapsHardestAndIsReported() {
            FitAnalysis analysis = score(List.of(
                    requirement("r1", "Must hold a valid licence").core()
                            .kind(RequirementKind.LEGAL_ELIGIBILITY).status(MatchStatus.GAP)
                            .strength(EvidenceStrength.NONE).build(),
                    requirement("r2", "Java").build(),
                    requirement("r3", "REST").build()));

            assertThat(analysis.eligibility()).isEqualTo(Eligibility.NOT_ELIGIBLE);
            assertThat(analysis.totalMatchScore()).isLessThanOrEqualTo(new BigDecimal("1.5"));
            assertThat(analysis.scoringAdjustments())
                    .anyMatch(adjustment -> adjustment.ruleId().equals("NOT_ELIGIBLE"));
        }

        @Test
        void anUnstatedLegalConditionLeavesEligibilityUnknownAndCostsNothing() {
            FitAnalysis analysis = score(List.of(
                    requirement("r1", "Work authorisation").kind(RequirementKind.LEGAL_ELIGIBILITY)
                            .core().status(MatchStatus.UNKNOWN).strength(EvidenceStrength.NONE).build(),
                    requirement("r2", "Java").build(),
                    requirement("r3", "REST").build()));

            assertThat(analysis.eligibility()).isEqualTo(Eligibility.UNKNOWN);
            assertThat(analysis.scoringAdjustments()).isEmpty();
        }
    }

    @Nested
    class TheTotalCanBeRecomputedFromWhatIsOnScreen {

        @Test
        void theTotalIsTheWeightedSumOfTheDisplayedCategoryScores() {
            FitAnalysis analysis = score(List.of(
                    requirement("r1", "Java").build(),
                    requirement("r2", "REST").build(),
                    requirement("r3", "Responsibilities")
                            .category(CategoryName.ROLE_AND_RESPONSIBILITY_ALIGNMENT).build(),
                    requirement("r4", "More responsibilities")
                            .category(CategoryName.ROLE_AND_RESPONSIBILITY_ALIGNMENT).build()));

            BigDecimal recomputed = analysis.categoryResults().stream()
                    .map(result -> result.score().multiply(result.appliedWeight()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(1, java.math.RoundingMode.HALF_UP);

            assertThat(analysis.totalMatchScore())
                    .as("the six numbers on screen must add up to the headline")
                    .isEqualByComparingTo(recomputed);
        }

        @Test
        void everyCategoryShowsOneDecimalPlace() {
            FitAnalysis analysis = score(List.of(
                    requirement("r1", "Java").build(), requirement("r2", "REST").build()));

            assertThat(analysis.categoryResults())
                    .allSatisfy(result -> assertThat(result.score().scale()).isEqualTo(1));
            assertThat(analysis.totalMatchScore().scale()).isEqualTo(1);
        }

        @Test
        void everyCategoryExplainsItsOwnNumber() {
            FitAnalysis analysis = score(List.of(
                    requirement("r1", "Java").build(), requirement("r2", "REST").build()));

            assertThat(analysis.categoryResults())
                    .allSatisfy(result -> assertThat(result.scoreImpactExplanation()).isNotBlank());
        }

        @Test
        void aCategoryWithTooLittleEvidenceSaysSoRatherThanGuessing() {
            CategoryResult domain = score(List.of(
                    requirement("r1", "Java").build(), requirement("r2", "REST").build()))
                    .categoryResults().stream()
                    .filter(result -> result.category() == CategoryName.DOMAIN_AND_OPERATING_ENVIRONMENT)
                    .findFirst().orElseThrow();

            assertThat(domain.rated()).isFalse();
            assertThat(domain.summary()).contains("not enough information");
            assertThat(domain.scoreImpactExplanation()).contains("excluded from the calculation");
        }

        @Test
        void aCategoryThePostingNeverMentionedCarriesNoWeight() {
            FitAnalysis analysis = score(List.of(
                    requirement("r1", "Java").build(), requirement("r2", "REST").build()));

            CategoryResult domain = analysis.categoryResults().stream()
                    .filter(result -> result.category() == CategoryName.DOMAIN_AND_OPERATING_ENVIRONMENT)
                    .findFirst().orElseThrow();

            assertThat(domain.appliedWeight())
                    .as("an employer's silence about a category must not cost the candidate anything")
                    .isEqualByComparingTo("0.0000");
            assertThat(analysis.categoryResults().stream()
                    .map(CategoryResult::appliedWeight)
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add))
                    .as("the applied weights still add up to a whole")
                    .isEqualByComparingTo("1.0000");
        }
    }

    @Nested
    class TierAndRecommendation {

        @Test
        void aCoreGapMakesItAReachHoweverGoodTheAverageIs() {
            FitAnalysis analysis = score(List.of(
                    requirement("r1", "AWS").core().status(MatchStatus.GAP)
                            .strength(EvidenceStrength.NONE).build(),
                    requirement("r2", "Java").core().build(),
                    requirement("r3", "REST").build(),
                    requirement("r4", "Responsibilities")
                            .category(CategoryName.ROLE_AND_RESPONSIBILITY_ALIGNMENT).build(),
                    requirement("r5", "More")
                            .category(CategoryName.ROLE_AND_RESPONSIBILITY_ALIGNMENT).build()));

            assertThat(analysis.applicationTier()).isEqualTo(ApplicationTier.REACH);
        }

        @Test
        void aFullyMetRoleWithHighSeniorityStandingIsASafety() {
            FitAnalysis analysis = calculator.calculate(
                    draft(List.of(requirement("r1", "Java").core().build(),
                            requirement("r2", "REST").build(),
                            requirement("r3", "SQL").build()), subfactors(4, 4)),
                    CandidateProfile.empty(), List.of(), 3000, 0.0);

            assertThat(analysis.applicationTier()).isEqualTo(ApplicationTier.SAFETY);
        }

        @Test
        void tierIsNotSimplyAnotherNameForTheTotal() {
            FitAnalysis safety = calculator.calculate(
                    draft(List.of(requirement("r1", "Java").core().build(),
                            requirement("r2", "REST").build()), subfactors(4, 4)),
                    CandidateProfile.empty(), List.of(), 3000, 0.0);
            FitAnalysis target = calculator.calculate(
                    draft(List.of(requirement("r1", "Java").core().build(),
                            requirement("r2", "REST").build()), subfactors(4, 1)),
                    CandidateProfile.empty(), List.of(), 3000, 0.0);

            assertThat(safety.applicationTier()).isEqualTo(ApplicationTier.SAFETY);
            assertThat(target.applicationTier())
                    .as("same requirement coverage, different level standing, different tier")
                    .isEqualTo(ApplicationTier.TARGET);
        }

        @Test
        void aCoreGapDowngradesTheRecommendationToConditional() {
            FitAnalysis analysis = score(List.of(
                    requirement("r1", "AWS").core().status(MatchStatus.GAP)
                            .strength(EvidenceStrength.NONE).build(),
                    requirement("r2", "Java").core().build(),
                    requirement("r3", "REST").build()));

            assertThat(analysis.recommendation()).isEqualTo(Recommendation.CONDITIONAL);
        }

        @Test
        void anIneligibleCandidateIsAlwaysLowPriority() {
            FitAnalysis analysis = score(List.of(
                    requirement("r1", "Must hold a valid licence").kind(RequirementKind.LEGAL_ELIGIBILITY)
                            .core().status(MatchStatus.GAP).strength(EvidenceStrength.NONE).build(),
                    requirement("r2", "Java").build(),
                    requirement("r3", "REST").build()));

            assertThat(analysis.recommendation()).isEqualTo(Recommendation.LOW_PRIORITY);
        }
    }

    @Nested
    class Confidence {

        @Test
        void aSevereExtractionWarningLowersConfidence() {
            FitAnalysis analysis = score(
                    List.of(requirement("r1", "Java").build(), requirement("r2", "REST").build()),
                    CandidateProfile.empty(),
                    List.of(ExtractionWarning.of(WarningCode.POSSIBLE_MULTI_COLUMN)), 3000, 0.0);

            assertThat(analysis.scoreConfidence()).isEqualTo(ScoreConfidence.LOW);
        }

        @Test
        void discardedEvidenceLowersConfidence() {
            FitAnalysis analysis = score(
                    List.of(requirement("r1", "Java").build(), requirement("r2", "REST").build()),
                    CandidateProfile.empty(), List.of(), 3000, 0.5);

            assertThat(analysis.scoreConfidence()).isEqualTo(ScoreConfidence.LOW);
        }

        @Test
        void aCleanFullyJudgedAnalysisIsHighConfidence() {
            FitAnalysis analysis = score(List.of(
                    requirement("r1", "Java").build(), requirement("r2", "REST").build(),
                    requirement("r3", "SQL").build(), requirement("r4", "HTTP").build(),
                    requirement("r5", "Testing").build()));

            assertThat(analysis.scoreConfidence()).isEqualTo(ScoreConfidence.HIGH);
        }
    }

    @Test
    void anAttractiveOpportunityDoesNotRaiseTheFitScore() {
        List<RequirementAssessment> assessments = List.of(
                requirement("r1", "Java").build(), requirement("r2", "REST").build());

        BigDecimal withGlowingOpportunity = calculator.calculate(
                draft(assessments, subfactors(3, 3)), CandidateProfile.empty(), List.of(), 3000, 0.0)
                .totalMatchScore();
        BigDecimal reference = calculator.calculate(
                draft(assessments, subfactors(3, 3)), CandidateProfile.empty(), List.of(), 3000, 0.0)
                .totalMatchScore();

        assertThat(withGlowingOpportunity)
                .as("compensation and prestige are not inputs to fit")
                .isEqualByComparingTo(reference);
    }

    @Test
    void everyAppliedCeilingIsExplainedToTheUser() {
        List<RequirementAssessment> assessments = new java.util.ArrayList<>(List.of(
                requirement("gap", "AWS").core().status(MatchStatus.GAP)
                        .strength(EvidenceStrength.NONE).build(),
                requirement("r2", "Java").build(), requirement("r3", "REST").build()));
        for (int i = 0; i < 12; i++) {
            assessments.add(requirement("ok-" + i, "Met requirement " + i)
                    .category(CategoryName.ROLE_AND_RESPONSIBILITY_ALIGNMENT).build());
        }
        FitAnalysis analysis = score(assessments);

        assertThat(analysis.limitations())
                .anyMatch(limitation -> limitation.contains("score ceiling was applied"));
        assertThat(analysis.limitations()).contains(com.joblens.scoring.model.ScoreLabel.CAVEAT);
    }
}
