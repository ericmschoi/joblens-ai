package com.joblens.api.error;

import com.joblens.error.ApiException;
import com.joblens.error.ErrorCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.joblens.config.JoblensProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Only the probe controller is loaded. The slice would otherwise pull in every real controller and
// require their services, which has nothing to do with what this test covers.
@WebMvcTest(controllers = GlobalExceptionHandlerTest.ProbeController.class)
@EnableConfigurationProperties(JoblensProperties.class)
@Import({ProblemDetailFactory.class, GlobalExceptionHandlerTest.ProbeController.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiExceptionIsRenderedAsAProblemDetail() throws Exception {
        mockMvc.perform(get("/test/api-exception"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://joblens.local/problems/pdf-image-only"))
                .andExpect(jsonPath("$.title").value("This PDF has no readable text"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("PDF_IMAGE_ONLY"))
                .andExpect(jsonPath("$.detail").value("This file has no extractable text."))
                .andExpect(jsonPath("$.recoveryAction").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void beanValidationFailureListsFieldErrors() throws Exception {
        mockMvc.perform(post("/test/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("title"))
                .andExpect(jsonPath("$.fieldErrors[0].message").isNotEmpty());
    }

    @Test
    void unreadableBodyIsReportedWithoutParserDetail() throws Exception {
        mockMvc.perform(post("/test/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_NOT_READABLE"))
                .andExpect(jsonPath("$.detail").value("The request body could not be read."));
    }

    @Test
    void unknownPathUsesTheSameProblemShape() throws Exception {
        mockMvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("That address does not exist."))
                .andExpect(jsonPath("$.recoveryAction").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("static resource"))));
    }

    @Test
    void wrongHttpMethodUsesTheSameProblemShape() throws Exception {
        mockMvc.perform(post("/test/boom"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unexpectedFailureDoesNotLeakInternalDetail() throws Exception {
        String body = mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("The request could not be completed."))
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("jdbc://internal-host")
                .doesNotContain("IllegalStateException")
                .doesNotContain("com.joblens.api.error.GlobalExceptionHandlerTest");
    }

    @RestController
    @RequestMapping("/test")
    static class ProbeController {

        @org.springframework.web.bind.annotation.GetMapping("/api-exception")
        void apiException() {
            throw new ApiException(ErrorCode.PDF_IMAGE_ONLY, "This file has no extractable text.");
        }

        @org.springframework.web.bind.annotation.GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("connection to jdbc://internal-host failed");
        }

        @PostMapping("/validated")
        void validated(@Valid @RequestBody Payload payload) {
            // The handler under test runs before this body is reached for invalid input.
        }

        record Payload(@NotBlank(message = "Enter a job title.") String title) {}
    }
}
