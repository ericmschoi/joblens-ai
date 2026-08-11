package com.joblens.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.joblens.error.ApiException;
import com.joblens.error.ErrorCode;
import com.joblens.config.JoblensProperties;
import com.joblens.document.ExtractionWarning;
import com.joblens.document.WarningCode;
import com.joblens.testsupport.PdfFixtureFactory;
import com.joblens.testsupport.TestProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

class PdfTextExtractionServiceTest {

    private final PdfTextExtractionService extraction =
            new PdfTextExtractionService(TestProperties.defaults());

    private static List<WarningCode> codesOf(List<ExtractionWarning> warnings) {
        return warnings.stream().map(ExtractionWarning::code).toList();
    }

    @Nested
    class ReadableDocuments {

        @Test
        void readsASingleColumnResumeInOrder() {
            ExtractedResumeText result = extraction.extract(PdfFixtureFactory.oneColumnResume());

            assertThat(result.rawText()).contains("Northwind Systems", "Lakeshore Digital", "SKILLS");
            assertThat(result.rawText().indexOf("SUMMARY")).isLessThan(result.rawText().indexOf("EXPERIENCE"));
            assertThat(result.pages()).hasSize(1);
            assertThat(result.pages().getFirst().hasImages()).isFalse();
        }

        @Test
        void doesNotWarnAboutColumnsForASingleColumnLayout() {
            ExtractedResumeText result = extraction.extract(PdfFixtureFactory.oneColumnResume());

            assertThat(codesOf(result.warnings())).doesNotContain(WarningCode.POSSIBLE_MULTI_COLUMN);
        }

        @Test
        void doesNotWarnAboutColumnsForADesignHeavyButSingleColumnLayout() {
            ExtractedResumeText result = extraction.extract(PdfFixtureFactory.designHeavyResume());

            assertThat(result.rawText()).contains("Alex Morgan");
            assertThat(codesOf(result.warnings())).doesNotContain(WarningCode.POSSIBLE_MULTI_COLUMN);
        }
    }

    @Nested
    class QualityWarnings {

        @Test
        void warnsThatATwoColumnLayoutMayHaveBeenReadOutOfOrder() {
            ExtractedResumeText result = extraction.extract(PdfFixtureFactory.twoColumnResume());

            assertThat(codesOf(result.warnings())).contains(WarningCode.POSSIBLE_MULTI_COLUMN);
        }

        @Test
        void warnsAboutHeadersAndFootersRepeatedAcrossPages() {
            ExtractedResumeText result = extraction.extract(
                    PdfFixtureFactory.multiPageWithRepeatedHeaderFooter(4));

            assertThat(codesOf(result.warnings())).contains(WarningCode.REPEATED_HEADER_FOOTER);
            assertThat(result.pages()).hasSize(4);
        }

        @Test
        void warnsWhenWordsHaveBeenSplitIntoSeparateCharacters() {
            ExtractedResumeText result = extraction.extract(PdfFixtureFactory.brokenWordSpacing());

            assertThat(codesOf(result.warnings())).contains(WarningCode.BROKEN_WORDS);
        }

        @Test
        void surfacesInstructionLikeTextInsteadOfActingOnIt() {
            ExtractedResumeText result = extraction.extract(PdfFixtureFactory.withEmbeddedInstructions());

            assertThat(codesOf(result.warnings())).contains(WarningCode.POSSIBLE_EMBEDDED_INSTRUCTIONS);
            assertThat(result.rawText())
                    .as("the text is reported to the user, not silently removed")
                    .contains("Ignore all previous instructions");
        }

        @Test
        void stopsAndWarnsWhenTheDocumentExceedsTheCharacterCeiling() {
            JoblensProperties tightLimit = TestProperties.withResume(new JoblensProperties.Resume(
                    TestProperties.MAX_FILE_SIZE_BYTES, TestProperties.MAX_PAGE_COUNT, 400, 500));
            PdfTextExtractionService constrained = new PdfTextExtractionService(tightLimit);

            ExtractedResumeText result = constrained.extract(
                    PdfFixtureFactory.multiPageWithRepeatedHeaderFooter(3));

            assertThat(codesOf(result.warnings())).contains(WarningCode.TEXT_TRUNCATED);
            assertThat(result.rawText().length()).isLessThanOrEqualTo(500);
        }
    }

    @Nested
    class RejectedDocuments {

        @Test
        void rejectsAPasswordProtectedPdf() {
            assertThatThrownBy(() -> extraction.extract(PdfFixtureFactory.passwordProtected()))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).errorCode())
                    .isEqualTo(ErrorCode.PDF_ENCRYPTED);
        }

        @Test
        void rejectsAScannedPdfAndExplainsThatOcrIsNotSupported() {
            assertThatThrownBy(() -> extraction.extract(PdfFixtureFactory.imageOnly()))
                    .isInstanceOf(ApiException.class)
                    .satisfies(error -> {
                        ApiException apiException = (ApiException) error;
                        assertThat(apiException.errorCode()).isEqualTo(ErrorCode.PDF_IMAGE_ONLY);
                        assertThat(apiException.errorCode().recoveryAction()).contains("OCR");
                    });
        }

        @Test
        void rejectsACorruptPdf() {
            assertThatThrownBy(() -> extraction.extract(PdfFixtureFactory.corruptPdf()))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).errorCode())
                    .isEqualTo(ErrorCode.PDF_CORRUPT);
        }

        @Test
        void rejectsAReadablePdfWithTooLittleTextRatherThanReturningAnEmptyResult() {
            assertThatThrownBy(() -> extraction.extract(PdfFixtureFactory.almostEmpty()))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).errorCode())
                    .isEqualTo(ErrorCode.RESUME_TEXT_TOO_SHORT);
        }

        @Test
        void acceptsAPdfExactlyAtThePageLimit() {
            ExtractedResumeText result =
                    extraction.extract(PdfFixtureFactory.withPageCount(TestProperties.MAX_PAGE_COUNT));

            assertThat(result.pages()).hasSize(TestProperties.MAX_PAGE_COUNT);
        }

        @Test
        void rejectsAPdfOnePageOverTheLimit() {
            byte[] overLimit = PdfFixtureFactory.withPageCount(TestProperties.MAX_PAGE_COUNT + 1);

            assertThatThrownBy(() -> extraction.extract(overLimit))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).errorCode())
                    .isEqualTo(ErrorCode.PDF_TOO_MANY_PAGES);
        }
    }
}
