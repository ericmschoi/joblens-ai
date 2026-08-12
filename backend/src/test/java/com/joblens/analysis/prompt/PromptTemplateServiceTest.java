package com.joblens.analysis.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import com.joblens.analysis.AnalysisInput;
import com.joblens.jobposting.model.JobPosting;
import com.joblens.resume.model.CandidateProfile;
import java.util.List;
import org.junit.jupiter.api.Test;

class PromptTemplateServiceTest {

    private final PromptTemplateService prompts = new PromptTemplateService();

    private static AnalysisInput input(boolean fullTextRequirements, boolean absentIsUnknown) {
        return new AnalysisInput(
                "Senior Software Engineer at Northwind Systems, shipping payment services in Java.",
                CandidateProfile.empty(),
                "Backend Engineer. Required Qualifications: Strong Java.",
                JobPosting.empty(),
                fullTextRequirements,
                absentIsUnknown);
    }

    @Test
    void tellsTheModelPlainlyThatItMustNotProduceAScore() {
        assertThat(prompts.systemPrompt())
                .contains("never return a score")
                .contains("JobLens computes every score itself");
    }

    @Test
    void spellsOutTheDifferenceBetweenAGapAndAnUnknown() {
        assertThat(prompts.systemPrompt())
                .contains("`GAP` and `UNKNOWN` are different claims");
    }

    @Test
    void wrapsEachDocumentInAFenceTheDocumentCannotPredict() {
        String prompt = prompts.userPrompt(input(false, false));

        assertThat(prompt).contains("-----RESUME ", "-----JOB_POSTING ");
        assertThat(prompt).contains("Everything between a pair of markers is");
    }

    @Test
    void usesADifferentNonceEveryTime() {
        String first = prompts.userPrompt(input(false, false));
        String second = prompts.userPrompt(input(false, false));

        assertThat(nonceOf(first))
                .as("a document cannot close a fence it cannot guess")
                .isNotEqualTo(nonceOf(second));
    }

    @Test
    void carriesTheRequirementSourceRuleIntoThePrompt() {
        assertThat(prompts.userPrompt(input(true, false)))
                .contains("Read the whole description and decompose requirements from it");
        assertThat(prompts.userPrompt(input(false, false)))
                .contains("recognised cleanly and may be used");
    }

    @Test
    void carriesTheEvidenceAbsenceRuleIntoThePrompt() {
        assertThat(prompts.userPrompt(input(false, true)))
                .contains("You may not report GAP in this analysis");
        assertThat(prompts.userPrompt(input(false, false)))
                .contains("you may report GAP");
    }

    @Test
    void leavesNoPlaceholderUnresolved() {
        String prompt = prompts.userPrompt(input(true, true));

        assertThat(prompt).doesNotContain("{{RESUME}}", "{{JOB_POSTING}}", "{{NONCE}}",
                "{{REQUIREMENT_SOURCE_RULE}}", "{{EVIDENCE_ABSENCE_RULE}}");
    }

    @Test
    void includesBothDocumentsVerbatim() {
        String prompt = prompts.userPrompt(input(false, false));

        assertThat(prompt).contains("Northwind Systems", "Required Qualifications: Strong Java");
    }

    private static String nonceOf(String prompt) {
        int start = prompt.indexOf("-----RESUME ") + "-----RESUME ".length();
        return prompt.substring(start, prompt.indexOf("-----", start));
    }
}
