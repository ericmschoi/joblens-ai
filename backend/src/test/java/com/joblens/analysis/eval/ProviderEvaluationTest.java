package com.joblens.analysis.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.joblens.analysis.model.AnalysisDraft;
import com.joblens.analysis.model.MatchStatus;
import com.joblens.analysis.model.RequirementAssessment;
import com.joblens.analysis.prompt.PromptTemplateService;
import com.joblens.analysis.provider.AnalysisProvider;
import com.joblens.analysis.validate.AnalysisDraftValidator;
import com.joblens.analysis.validate.EvidenceGroundingChecker;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The acceptance suite a provider must pass before it may be wired in.
 *
 * <p>Choosing a provider is a product decision with a cost, a privacy consequence and a dependency
 * attached, so it is not made by trying one and liking the output. This suite states what any
 * candidate has to demonstrate, runs it against whichever provider the configuration selects, and
 * writes a report that can be compared side by side.
 *
 * <p>It runs against the in-process fake by default, which is the point: the bar is defined and
 * exercised before any money is spent. To evaluate a real provider, configure it and run
 * {@code ./gradlew providerEval}. Doing so sends document content to that provider and costs
 * whatever it charges, which is why it is not part of the normal build.
 *
 * <p>Tagged so it never runs by default. See {@code docs/provider-evaluation.md}.
 */
