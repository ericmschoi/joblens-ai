package com.joblens.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.joblens.testsupport.PdfFixtureFactory;
import com.joblens.testsupport.TestProperties;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Resume content must never reach the logs. This is checked rather than trusted, because a log line
 * added in passing is the easiest way for personal data to escape a system that otherwise stores
 * nothing.
 */
class ResumeLogSafetyTest {

    /** Distinctive strings that only exist inside the fixture document. */
    private static final List<String> DOCUMENT_CONTENT = List.of(
            "Alex Morgan", "Northwind Systems", "Lakeshore Digital", "University of Waterloo",
            "Ledger Reconciler", "partner onboarding");

    private final ResumeExtractionService service = new ResumeExtractionService(
            new PdfValidationService(TestProperties.defaults()),
            new PdfTextExtractionService(TestProperties.defaults()),
            new ResumeNormalizer());

    private Logger rootLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void captureLogs() {
        rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        appender = new ListAppender<>();
        appender.start();
        rootLogger.addAppender(appender);
    }

    @AfterEach
    void releaseLogs() {
        rootLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void logsProgressWithoutLoggingTheDocument() {
        ResumeExtractionResult result = service.extract(PdfFixtureFactory.oneColumnResume());

        String logs = captured();

        assertThat(logs)
                .as("the extraction must still be traceable")
                .contains("resume extracted", result.extractionId());
        assertThat(logs).doesNotContain(DOCUMENT_CONTENT.toArray(CharSequence[]::new));
    }

    @Test
    void logsNothingFromTheDocumentWhenParsingFails() {
        Throwable thrown = catchThrowable(() -> service.extract(PdfFixtureFactory.corruptPdf()));

        assertThat(thrown).isNotNull();
        assertThat(captured()).doesNotContain(DOCUMENT_CONTENT.toArray(CharSequence[]::new));
    }

    @Test
    void keepsInstructionLikeDocumentTextOutOfTheLogsToo() {
        service.extract(PdfFixtureFactory.withEmbeddedInstructions());

        assertThat(captured()).doesNotContain("Ignore all previous instructions");
    }

    private String captured() {
        return appender.list.stream()
                .map(event -> event.getFormattedMessage()
                        + " " + Arrays.toString(event.getArgumentArray())
                        + " " + (event.getThrowableProxy() == null ? "" : event.getThrowableProxy().getMessage()))
                .collect(Collectors.joining("\n"));
    }
}
