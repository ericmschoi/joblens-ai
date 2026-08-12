package com.joblens.analysis.validate;

import static org.assertj.core.api.Assertions.assertThat;

import com.joblens.analysis.model.AnalysisDraft;
import com.joblens.analysis.model.Assessment;
import com.joblens.analysis.model.CategoryName;
import com.joblens.analysis.model.Criticality;
import com.joblens.analysis.model.EvidenceMatch;
import com.joblens.analysis.model.EvidenceRelation;
import com.joblens.analysis.model.EvidenceStrength;
import com.joblens.analysis.model.Importance;
import com.joblens.analysis.model.InterviewPreparation;
import com.joblens.analysis.model.MatchStatus;
import com.joblens.analysis.model.NarrativeAssessment;
import com.joblens.analysis.model.OpportunityValue;
import com.joblens.analysis.model.RequirementAssessment;
import com.joblens.analysis.model.RequirementKind;
import com.joblens.analysis.model.ResumePositioningAdvice;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A quote that is not in the resume is not evidence, whatever the model says about it. */
class EvidenceGroundingCheckerTest {

    private static final String RESUME = """
            Senior Software Engineer, Northwind Systems   Mar 2021 - Present
            - Designed and shipped a Spring Boot payments service handling 4,000 requests per minute.
            - Rebuilt the partner onboarding flow in React and TypeScript.
            """;

    private final EvidenceGroundingChecker checker = new EvidenceGroundingChecker();

    private static AnalysisDraft draftWith(MatchStatus status, String... quotes) {
        List<EvidenceMatch> evidence = java.util.Arrays.stream(quotes)
                .map(quote -> new EvidenceMatch("req-1", Importance.REQUIRED, MatchStatus.STRONG_MATCH,
                        EvidenceRelation.DIRECT, EvidenceStrength.STRONG, quote, "EXPERIENCE", "r", false))
                .toList();

        RequirementAssessment assessment = new RequirementAssessment("req-1", "Spring Boot",
                RequirementKind.TECHNICAL, Importance.REQUIRED, Criticality.CORE, null,
                CategoryName.CORE_TECHNICAL_STACK, status, EvidenceRelation.DIRECT,
                EvidenceStrength.STRONG, evidence, "r");

        NarrativeAssessment narrative = new NarrativeAssessment("h", "d", List.of(), List.of());
        return new AnalysisDraft(AnalysisDraft.SCHEMA_VERSION, List.of(assessment), List.of(),
                narrative, narrative, narrative,
                new OpportunityValue(Assessment.unknown("e"), Assessment.unknown("e"),
                        Assessment.unknown("e")),
                new ResumePositioningAdvice(List.of(), List.of(), List.of(), List.of(), List.of()),
                new InterviewPreparation(List.of(), List.of(), List.of(), List.of()),
                "r", List.of());
    }

    @Test
    void keepsAQuoteThatIsActuallyInTheResume() {
        EvidenceGroundingChecker.Result result = checker.check(
                draftWith(MatchStatus.STRONG_MATCH, "shipped a Spring Boot payments service"), RESUME);

        assertThat(result.groundedCount()).isEqualTo(1);
        assertThat(result.droppedCount()).isZero();
        assertThat(result.draft().requirementAssessments().getFirst().evidence())
                .singleElement()
                .satisfies(evidence -> assertThat(evidence.grounded()).isTrue());
    }

    @Test
    void toleratesDifferentWhitespaceAndCasing() {
        EvidenceGroundingChecker.Result result = checker.check(
                draftWith(MatchStatus.STRONG_MATCH, "Rebuilt   the PARTNER onboarding flow"), RESUME);

        assertThat(result.groundedCount()).isEqualTo(1);
    }

    @Test
    void dropsAQuoteTheModelInvented() {
        EvidenceGroundingChecker.Result result = checker.check(
                draftWith(MatchStatus.STRONG_MATCH, "Led a team of twelve engineers at Globex"), RESUME);

        assertThat(result.droppedCount()).isEqualTo(1);
        assertThat(result.draft().requirementAssessments().getFirst().evidence()).isEmpty();
    }

    @Test
    void aMatchThatLostAllItsEvidenceBecomesUnknownRatherThanAGap() {
        EvidenceGroundingChecker.Result result = checker.check(
                draftWith(MatchStatus.STRONG_MATCH, "Led a team of twelve engineers at Globex"), RESUME);

        assertThat(result.draft().requirementAssessments().getFirst().status())
                .as("a fabricated quote says nothing about what the candidate has or has not done")
                .isEqualTo(MatchStatus.UNKNOWN);
    }

    @Test
    void keepsTheMatchWhenAtLeastOneQuoteSurvives() {
        EvidenceGroundingChecker.Result result = checker.check(
                draftWith(MatchStatus.STRONG_MATCH, "Led a team of twelve engineers at Globex",
                        "shipped a Spring Boot payments service"), RESUME);

        assertThat(result.draft().requirementAssessments().getFirst().status())
                .isEqualTo(MatchStatus.STRONG_MATCH);
        assertThat(result.groundedCount()).isEqualTo(1);
        assertThat(result.droppedCount()).isEqualTo(1);
    }

    @Test
    void leavesARequirementThatNeverClaimedEvidenceAlone() {
        EvidenceGroundingChecker.Result result = checker.check(draftWith(MatchStatus.UNKNOWN), RESUME);

        assertThat(result.draft().requirementAssessments().getFirst().status())
                .isEqualTo(MatchStatus.UNKNOWN);
        assertThat(result.droppedCount()).isZero();
    }

    @Test
    void reportsHowMuchOfTheEvidenceHadToBeDiscarded() {
        EvidenceGroundingChecker.Result result = checker.check(
                draftWith(MatchStatus.STRONG_MATCH, "Led a team of twelve engineers at Globex",
                        "shipped a Spring Boot payments service"), RESUME);

        assertThat(result.groundingFailureRatio()).isEqualTo(0.5);
    }

    @Test
    void refusesToTreatAVeryShortQuoteAsEvidence() {
        EvidenceGroundingChecker.Result result = checker.check(
                draftWith(MatchStatus.STRONG_MATCH, "Java"), RESUME);

        assertThat(result.droppedCount())
                .as("a four-character quote matches by accident and proves nothing")
                .isEqualTo(1);
    }
}
