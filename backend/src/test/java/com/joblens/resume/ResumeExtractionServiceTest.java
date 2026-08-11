package com.joblens.resume;

import static org.assertj.core.api.Assertions.assertThat;

import com.joblens.document.ExtractionWarning;
import com.joblens.document.WarningCode;
import com.joblens.testsupport.PdfFixtureFactory;
import com.joblens.testsupport.TestProperties;
import org.junit.jupiter.api.Test;

class ResumeExtractionServiceTest {

    private final ResumeExtractionService service = new ResumeExtractionService(
            new PdfValidationService(TestProperties.defaults()),
            new PdfTextExtractionService(TestProperties.defaults()),
            new ResumeNormalizer());

    @Test
    void alwaysReturnsTheFullRawTextEvenWhenNoStructureCanBeParsed() {
        ResumeExtractionResult result = service.extract(PdfFixtureFactory.unstructuredProse());

        assertThat(result.profile().workExperiences())
                .as("the prose fixture has no headings, so the structured view is empty by design")
                .isEmpty();
        assertThat(result.rawText())
                .as("a failed structural parse must not cost the user their text")
                .contains("Northwind Systems", "Lakeshore Digital", "University of Waterloo",
                        "carried the on-call pager");
        assertThat(result.rawText().length()).isGreaterThan(600);
    }

    @Test
    void explainsBothFailuresWhenNothingCouldBeStructured() {
        ResumeExtractionResult result = service.extract(PdfFixtureFactory.unstructuredProse());

        assertThat(result.warnings().stream().map(ExtractionWarning::code))
                .contains(WarningCode.NO_SECTIONS_DETECTED, WarningCode.NO_ROLES_DETECTED);
    }

    @Test
    void marksEveryExtractionAsNeedingReview() {
        ResumeExtractionResult clean = service.extract(PdfFixtureFactory.oneColumnResume());

        assertThat(clean.reviewStatus())
                .as("a clean parse is still only a guess until the candidate says otherwise")
                .isEqualTo(ResumeReviewStatus.REVIEW_REQUIRED);
        assertThat(clean.evidenceAbsencePolicy()).isEqualTo(EvidenceAbsencePolicy.MUST_BE_UNKNOWN);
    }

    @Test
    void keepsTruncatedTextTogetherWithAnExplicitWarning() {
        ResumeExtractionService constrained = new ResumeExtractionService(
                new PdfValidationService(TestProperties.defaults()),
                new PdfTextExtractionService(TestProperties.withResume(
                        new com.joblens.config.JoblensProperties.Resume(
                                TestProperties.MAX_FILE_SIZE_BYTES, TestProperties.MAX_PAGE_COUNT, 400, 600))),
                new ResumeNormalizer());

        ResumeExtractionResult result = constrained.extract(
                PdfFixtureFactory.multiPageWithRepeatedHeaderFooter(3));

        assertThat(result.warnings().stream().map(ExtractionWarning::code))
                .as("shortened text must never be returned silently")
                .contains(WarningCode.TEXT_TRUNCATED);
        assertThat(result.rawText()).isNotEmpty();
        assertThat(result.rawText().length()).isLessThanOrEqualTo(600);
    }
}
