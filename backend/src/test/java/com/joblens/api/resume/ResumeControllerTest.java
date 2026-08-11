package com.joblens.api.resume;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.joblens.testsupport.PdfFixtureFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ResumeControllerTest {

    private static final String ENDPOINT = "/api/v1/resumes/extract";

    @Autowired
    private MockMvc mockMvc;

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
