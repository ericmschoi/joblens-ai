package com.joblens.api.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.joblens.testsupport.JobPostingFixtures;
import com.joblens.testsupport.PdfFixtureFactory;
import org.junit.jupiter.api.Test;
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
 * The whole journey through the API: upload, review, confirm, twice, then analyse.
 *
 * <p>Runs against the default in-process provider, so it needs no API key and makes no outbound
 * call — which is the point of that provider existing.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AnalysisEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode confirmedResume() throws Exception {
        JsonNode extracted = objectMapper.readTree(mockMvc.perform(multipart("/api/v1/resumes/extract")
                        .file(new MockMultipartFile("file", "resume.pdf", "application/pdf",
                                PdfFixtureFactory.oneColumnResume())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        ObjectNode request = objectMapper.createObjectNode();
        request.put("rawText", extracted.get("rawText").asString());
        request.set("candidateProfile", extracted.get("candidateProfile"));
        request.set("carriedWarnings", extracted.get("extractionWarnings"));
        request.put("confirmed", true);

        return objectMapper.readTree(mockMvc.perform(post("/api/v1/resumes/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode confirmedJob() throws Exception {
        ObjectNode extractRequest = objectMapper.createObjectNode();
        extractRequest.put("text", JobPostingFixtures.WELL_STRUCTURED);

        JsonNode extracted = objectMapper.readTree(mockMvc.perform(post("/api/v1/job-descriptions/extract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(extractRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        ObjectNode request = objectMapper.createObjectNode();
        request.put("rawText", extracted.get("rawText").asString());
        request.set("jobPosting", extracted.get("jobPosting"));
        request.set("carriedWarnings", extracted.get("extractionWarnings"));
        request.put("confirmed", true);

        return objectMapper.readTree(mockMvc.perform(post("/api/v1/job-descriptions/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
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

    private String analyse(ObjectNode request, int expectedStatus) throws Exception {
        return mockMvc.perform(post("/api/v1/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void analysesTwoConfirmedDocumentsWithoutAnyApiKey() throws Exception {
        String body = analyse(analysisRequest(confirmedResume(), confirmedJob()), 200);
        JsonNode response = objectMapper.readTree(body);

        assertThat(response.get("schemaVersion").asString()).isEqualTo("analysis/v1");
        assertThat(response.get("analysisMetadata").get("providerId").asString()).isEqualTo("fake");
        assertThat(response.get("analysisMetadata").get("promptVersion").asString()).isEqualTo("v1");
        assertThat(response.get("draft").get("requirementAssessments")).isNotEmpty();
    }

    @Test
    void returnsNoScoreAnywhereInTheResponse() throws Exception {
        String body = analyse(analysisRequest(confirmedResume(), confirmedJob()), 200);

        assertThat(body)
                .as("scores are computed by the backend in the next phase, not returned by the model")
                .doesNotContain("totalMatchScore", "\"score\"", "applicationTier", "recommendation");
    }

    @Test
    void everySurvivingQuoteIsActuallyInTheResume() throws Exception {
        JsonNode resume = confirmedResume();
        JsonNode response = objectMapper.readTree(analyse(analysisRequest(resume, confirmedJob()), 200));

        String resumeText = resume.get("rawText").asString().replaceAll("\\s+", " ").toLowerCase();
        response.get("draft").get("requirementAssessments").forEach(assessment ->
                assessment.get("evidence").forEach(evidence -> {
                    assertThat(evidence.get("grounded").asBoolean()).isTrue();
                    assertThat(resumeText)
                            .contains(evidence.get("resumeQuote").asString()
                                    .replaceAll("\\s+", " ").strip().toLowerCase());
                }));
    }

    @Test
    void refusesToAnalyseSomethingThatWasNeverConfirmed() throws Exception {
        ObjectNode request = analysisRequest(confirmedResume(), confirmedJob());
        ((ObjectNode) request.get("resume")).put("reviewStatus", "REVIEW_REQUIRED");

        assertThat(objectMapper.readTree(analyse(request, 400)).get("code").asString())
                .isEqualTo("REVIEW_NOT_CONFIRMED");
    }

    @Test
    void refusesContentThatChangedAfterItWasConfirmed() throws Exception {
        ObjectNode request = analysisRequest(confirmedResume(), confirmedJob());
        ObjectNode resume = (ObjectNode) request.get("resume");
        resume.put("rawText", resume.get("rawText").asString() + "\nAlso: ten years of Kubernetes.");

        assertThat(objectMapper.readTree(analyse(request, 400)).get("code").asString())
                .as("the fingerprint is what ties the payload to what was reviewed")
                .isEqualTo("ANALYSIS_CONTENT_MISMATCH");
    }

    @Test
    void neverReportsAGapWhenTheResumeCarriesStructuralUncertainty() throws Exception {
        // A two-column resume keeps POSSIBLE_MULTI_COLUMN through confirmation, so absent evidence
        // must stay unknown however the provider phrased it.
        JsonNode extracted = objectMapper.readTree(mockMvc.perform(multipart("/api/v1/resumes/extract")
                        .file(new MockMultipartFile("file", "resume.pdf", "application/pdf",
                                PdfFixtureFactory.twoColumnResume())))
                .andReturn().getResponse().getContentAsString());

        ObjectNode confirmRequest = objectMapper.createObjectNode();
        confirmRequest.put("rawText", extracted.get("rawText").asString());
        confirmRequest.set("candidateProfile", extracted.get("candidateProfile"));
        confirmRequest.set("carriedWarnings", extracted.get("extractionWarnings"));
        confirmRequest.put("confirmed", true);

        JsonNode confirmed = objectMapper.readTree(mockMvc.perform(post("/api/v1/resumes/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmRequest)))
                .andReturn().getResponse().getContentAsString());
        assertThat(confirmed.get("evidenceAbsencePolicy").asString()).isEqualTo("MUST_BE_UNKNOWN");

        JsonNode response = objectMapper.readTree(
                analyse(analysisRequest(confirmed, confirmedJob()), 200));

        response.get("draft").get("requirementAssessments").forEach(assessment ->
                assertThat(assessment.get("status").asString()).isNotEqualTo("GAP"));
    }
}
