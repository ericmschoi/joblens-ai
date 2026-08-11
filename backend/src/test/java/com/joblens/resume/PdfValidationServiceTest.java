package com.joblens.resume;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.joblens.error.ApiException;
import com.joblens.error.ErrorCode;
import com.joblens.testsupport.PdfFixtureFactory;
import com.joblens.testsupport.TestProperties;
import org.junit.jupiter.api.Test;

class PdfValidationServiceTest {

    private final PdfValidationService validation = new PdfValidationService(TestProperties.defaults());

    @Test
    void acceptsATextBasedPdf() {
        assertThatCode(() -> validation.validate(PdfFixtureFactory.oneColumnResume())).doesNotThrowAnyException();
    }

    @Test
    void rejectsAnEmptyUpload() {
        assertThatThrownBy(() -> validation.validate(new byte[0]))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).errorCode())
                .isEqualTo(ErrorCode.FILE_MISSING);
    }

    @Test
    void rejectsAFileThatIsNotAPdfWhateverItIsCalled() {
        assertThatThrownBy(() -> validation.validate(PdfFixtureFactory.notAPdf()))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).errorCode())
                .isEqualTo(ErrorCode.FILE_TYPE_NOT_SUPPORTED);
    }

    @Test
    void acceptsAFileExactlyAtTheSizeLimit() {
        byte[] atLimit = PdfFixtureFactory.ofSize((int) TestProperties.MAX_FILE_SIZE_BYTES);

        assertThatCode(() -> validation.validate(atLimit)).doesNotThrowAnyException();
    }

    @Test
    void rejectsAFileOneByteOverTheSizeLimit() {
        byte[] overLimit = PdfFixtureFactory.ofSize((int) TestProperties.MAX_FILE_SIZE_BYTES + 1);

        assertThatThrownBy(() -> validation.validate(overLimit))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).errorCode())
                .isEqualTo(ErrorCode.FILE_TOO_LARGE);
    }

    @Test
    void checksSizeBeforeContentSoAHugeFileIsNeverParsed() {
        byte[] hugeNonPdf = new byte[(int) TestProperties.MAX_FILE_SIZE_BYTES + 1];

        assertThatThrownBy(() -> validation.validate(hugeNonPdf))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).errorCode())
                .isEqualTo(ErrorCode.FILE_TOO_LARGE);
    }
}
