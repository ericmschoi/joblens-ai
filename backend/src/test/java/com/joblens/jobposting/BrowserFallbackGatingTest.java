package com.joblens.jobposting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.joblens.config.JoblensProperties;
import com.joblens.error.ApiException;
import com.joblens.error.ErrorCode;
import com.joblens.jobposting.fetch.BlockedAddressPolicy;
import com.joblens.jobposting.fetch.HostResolver;
import com.joblens.jobposting.fetch.SafeUrlValidator;
import com.joblens.jobposting.render.PlaywrightPageRenderer;
import com.joblens.testsupport.AtsPageFixtures;
import com.joblens.testsupport.JobPostingServices;
import com.joblens.testsupport.LocalTestServer;
import com.joblens.testsupport.TestProperties;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * When the browser may run, and when it must not.
 *
 * <p>Rendering exists for pages that need JavaScript. A bot check, a sign-in wall, a 403 or a 429 is
 * a site declining, and launching a browser at it would be working around a refusal. These tests
 * count renderer invocations rather than trusting the code to be arranged correctly.
 */
class BrowserFallbackGatingTest {

    private static final String HTML = "text/html; charset=utf-8";

    private LocalTestServer server;
    private AtomicInteger renderCount;
    private JobPostingExtractionService service;

    /** An enabled renderer that records calls instead of launching Chromium. */
    private static final class RecordingRenderer extends PlaywrightPageRenderer {

        private final AtomicInteger calls;
        private final String htmlToReturn;

        RecordingRenderer(JoblensProperties properties, BlockedAddressPolicy addresses,
                AtomicInteger calls, String htmlToReturn) {
            super(properties, addresses, HostResolver.system());
            this.calls = calls;
            this.htmlToReturn = htmlToReturn;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public String render(SafeUrlValidator.ValidatedUrl target) {
            calls.incrementAndGet();
            return htmlToReturn;
        }
    }

    private void wire(String htmlTheBrowserWouldProduce) {
        int port = Integer.parseInt(server.url("/").split(":")[2].replace("/", ""));
        JoblensProperties properties = TestProperties.withJobFetch(new JoblensProperties.JobFetch(
                Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(10), 512 * 1024, 3,
                List.of(80, 443, port), 4, "JobLensBot/0.1",
                new JoblensProperties.JobFetch.Browser(true, Duration.ofSeconds(5), 1)));

        service = JobPostingServices.reachingLoopback(properties,
                (props, addresses) -> new RecordingRenderer(props, addresses, renderCount,
                        htmlTheBrowserWouldProduce));
    }

