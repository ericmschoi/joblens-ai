package com.joblens.analysis.validate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.joblens.analysis.model.AnalysisDraft;
import com.joblens.analysis.model.MatchStatus;
import com.joblens.error.ApiException;
import com.joblens.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Model output is untrusted input, and this is where that is enforced. */
class AnalysisDraftValidatorTest {

    private final AnalysisDraftValidator validator = new AnalysisDraftValidator();

    private static String draftJson(String requirementOverrides) {
        return """
                {
                  "schemaVersion": "analysis-draft/v1",
                  "requirementAssessments": [
                    {
                      "id": "req-1",
                      "requirementText": "Strong Java",
                      "kind": "TECHNICAL",
                      "importance": "REQUIRED",
                      "criticality": "CORE",
                      "alternativeGroupId": null,
                      "primaryCategory": "CORE_TECHNICAL_STACK",
                      "status": "%s",
                      "relation": "DIRECT",
                      "evidenceStrength": "STRONG",
                      "evidence": [],
                      "rationale": "why"
                    }
                  ],
                  "subfactorJudgements": [],
                  "roleAlignment": {"headline":"h","detail":"d","supportingEvidenceIds":[],"concerns":[]},
                  "seniorityAlignment": {"headline":"h","detail":"d","supportingEvidenceIds":[],"concerns":[]},
                  "realisticCompetitiveness": {"headline":"h","detail":"d","supportingEvidenceIds":[],"concerns":[]},
                  "opportunityValue": {
                    "careerGrowth": {"rating":"MODERATE","basis":"INFERRED_FROM_POSTING","explanation":"e"},
                    "compensation": {"rating":"UNKNOWN","basis":"NOT_AVAILABLE","explanation":"e"},
                    "companyOutlook": {"rating":"UNKNOWN","basis":"NOT_AVAILABLE","explanation":"e"}
                  },
                  "resumePositioning": {"reorderSuggestions":[],"emphasisSuggestions":[],
                    "terminologyAlignment":[],"deemphasizeSuggestions":[],"faithfulRewrites":[]},
                  "interviewPreparation": {"likelyQuestions":[],"talkingPoints":[],
                    "gapsToExplain":[],"questionsToAsk":[]},
                  "finalRationale": "r",
                  "limitations": []
                }
                """.formatted(requirementOverrides);
    }

    private static ErrorCode codeOf(Throwable thrown) {
        return ((ApiException) thrown).errorCode();
    }

    @Test
    void acceptsAWellFormedDraft() {
        AnalysisDraft draft = validator.validate(draftJson("STRONG_MATCH"), false);

        assertThat(draft.requirementAssessments()).hasSize(1);
        assertThat(draft.schemaVersion()).isEqualTo(AnalysisDraft.SCHEMA_VERSION);
    }

    @Test
    void rejectsADraftCarryingAScore() {
        String withScore = draftJson("STRONG_MATCH")
                .replace("\"finalRationale\": \"r\"", "\"totalMatchScore\": 4.4, \"finalRationale\": \"r\"");

        assertThatThrownBy(() -> validator.validate(withScore, false))
                .as("scoring belongs to the backend; a provider returning one is off-contract")
                .isInstanceOf(ApiException.class)
                .extracting(AnalysisDraftValidatorTest::codeOf)
                .isEqualTo(ErrorCode.AI_OUTPUT_INVALID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not json at all", "{}", "{\"schemaVersion\":\"analysis-draft/v0\"}"})
    void rejectsAnythingThatIsNotThisContract(String payload) {
        assertThatThrownBy(() -> validator.validate(payload, false))
                .isInstanceOf(ApiException.class)
                .extracting(AnalysisDraftValidatorTest::codeOf)
                .isEqualTo(ErrorCode.AI_OUTPUT_INVALID);
    }

    @Test
    void rejectsADraftWithNoRequirements() {
        String empty = draftJson("STRONG_MATCH")
                .replaceAll("\"requirementAssessments\": \\[[\\s\\S]*?\\n  \\],", "\"requirementAssessments\": [],");

        assertThatThrownBy(() -> validator.validate(empty, false))
                .isInstanceOf(ApiException.class)
                .extracting(AnalysisDraftValidatorTest::codeOf)
                .isEqualTo(ErrorCode.AI_OUTPUT_INVALID);
    }

    @Test
    void rejectsEvidenceAttachedToTheWrongRequirement() {
        String crossed = draftJson("STRONG_MATCH").replace("\"evidence\": []",
                """
                "evidence": [{"requirementId":"req-99","importance":"REQUIRED","status":"STRONG_MATCH",
                  "relation":"DIRECT","strength":"STRONG","resumeQuote":"q","sourceLocator":"s",
                  "rationale":"r","grounded":false}]""");

        assertThatThrownBy(() -> validator.validate(crossed, false))
                .isInstanceOf(ApiException.class)
                .extracting(AnalysisDraftValidatorTest::codeOf)
                .isEqualTo(ErrorCode.AI_OUTPUT_INVALID);
    }

    @Test
    void rejectsAConfidentClaimAboutACompanyItCannotKnowAnythingAbout() {
        String invented = draftJson("STRONG_MATCH").replace(
                "\"companyOutlook\": {\"rating\":\"UNKNOWN\",\"basis\":\"NOT_AVAILABLE\",\"explanation\":\"e\"}",
                "\"companyOutlook\": {\"rating\":\"STRONG\",\"basis\":\"STATED_IN_POSTING\",\"explanation\":\"e\"}");

        assertThatThrownBy(() -> validator.validate(invented, false))
                .as("JobLens does no company research, so this could only be recalled from training")
                .isInstanceOf(ApiException.class)
                .extracting(AnalysisDraftValidatorTest::codeOf)
                .isEqualTo(ErrorCode.AI_OUTPUT_INVALID);
    }

    @Test
    void downgradesGapsToUnknownWhenTheResumeIsNotTrustworthy() {
        AnalysisDraft draft = validator.validate(draftJson("GAP"), true);

        assertThat(draft.requirementAssessments().getFirst().status())
                .as("a parser miss must not become a finding about the candidate")
                .isEqualTo(MatchStatus.UNKNOWN);
        assertThat(draft.limitations()).anyMatch(limitation -> limitation.contains("not confirmed"));
    }

    @Test
    void leavesGapsAloneWhenTheResumeWasConfirmedAndClean() {
        AnalysisDraft draft = validator.validate(draftJson("GAP"), false);

        assertThat(draft.requirementAssessments().getFirst().status()).isEqualTo(MatchStatus.GAP);
        assertThat(draft.limitations()).isEmpty();
    }

    @Test
    void neverPutsTheProvidersOwnErrorTextInFrontOfTheUser() {
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> validator.validate("{\"schemaVersion\": \"analysis-draft/v1\", \"junk\": 1}", false));

        assertThat(((ApiException) thrown).detail())
                .isEqualTo("The analysis result did not match the expected format.");
    }
}