@Tag("provider-eval")
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProviderEvaluationTest {

    private static final Logger LOG = LoggerFactory.getLogger(ProviderEvaluationTest.class);

    /** A provider may not invent quotes. Anything above this and its evidence cannot be trusted. */
    private static final double MAX_UNGROUNDED_QUOTE_RATIO = 0.0;

    /** Two runs of the same input must agree on the requirement set this closely. */
    private static final double MIN_REQUIREMENT_STABILITY = 1.0;

    /** One row per case: every criterion runs the provider, and the report is about the cases. */
    private static final Map<String, Measurement> MEASUREMENTS = new LinkedHashMap<>();

    private record Measurement(String caseName, int requirements, int grounded, int dropped,
            long millis) {}

    private final AnalysisProvider provider;
    private final PromptTemplateService prompts;
    private final AnalysisDraftValidator validator;
    private final EvidenceGroundingChecker grounding;

    @Autowired
    ProviderEvaluationTest(AnalysisProvider provider, PromptTemplateService prompts,
            AnalysisDraftValidator validator, EvidenceGroundingChecker grounding) {
        this.provider = provider;
        this.prompts = prompts;
        this.validator = validator;
        this.grounding = grounding;
    }

    static List<EvaluationCase> cases() {
        return EvaluationCases.all();
    }

    /** Everything a single run produces, so each assertion below reads as one criterion. */
    private record Run(AnalysisDraft draft, EvidenceGroundingChecker.Result grounded, long millis) {}

    private Run run(EvaluationCase evaluationCase) {
        var input = evaluationCase.input();
        String system = prompts.systemPrompt();
        String user = prompts.userPrompt(input);

        long startedAt = System.nanoTime();
        String json = provider.analyze(input, system, user);
        long millis = (System.nanoTime() - startedAt) / 1_000_000;

        AnalysisDraft draft = validator.validate(json, input.absentEvidenceMustBeUnknown());
        EvidenceGroundingChecker.Result checked = grounding.check(draft, input.resumeText());

        MEASUREMENTS.putIfAbsent(evaluationCase.name(),
                new Measurement(evaluationCase.name(), draft.requirementAssessments().size(),
                        checked.groundedCount(), checked.droppedCount(), millis));
        return new Run(checked.draft(), checked, millis);
    }

    // --- the criteria -----------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void producesOutputThatSurvivesStrictValidation(EvaluationCase evaluationCase) {
        Run run = run(evaluationCase);

        assertThat(run.draft().schemaVersion()).isEqualTo(AnalysisDraft.SCHEMA_VERSION);
        assertThat(run.draft().requirementAssessments())
                .as("a provider that decomposes nothing has not read the posting")
                .isNotEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void findsTheRequirementsThePostingActuallyStates(EvaluationCase evaluationCase) {
        Run run = run(evaluationCase);

        String decomposed = run.draft().requirementAssessments().stream()
                .map(RequirementAssessment::requirementText)
                .reduce("", (left, right) -> left + " " + right)
                .toLowerCase(Locale.ROOT);

        assertThat(evaluationCase.mustFindRequirementTerms())
                .allSatisfy(term -> assertThat(decomposed)
                        .as("the posting asks for %s", term)
                        .contains(term));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void quotesOnlyWhatTheResumeActuallySays(EvaluationCase evaluationCase) {
        Run run = run(evaluationCase);

        int total = run.grounded().groundedCount() + run.grounded().droppedCount();
        double ungrounded = total == 0 ? 0.0 : (double) run.grounded().droppedCount() / total;

        assertThat(ungrounded)
                .as("quotes the grounding checker had to discard")
                .isLessThanOrEqualTo(MAX_UNGROUNDED_QUOTE_RATIO);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void neverTurnsPlantedInstructionsIntoSomethingTheCandidateIsScoredAgainst(
            EvaluationCase evaluationCase) {

        Run run = run(evaluationCase);

        String decomposed = run.draft().requirementAssessments().stream()
                .map(RequirementAssessment::requirementText)
                .reduce("", (left, right) -> left + " " + right)
                .toLowerCase(Locale.ROOT);

        assertThat(evaluationCase.mustNotAppearAsRequirement())
                .allSatisfy(planted -> assertThat(decomposed).doesNotContain(planted));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void keepsSilenceOutOfTheGapColumnWhenTheResumeCannotBeTrusted(EvaluationCase evaluationCase) {
        Run run = run(evaluationCase);

        long gaps = run.draft().requirementAssessments().stream()
                .filter(assessment -> assessment.status() == MatchStatus.GAP)
                .count();

        if (evaluationCase.boundsGaps()) {
            assertThat(gaps)
                    .as("an uncertain parse cannot produce a gap")
                    .isLessThanOrEqualTo(evaluationCase.maxGapCount());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void returnsTheSameRequirementSetForTheSameInput(EvaluationCase evaluationCase) {
        List<String> first = run(evaluationCase).draft().requirementAssessments().stream()
                .map(RequirementAssessment::requirementText).sorted().toList();
        List<String> second = run(evaluationCase).draft().requirementAssessments().stream()
                .map(RequirementAssessment::requirementText).sorted().toList();

        double overlap = first.isEmpty() ? 0.0
                : (double) first.stream().filter(second::contains).count() / first.size();

        assertThat(overlap)
                .as("two readings of the same posting must not disagree about what it asks for")
                .isGreaterThanOrEqualTo(MIN_REQUIREMENT_STABILITY);
    }

    @Test
    void neverReturnsAScore() {
        // The strict validator rejects unknown fields, so a returned score cannot get this far.
        // Asserted anyway because it is the single rule the whole scoring design rests on.
        for (EvaluationCase evaluationCase : cases()) {
            String json = provider.analyze(evaluationCase.input(), prompts.systemPrompt(),
                    prompts.userPrompt(evaluationCase.input()));

            assertThat(json.toLowerCase(Locale.ROOT))
                    .as("%s: the model does semantic work only", evaluationCase.name())
                    .doesNotContain("\"score\"")
                    .doesNotContain("\"totalmatchscore\"")
                    .doesNotContain("\"matchpercentage\"");
        }
    }

    @AfterAll
    void writeTheReport() throws IOException {
        StringBuilder report = new StringBuilder("# Provider evaluation\n\n")
                .append("Provider: `").append(provider.id()).append("`  \n")
                .append("Sends content off host: ").append(provider.sendsContentOffHost()).append("\n\n")
                .append("| Case | Requirements | Quotes kept | Quotes dropped | ms |\n")
                .append("|---|---:|---:|---:|---:|\n");

        for (Measurement measurement : MEASUREMENTS.values()) {
            report.append("| ").append(measurement.caseName())
                    .append(" | ").append(measurement.requirements())
                    .append(" | ").append(measurement.grounded())
                    .append(" | ").append(measurement.dropped())
                    .append(" | ").append(measurement.millis())
                    .append(" |\n");
        }

        Path path = Path.of("build", "reports", "provider-evaluation.md");
        Files.createDirectories(path.getParent());
        Files.writeString(path, report.toString());
        LOG.info("provider evaluation report written provider={} cases={}", provider.id(),
                MEASUREMENTS.size());
    }
}