    @BeforeEach
    void startServer() {
        server = LocalTestServer.start();
        renderCount = new AtomicInteger();
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    void rendersAPageThatOnlyBuildsItsContentInTheBrowser() {
        server.serve("/jobs/spa", HTML, AtsPageFixtures.unrenderedShell());
        wire(AtsPageFixtures.ashby());

        JobPostingExtractionResult result = service.extractFromUrl(server.url("/jobs/spa"));

        assertThat(renderCount).hasValue(1);
        assertThat(result.fetchMetadata().renderedWithBrowser()).isTrue();
        assertThat(result.posting().requiredQualifications()).hasSize(3);
    }

    @Test
    void doesNotRenderAPageThatWasAlreadyReadable() {
        server.serve("/jobs/42", HTML, AtsPageFixtures.greenhouse());
        wire(AtsPageFixtures.greenhouse());

        JobPostingExtractionResult result = service.extractFromUrl(server.url("/jobs/42"));

        assertThat(renderCount).hasValue(0);
        assertThat(result.fetchMetadata().renderedWithBrowser()).isFalse();
    }

    @Test
    void doesNotRenderABotCheck() {
        server.serve("/jobs/42", HTML, "<html><head><title>Just a moment...</title></head>"
                + "<body><p>Please enable JavaScript and cookies to continue. Ray ID: 8c1f2a</p>"
                + "</body></html>");
        wire(AtsPageFixtures.greenhouse());

        assertThatThrownBy(() -> service.extractFromUrl(server.url("/jobs/42")))
                .isInstanceOf(ApiException.class)
                .extracting(thrown -> ((ApiException) thrown).errorCode())
                .isEqualTo(ErrorCode.URL_BLOCKED_BY_SITE);
        assertThat(renderCount)
                .as("a bot check is a refusal, and a browser would be a way around it")
                .hasValue(0);
    }

    @Test
    void doesNotRenderASignInWall() {
        server.serve("/jobs/42", HTML, "<html><head><title>Sign in | Acme Jobs</title></head>"
                + "<body><form><input type=\"email\"><input type=\"password\"></form></body></html>");
        wire(AtsPageFixtures.greenhouse());

        assertThatThrownBy(() -> service.extractFromUrl(server.url("/jobs/42")))
                .isInstanceOf(ApiException.class)
                .extracting(thrown -> ((ApiException) thrown).errorCode())
                .isEqualTo(ErrorCode.URL_LOGIN_REQUIRED);
        assertThat(renderCount).hasValue(0);
    }

    @Test
    void doesNotRenderAForbiddenResponse() {
        server.status("/jobs/42", 403);
        wire(AtsPageFixtures.greenhouse());

        assertThatThrownBy(() -> service.extractFromUrl(server.url("/jobs/42")))
                .isInstanceOf(ApiException.class)
                .extracting(thrown -> ((ApiException) thrown).errorCode())
                .isEqualTo(ErrorCode.URL_BLOCKED_BY_SITE);
        assertThat(renderCount).hasValue(0);
    }

    @Test
    void doesNotRenderARateLimitedResponse() {
        server.status("/jobs/42", 429);
        wire(AtsPageFixtures.greenhouse());

        assertThatThrownBy(() -> service.extractFromUrl(server.url("/jobs/42")))
                .isInstanceOf(ApiException.class)
                .extracting(thrown -> ((ApiException) thrown).errorCode())
                .isEqualTo(ErrorCode.URL_BLOCKED_BY_SITE);
        assertThat(renderCount).hasValue(0);
    }

    @Test
    void reportsHonestlyWhenRenderingStillFindsNothing() {
        server.serve("/jobs/spa", HTML, AtsPageFixtures.unrenderedShell());
        wire(AtsPageFixtures.unrenderedShell());

        assertThatThrownBy(() -> service.extractFromUrl(server.url("/jobs/spa")))
                .isInstanceOf(ApiException.class)
                .satisfies(thrown -> {
                    ApiException failure = (ApiException) thrown;
                    assertThat(failure.errorCode()).isEqualTo(ErrorCode.JD_EXTRACTION_INSUFFICIENT);
                    assertThat(failure.detail()).contains("after it finished loading");
                });
        assertThat(renderCount).as("one attempt, not a retry loop").hasValue(1);
    }

    @Test
    void leavesTheBrowserAloneWhenRenderingIsTurnedOff() {
        server.serve("/jobs/spa", HTML, AtsPageFixtures.unrenderedShell());
        int port = Integer.parseInt(server.url("/").split(":")[2].replace("/", ""));
        JoblensProperties disabled = TestProperties.withJobFetch(new JoblensProperties.JobFetch(
                Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(10), 512 * 1024, 3,
                List.of(80, 443, port), 4, "JobLensBot/0.1",
                TestProperties.browserDisabled()));

        JobPostingExtractionService offByDefault = JobPostingServices.reachingLoopback(disabled);

        assertThatThrownBy(() -> offByDefault.extractFromUrl(server.url("/jobs/spa")))
                .isInstanceOf(ApiException.class)
                .satisfies(thrown -> assertThat(((ApiException) thrown).detail())
                        .contains("builds its content in the browser"));
    }
}
