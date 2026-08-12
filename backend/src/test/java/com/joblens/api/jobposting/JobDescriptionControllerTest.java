package com.joblens.api.jobposting;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.joblens.testsupport.JobPostingFixtures;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
@AutoConfigureMockMvc
class JobDescriptionControllerTest {

    private static final String EXTRACT = "/api/v1/job-descriptions/extract";
    private static final String CONFIRM = "/api/v1/job-descriptions/confirm";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private ResultActions extract(String body) throws Exception {
        return mockMvc.perform(post(EXTRACT).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String extractRequestFor(String pastedText) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("text", pastedText);
        return objectMapper.writeValueAsString(request);
    }

    @Nested
    class SourceSelection {

        @Test
        void acceptsPastedText() throws Exception {
            extract(extractRequestFor(JobPostingFixtures.WELL_STRUCTURED))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.schemaVersion").value("job-extraction/v1"))
                    .andExpect(jsonPath("$.sourceType").value("TEXT"))
                    .andExpect(jsonPath("$.jobPosting.title").value("Senior Backend Engineer"))
                    .andExpect(jsonPath("$.jobPosting.requiredQualifications.length()").value(4))
                    .andExpect(jsonPath("$.jobPosting.preferredQualifications.length()").value(2))
                    .andExpect(jsonPath("$.stats.requiredCount").value(4));
        }

        @Test
        void rejectsBothSourcesAtOnce() throws Exception {
            ObjectNode request = objectMapper.createObjectNode();
            request.put("url", "https://example.com/jobs/1");
            request.put("text", JobPostingFixtures.WELL_STRUCTURED);

            extract(objectMapper.writeValueAsString(request))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fieldErrors[0].message")
                            .value(org.hamcrest.Matchers.containsString("not both")));
        }

        @Test
        void rejectsNeitherSource() throws Exception {
            extract("{}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }


        @Test
        void rejectsTextTooShortToDescribeARole() throws Exception {
            extract(extractRequestFor("Backend engineer wanted."))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("JD_TEXT_TOO_SHORT"));
        }
    }

    @Nested
    class ReviewAndConfirmation {

        @Test
        void marksEveryExtractionAsRequiringReview() throws Exception {
            extract(extractRequestFor(JobPostingFixtures.WELL_STRUCTURED))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reviewStatus").value("REVIEW_REQUIRED"))
                    .andExpect(jsonPath("$.requirementSourcePolicy").value("FULL_TEXT_FALLBACK"));
        }

        @Test
        void returnsRawTextAndWarningsWhenNothingCouldBeStructured() throws Exception {
            extract(extractRequestFor(JobPostingFixtures.PLAIN_PROSE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rawText")
                            .value(org.hamcrest.Matchers.containsString("full-stack software engineer")))
                    .andExpect(jsonPath("$.jobPosting.requiredQualifications.length()").value(0))
                    .andExpect(jsonPath("$.extractionWarnings[*].code").value(org.hamcrest.Matchers.hasItems(
                            "NO_POSTING_SECTIONS_DETECTED", "NO_QUALIFICATION_SECTIONS_DETECTED")));
        }

        @Test
        void extractedContentCanBeCorrectedAndConfirmed() throws Exception {
            JsonNode extracted = objectMapper.readTree(
                    extract(extractRequestFor(JobPostingFixtures.WELL_STRUCTURED))
                            .andReturn().getResponse().getContentAsString());

            mockMvc.perform(post(CONFIRM)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(confirmationFor(extracted))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.schemaVersion").value("job-confirmation/v1"))
                    .andExpect(jsonPath("$.reviewStatus").value("CONFIRMED"))
                    .andExpect(jsonPath("$.contentFingerprint").isNotEmpty())
                    .andExpect(jsonPath("$.requirementSourcePolicy").value("STRUCTURED_SECTIONS"))
                    .andExpect(jsonPath("$.jobPosting.requiredQualifications.length()").value(4));
        }

        @Test
        void confirmationWithoutAnExplicitYesIsRejected() throws Exception {
            JsonNode extracted = objectMapper.readTree(
                    extract(extractRequestFor(JobPostingFixtures.WELL_STRUCTURED))
                            .andReturn().getResponse().getContentAsString());
            ObjectNode request = confirmationFor(extracted);
            request.put("confirmed", false);

            mockMvc.perform(post(CONFIRM)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("confirmed"));
        }

        @Test
        void anAmbiguousPostingStaysUntrustedEvenAfterConfirmation() throws Exception {
            JsonNode extracted = objectMapper.readTree(
                    extract(extractRequestFor(JobPostingFixtures.AMBIGUOUS_QUALIFICATIONS))
                            .andReturn().getResponse().getContentAsString());

            mockMvc.perform(post(CONFIRM)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(confirmationFor(extracted))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reviewStatus").value("CONFIRMED"))
                    .andExpect(jsonPath("$.requirementSourcePolicy").value("FULL_TEXT_FALLBACK"));
        }

        private ObjectNode confirmationFor(JsonNode extracted) {
            ObjectNode request = objectMapper.createObjectNode();
            request.put("extractionId", extracted.get("extractionId").asString());
            request.put("rawText", extracted.get("rawText").asString());
            request.set("jobPosting", extracted.get("jobPosting"));
            request.set("carriedWarnings", extracted.get("extractionWarnings"));
            request.put("confirmed", true);
            return request;
        }
    }
}
