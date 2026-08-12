package com.joblens.jobposting.render;

import static org.assertj.core.api.Assertions.assertThat;

import com.joblens.config.JoblensProperties;
import com.joblens.jobposting.JobPostingExtractionResult;
import com.joblens.jobposting.JobPostingExtractionService;
import com.joblens.testsupport.JobPostingServices;
import com.joblens.testsupport.LocalTestServer;
import com.joblens.testsupport.TestProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The rendering fallback against a real Chromium.
 *
 * <p>Tagged so it stays out of {@code ./gradlew test}: it downloads a browser on first run and is
 * an order of magnitude slower than everything else. Run it with {@code ./gradlew browserTest}.
 */
@Tag("browser")
class PlaywrightRenderingTest {

    private static final String HTML = "text/html; charset=utf-8";

    /** A page with nothing in its markup, exactly like the single-page boards. */
    private static final String CLIENT_RENDERED = """
            <html><head><title>Acme Careers</title></head>
            <body><div id="root"></div>
            <script>
              document.addEventListener('DOMContentLoaded', function () {
                document.getElementById('root').innerHTML =
                  '<main><h1>Senior Backend Engineer</h1>'
                  + '<p>Acme Corp — Toronto, ON</p>'
                  + '<h2>Responsibilities</h2><ul>'
                  + '<li>Design and build backend services in Java and Spring Boot</li>'
                  + '<li>Own features from requirements through release and production support</li></ul>'
                  + '<h2>Required Qualifications</h2><ul>'
                  + '<li>5+ years of professional backend development experience</li>'
                  + '<li>Strong Java and Spring Boot</li>'
                  + '<li>Hands-on experience with AWS</li></ul>'
                  + '<h2>Preferred Qualifications</h2><ul>'
                  + '<li>Kafka or another event streaming platform</li></ul></main>';
              });
            </script></body></html>
            """;

    private LocalTestServer server;

    @BeforeEach
    void startServer() {
        server = LocalTestServer.start();
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    private JobPostingExtractionService serviceWithRendering(boolean enabled) {
        int port = Integer.parseInt(server.url("/").split(":")[2].replace("/", ""));
        JoblensProperties properties = TestProperties.withJobFetch(new JoblensProperties.JobFetch(
                Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(30), 512 * 1024, 3,
                List.of(80, 443, port), 4, "JobLensBot/0.1",
                new JoblensProperties.JobFetch.Browser(enabled, Duration.ofSeconds(20), 1)));
        return JobPostingServices.reachingLoopback(properties);
    }

    @Test
    void readsAPostingThatOnlyExistsAfterScriptsRun() {
        server.serve("/jobs/spa", HTML, CLIENT_RENDERED);

        JobPostingExtractionResult result = serviceWithRendering(true).extractFromUrl(server.url("/jobs/spa"));

        assertThat(result.fetchMetadata().renderedWithBrowser()).isTrue();
        assertThat(result.posting().title()).isEqualTo("Senior Backend Engineer");
        assertThat(result.posting().requiredQualifications()).hasSize(3);
        assertThat(result.posting().preferredQualifications()).hasSize(1);
        assertThat(result.posting().responsibilities()).hasSize(2);
    }

    @Test
    void stillHoldsARenderedPostingToTheReviewContract() {
        server.serve("/jobs/spa", HTML, CLIENT_RENDERED);

        JobPostingExtractionResult result = serviceWithRendering(true).extractFromUrl(server.url("/jobs/spa"));

        assertThat(result.reviewStatus())
                .isEqualTo(com.joblens.document.ReviewStatus.REVIEW_REQUIRED);
        assertThat(result.requirementSourcePolicy())
                .isEqualTo(com.joblens.jobposting.RequirementSourcePolicy.FULL_TEXT_FALLBACK);
    }

    @Test
    void handlesSeveralRendersInSequenceWithoutLeakingContexts() {
        server.serve("/jobs/spa", HTML, CLIENT_RENDERED);
        JobPostingExtractionService service = serviceWithRendering(true);

        for (int attempt = 0; attempt < 3; attempt++) {
            assertThat(service.extractFromUrl(server.url("/jobs/spa")).posting().requiredQualifications())
                    .hasSize(3);
        }
    }
}
