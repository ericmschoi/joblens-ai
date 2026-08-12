package com.joblens.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * The properties of the HTTP surface that hold regardless of which endpoint is being called.
 *
 * <p>These are the ones that are easy to lose in a refactor and expensive to lose in production: a
 * response holding extracted resume text must not be cacheable, an unknown origin must not be able
 * to read the API from a browser, and a body must not be parsed before its size is known to be
 * sane.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiHardeningTest {

    @Autowired
    private MockMvc mockMvc;

    private ResultActions extractPastedPosting() throws Exception {
        return mockMvc.perform(post("/api/v1/job-descriptions/extract")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":" + quoted(JobPostingFixtures.WELL_STRUCTURED) + "}"));
    }

    private static String quoted(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    @Test
    void neverLetsADocumentResponseBeCached() throws Exception {
        extractPastedPosting()
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"));
    }

    @Test
    void carriesTheSameHardeningHeadersOnAFailure() throws Exception {
        mockMvc.perform(post("/api/v1/job-descriptions/extract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"too short\"}"))
                .andExpect(status().is4xxClientError())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    void declaresThatTheApiLoadsNothingAndFramesNothing() throws Exception {
        extractPastedPosting().andExpect(header().string("Content-Security-Policy",
                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"));
    }

    @Test
    void allowsTheConfiguredOriginToReadTheApi() throws Exception {
        mockMvc.perform(options("/api/v1/analyses")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void refusesAnOriginThatWasNotConfigured() throws Exception {
        mockMvc.perform(options("/api/v1/analyses")
                        .header("Origin", "https://joblens.example.net")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void neverOffersCredentialedCors() throws Exception {
        mockMvc.perform(options("/api/v1/analyses")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void rejectsAJsonBodyLargerThanTheCapWithoutParsingIt() throws Exception {
        String oversized = "{\"text\":\"" + "x".repeat(5 * 1024 * 1024) + "\"}";

        mockMvc.perform(post("/api/v1/job-descriptions/extract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversized))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("REQUEST_TOO_LARGE"))
                .andExpect(jsonPath("$.recoveryAction").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void stillAcceptsAnUploadThatTheJsonCapWouldHaveBlocked() throws Exception {
        // The multipart limits govern uploads; the JSON cap must not reach them.
        mockMvc.perform(multipart("/api/v1/resumes/extract")
                        .file(new MockMultipartFile("file", "resume.pdf", "application/pdf",
                                PdfFixtureFactory.oneColumnResume())))
                .andExpect(status().isOk());
    }

    @Test
    void answersAnUnknownPathWithTheSameProblemShapeAsEverythingElse() throws Exception {
        mockMvc.perform(post("/api/v1/does-not-exist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("That address does not exist."))
                .andExpect(header().string("Cache-Control", "no-store"));
    }
}
