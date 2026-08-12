package com.joblens.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.joblens.api.error.ProblemDetailFactory;
import com.joblens.testsupport.TestProperties;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import tools.jackson.databind.ObjectMapper;

/**
 * The cap has to hold for a body that never declares its length, which is the case a
 * Content-Length check alone quietly misses.
 */
class RequestBodyLimitFilterTest {

    private static final long LIMIT = 1024;

    private final RequestBodyLimitFilter filter = new RequestBodyLimitFilter(
            TestProperties.withApi(new com.joblens.config.JoblensProperties.Api(LIMIT)),
            new ProblemDetailFactory(),
            new ObjectMapper());

    /** A body sent without a declared length, the way a chunked request arrives. */
    private static MockHttpServletRequest chunked(byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/analyses") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(body);
        return request;
    }

    @Test
    void stopsAnUndeclaredBodyAtTheCapWhileItIsBeingRead() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(chunked(new byte[(int) LIMIT + 1]), new MockHttpServletResponse(), chain);

        assertThatExceptionOfType(RequestBodyLimitFilter.BodyTooLargeException.class)
                .isThrownBy(() -> chain.getRequest().getInputStream().readAllBytes());
    }

    @Test
    void letsAnUndeclaredBodyUnderTheCapThrough() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(chunked(new byte[(int) LIMIT]), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest().getInputStream().readAllBytes()).hasSize((int) LIMIT);
    }

    @Test
    void answersADeclaredOversizeBodyWithoutReadingIt() throws IOException, jakarta.servlet.ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/analyses");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(new byte[(int) LIMIT + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("REQUEST_TOO_LARGE");
        assertThat(chain.getRequest()).as("the body must never reach the application").isNull();
    }

    @Test
    void leavesNonJsonRequestsAlone() throws IOException, jakarta.servlet.ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/resumes/extract");
        request.setContentType(MediaType.MULTIPART_FORM_DATA_VALUE);
        request.setContent(new byte[(int) LIMIT * 4]);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }
}
