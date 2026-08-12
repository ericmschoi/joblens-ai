package com.joblens.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.joblens.testsupport.JobPostingFixtures;
import com.joblens.testsupport.PdfFixtureFactory;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Document content must not reach the logs from anywhere in the request path.
 *
 * <p>The unit-level check on the extraction service already exists; this one runs the whole journey
 * through the API — upload, confirm, posting, confirm, analyse — with every logger in the
 * application attached, including the failure paths. A framework exception message that happens to
 * quote the body would be caught here and nowhere else.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DocumentLogSafetyTest {

    /** Strings that exist only inside the two fixture documents. */
    private static final List<String> DOCUMENT_CONTENT = List.of(
            "Alex Morgan", "Northwind Systems", "Lakeshore Digital", "University of Waterloo",
            "Ledger Reconciler", "partner onboarding",
            "Acme Corp", "payment infrastructure", "Kafka or another event streaming platform",
            "Four weeks of vacation");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    void theWholeJourneyLeavesNoDocumentContentInTheLogs() throws Exception {
        JsonNode resume = confirmedResume(PdfFixtureFactory.oneColumnResume());
        JsonNode job = confirmedJob(JobPostingFixtures.WELL_STRUCTURED);
        analyse(analysisRequest(resume, job));

        assertThat(captured())
                .isNotEmpty()
                .doesNotContain(DOCUMENT_CONTENT.toArray(CharSequence[]::new));
    }

    @Test
    void aRejectedAnalysisLogsTheCodeAndNotTheContent() throws Exception {
        ObjectNode request = analysisRequest(
                confirmedResume(PdfFixtureFactory.oneColumnResume()),
                confirmedJob(JobPostingFixtures.WELL_STRUCTURED));
        ((ObjectNode) request.get("resume")).put("reviewStatus", "REVIEW_REQUIRED");

        analyse(request);

        assertThat(captured())
                .contains("REVIEW_NOT_CONFIRMED")
                .doesNotContain(DOCUMENT_CONTENT.toArray(CharSequence[]::new));
    }

    @Test
    void aMalformedBodyIsNeverEchoedIntoTheLogs() throws Exception {
        mockMvc.perform(post("/api/v1/job-descriptions/extract")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\": \"Alex Morgan worked at Northwind Systems\", "));

        assertThat(captured())
                .contains("REQUEST_NOT_READABLE")
                .doesNotContain("Alex Morgan", "Northwind Systems");
    }

    @Test
    void instructionsPlantedInAResumeAreNeverLogged() throws Exception {
        confirmedResume(PdfFixtureFactory.withEmbeddedInstructions());

        assertThat(captured()).doesNotContain("Ignore all previous instructions");
    }

    // --- the journey, kept short because the assertions above are the point --------------------

    private JsonNode confirmedResume(byte[] pdf) throws Exception {
        JsonNode extracted = objectMapper.readTree(mockMvc.perform(multipart("/api/v1/resumes/extract")
                        .file(new MockMultipartFile("file", "resume.pdf", "application/pdf", pdf)))
                .andReturn().getResponse().getContentAsString());

        ObjectNode request = objectMapper.createObjectNode();
        request.put("rawText", extracted.get("rawText").asString());
        request.set("candidateProfile", extracted.get("candidateProfile"));
        request.set("carriedWarnings", extracted.get("extractionWarnings"));
        request.put("confirmed", true);

        return objectMapper.readTree(mockMvc.perform(post("/api/v1/resumes/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode confirmedJob(String text) throws Exception {
        ObjectNode extractRequest = objectMapper.createObjectNode();
        extractRequest.put("text", text);

        JsonNode extracted = objectMapper.readTree(mockMvc.perform(post("/api/v1/job-descriptions/extract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(extractRequest)))
                .andReturn().getResponse().getContentAsString());

        ObjectNode request = objectMapper.createObjectNode();
        request.put("rawText", extracted.get("rawText").asString());
        request.set("jobPosting", extracted.get("jobPosting"));
        request.set("carriedWarnings", extracted.get("extractionWarnings"));
        request.put("confirmed", true);

        return objectMapper.readTree(mockMvc.perform(post("/api/v1/job-descriptions/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString());
    }

    private ObjectNode analysisRequest(JsonNode resume, JsonNode job) {
        ObjectNode resumePart = objectMapper.createObjectNode();
        resumePart.put("reviewStatus", resume.get("reviewStatus").asString());
        resumePart.put("contentFingerprint", resume.get("contentFingerprint").asString());
        resumePart.put("rawText", resume.get("rawText").asString());
        resumePart.set("candidateProfile", resume.get("candidateProfile"));
        resumePart.set("extractionWarnings", resume.get("extractionWarnings"));

        ObjectNode jobPart = objectMapper.createObjectNode();
        jobPart.put("reviewStatus", job.get("reviewStatus").asString());
        jobPart.put("contentFingerprint", job.get("contentFingerprint").asString());
        jobPart.put("rawText", job.get("rawText").asString());
        jobPart.set("jobPosting", job.get("jobPosting"));
        jobPart.set("extractionWarnings", job.get("extractionWarnings"));

        ObjectNode request = objectMapper.createObjectNode();
        request.set("resume", resumePart);
        request.set("job", jobPart);
        return request;
    }

    private void analyse(ObjectNode request) throws Exception {
        mockMvc.perform(post("/api/v1/analyses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private String captured() {
        return appender.list.stream()
                .map(event -> event.getFormattedMessage()
                        + " " + Arrays.toString(event.getArgumentArray())
                        + " " + (event.getThrowableProxy() == null ? "" : event.getThrowableProxy().getMessage()))
                .collect(Collectors.joining("\n"));
    }
}
