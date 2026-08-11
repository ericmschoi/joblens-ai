package com.joblens.api.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.joblens.testsupport.PdfFixtureFactory;
import org.junit.jupiter.api.Nested;
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

@SpringBootTest
@AutoConfigureMockMvc
class ResumeControllerTest {

    private static final String ENDPOINT = "/api/v1/resumes/extract";
    private static final String CONFIRM_ENDPOINT = "/api/v1/resumes/confirm";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static MockMultipartFile upload(byte[] content) {
        return new MockMultipartFile("file", "resume.pdf", "application/pdf", content);
    }

    @Test
    void returnsTheExtractedTextAndTheStructuredProfile() throws Exception {
        mockMvc.perform(multipart(ENDPOINT).file(upload(PdfFixtureFactory.oneColumnResume())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value("resume-extraction/v1"))
                .andExpect(jsonPath("$.extractionId").isNotEmpty())
                .andExpect(jsonPath("$.rawText").value(org.hamcrest.Matchers.containsString("Northwind Systems")))
                .andExpect(jsonPath("$.candidateProfile.workExperiences.length()").value(2))
                .andExpect(jsonPath("$.candidateProfile.workExperiences[0].company").value("Northwind Systems"))
                .andExpect(jsonPath("$.stats.pageCount").value(1))
                .andExpect(jsonPath("$.stats.charCount").isNumber());
    }

    @Test
    void reportsExtractionWarningsSoTheUserCanCheckThemBeforeAnalysing() throws Exception {
        mockMvc.perform(multipart(ENDPOINT).file(upload(PdfFixtureFactory.twoColumnResume())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extractionWarnings[*].code")
                        .value(org.hamcrest.Matchers.hasItem("POSSIBLE_MULTI_COLUMN")))
                .andExpect(jsonPath("$.extractionWarnings[0].message").isNotEmpty());
    }

    @Test
    void rejectsAFileThatIsNotAPdf() throws Exception {
        mockMvc.perform(multipart(ENDPOINT).file(upload(PdfFixtureFactory.notAPdf())))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("FILE_TYPE_NOT_SUPPORTED"))
                .andExpect(jsonPath("$.recoveryAction").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void rejectsAPasswordProtectedPdf() throws Exception {
        mockMvc.perform(multipart(ENDPOINT).file(upload(PdfFixtureFactory.passwordProtected())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PDF_ENCRYPTED"));
    }

    @Test
    void explainsThatScannedPdfsAreNotSupportedYet() throws Exception {
        mockMvc.perform(multipart(ENDPOINT).file(upload(PdfFixtureFactory.imageOnly())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PDF_IMAGE_ONLY"))
                .andExpect(jsonPath("$.recoveryAction").value(org.hamcrest.Matchers.containsString("OCR")));
    }

    @Test
    void rejectsACorruptPdf() throws Exception {
        mockMvc.perform(multipart(ENDPOINT).file(upload(PdfFixtureFactory.corruptPdf())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PDF_CORRUPT"));
    }

    @Test
    void rejectsAnEmptyUpload() throws Exception {
        mockMvc.perform(multipart(ENDPOINT).file(upload(new byte[0])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_MISSING"));
    }

    @Test
    void rejectsARequestWithNoFilePart() throws Exception {
        mockMvc.perform(multipart(ENDPOINT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_MISSING"));
    }

    @Test
    void marksEveryExtractionAsRequiringReview() throws Exception {
        mockMvc.perform(multipart(ENDPOINT).file(upload(PdfFixtureFactory.oneColumnResume())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.evidenceAbsencePolicy").value("MUST_BE_UNKNOWN"));
    }

    @Test
    void returnsTheRawTextEvenWhenNothingCouldBeStructured() throws Exception {
        mockMvc.perform(multipart(ENDPOINT).file(upload(PdfFixtureFactory.unstructuredProse())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rawText").value(org.hamcrest.Matchers.containsString("Lakeshore Digital")))
                .andExpect(jsonPath("$.candidateProfile.workExperiences.length()").value(0))
                .andExpect(jsonPath("$.extractionWarnings[*].code")
                        .value(org.hamcrest.Matchers.hasItems("NO_SECTIONS_DETECTED", "NO_ROLES_DETECTED")));
    }

    @Nested
    class ReviewAndConfirmation {

        @Test
        void extractedContentCanBeCorrectedAndConfirmed() throws Exception {
            JsonNode extracted = extract(PdfFixtureFactory.oneColumnResume());
            assertThat(extracted.get("reviewStatus").asString()).isEqualTo("REVIEW_REQUIRED");

            ObjectNode request = confirmationFor(extracted);
            request.put("rawText", extracted.get("rawText").asString() + "\nAdded during review: Kubernetes.");

            mockMvc.perform(post(CONFIRM_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.schemaVersion").value("resume-confirmation/v1"))
                    .andExpect(jsonPath("$.reviewStatus").value("CONFIRMED"))
                    .andExpect(jsonPath("$.confirmedAt").isNotEmpty())
                    .andExpect(jsonPath("$.contentFingerprint").isNotEmpty())
                    .andExpect(jsonPath("$.evidenceAbsencePolicy").value("MAY_BE_GAP"))
                    .andExpect(jsonPath("$.rawText")
                            .value(org.hamcrest.Matchers.containsString("Added during review: Kubernetes.")))
                    .andExpect(jsonPath("$.candidateProfile.workExperiences.length()").value(2));
        }

        @Test
        void confirmationWithoutAnExplicitYesIsRejected() throws Exception {
            ObjectNode request = confirmationFor(extract(PdfFixtureFactory.oneColumnResume()));
            request.put("confirmed", false);

            mockMvc.perform(post(CONFIRM_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("confirmed"));
        }

        @Test
        void anUncertainParseStaysUncertainEvenAfterConfirmation() throws Exception {
            JsonNode extracted = extract(PdfFixtureFactory.twoColumnResume());

            mockMvc.perform(post(CONFIRM_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(confirmationFor(extracted))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reviewStatus").value("CONFIRMED"))
                    .andExpect(jsonPath("$.evidenceAbsencePolicy")
                            .value("MUST_BE_UNKNOWN"));
        }

        private JsonNode extract(byte[] pdf) throws Exception {
            String json = mockMvc.perform(multipart(ENDPOINT).file(upload(pdf)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            return objectMapper.readTree(json);
        }

        private ObjectNode confirmationFor(JsonNode extracted) {
            ObjectNode request = objectMapper.createObjectNode();
            request.put("extractionId", extracted.get("extractionId").asString());
            request.put("rawText", extracted.get("rawText").asString());
            request.set("candidateProfile", extracted.get("candidateProfile"));
            request.set("carriedWarnings", extracted.get("extractionWarnings"));
            request.put("confirmed", true);
            return request;
        }
    }

    @Test
    void neverReturnsInternalDetailInAnErrorBody() throws Exception {
        String body = mockMvc.perform(multipart(ENDPOINT).file(upload(PdfFixtureFactory.corruptPdf())))
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("org.apache.pdfbox")
                .doesNotContain("Exception")
                .doesNotContain("com.joblens");
    }
}
