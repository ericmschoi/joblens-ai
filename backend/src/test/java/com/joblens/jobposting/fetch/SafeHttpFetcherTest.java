package com.joblens.jobposting.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.joblens.config.JoblensProperties;
import com.joblens.error.ApiException;
import com.joblens.error.ErrorCode;
import com.joblens.testsupport.LocalTestServer;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Exercises the fetcher against real sockets on loopback.
 *
 * <p>Loopback is opened through {@link BlockedAddressPolicy}'s constructor, which is the only way it
 * can be opened. There is no property, profile or environment variable that does the same, so a
 * deployment cannot accidentally acquire this reach.
 */
class SafeHttpFetcherTest {

    private static final String HTML = "text/html; charset=utf-8";

    private LocalTestServer server;

    @BeforeEach
    void startServer() {
        server = LocalTestServer.start();
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    private int port() {
        return Integer.parseInt(server.url("/").split(":")[2].replace("/", ""));
    }

    private JoblensProperties propertiesWith(long maxBytes, Duration responseTimeout, int maxRedirects) {
        JoblensProperties.JobFetch fetch = new JoblensProperties.JobFetch(
                Duration.ofSeconds(2), responseTimeout, Duration.ofSeconds(10), maxBytes, maxRedirects,
                List.of(80, 443, port()), 4, "JobLensBot/0.1");
        return com.joblens.testsupport.TestProperties.withJobFetch(fetch);
    }

    private record Wired(SafeUrlValidator validator, SafeHttpFetcher fetcher) {}

    private Wired wire(JoblensProperties properties) {
        BlockedAddressPolicy addresses = new BlockedAddressPolicy(true);
        SafeUrlValidator validator = new SafeUrlValidator(properties, addresses, HostResolver.system());
        return new Wired(validator, new SafeHttpFetcher(properties, validator, addresses, HostResolver.system()));
    }

    private Wired wire() {
        return wire(propertiesWith(64 * 1024, Duration.ofSeconds(5), 3));
    }

    private static ErrorCode codeOf(Throwable thrown) {
        return ((ApiException) thrown).errorCode();
    }

    @Test
    void fetchesAnOrdinaryPage() {
        server.serve("/job", HTML, "<html><body><h1>Backend Engineer</h1></body></html>");
        Wired wired = wire();

        FetchedPage page = wired.fetcher().fetch(wired.validator().validate(server.url("/job")));

        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.contentType()).isEqualTo("text/html");
        assertThat(page.body()).contains("Backend Engineer");
        assertThat(page.redirectCount()).isZero();
    }

    @Test
    void refusesAResponseThatDeclaresItselfTooLarge() {
        server.serve("/big", HTML, "x".repeat(4096));
        Wired wired = wire(propertiesWith(1024, Duration.ofSeconds(5), 3));

        assertThatThrownBy(() -> wired.fetcher().fetch(wired.validator().validate(server.url("/big"))))
                .isInstanceOf(ApiException.class)
                .extracting(SafeHttpFetcherTest::codeOf)
                .isEqualTo(ErrorCode.URL_RESPONSE_TOO_LARGE);
    }

    @Test
    void stopsReadingAnUndeclaredOversizedBodyMidStream() {
        server.serveUndeclaredOversize("/endless", 512 * 1024);
        Wired wired = wire(propertiesWith(8192, Duration.ofSeconds(5), 3));

        assertThatThrownBy(() -> wired.fetcher().fetch(wired.validator().validate(server.url("/endless"))))
                .as("the cap has to hold without a Content-Length to trust")
                .isInstanceOf(ApiException.class)
                .extracting(SafeHttpFetcherTest::codeOf)
                .isEqualTo(ErrorCode.URL_RESPONSE_TOO_LARGE);
    }

    @ParameterizedTest
    @CsvSource({"application/pdf", "image/png", "application/zip", "application/octet-stream"})
    void refusesContentTypesThatCannotBeAJobPosting(String contentType) {
        server.serve("/thing", contentType, "binary-ish");
        Wired wired = wire();

        assertThatThrownBy(() -> wired.fetcher().fetch(wired.validator().validate(server.url("/thing"))))
                .isInstanceOf(ApiException.class)
                .extracting(SafeHttpFetcherTest::codeOf)
                .isEqualTo(ErrorCode.URL_CONTENT_TYPE_UNSUPPORTED);
    }

    @Test
    void followsARedirectAndReportsWhereItLanded() {
        server.redirect("/start", 302, server.url("/final"));
        server.serve("/final", HTML, "<html><body>Backend Engineer at Acme</body></html>");
        Wired wired = wire();

        FetchedPage page = wired.fetcher().fetch(wired.validator().validate(server.url("/start")));

        assertThat(page.redirectCount()).isEqualTo(1);
        assertThat(page.finalUrl()).endsWith("/final");
        assertThat(page.body()).contains("Acme");
    }

    @ParameterizedTest
    @CsvSource({
            "http://169.254.169.254/latest/meta-data/",
            "http://10.0.0.5/internal",
            "http://[fd00:ec2::254]/latest/meta-data/",
            "file:///etc/passwd"
    })
    void refusesARedirectThatPointsSomewhereItCouldNotHaveStarted(String location) {
        server.redirect("/start", 302, location);
        Wired wired = wire();

        assertThatThrownBy(() -> wired.fetcher().fetch(wired.validator().validate(server.url("/start"))))
                .as("a redirect is just another untrusted URL")
                .isInstanceOf(ApiException.class)
                .extracting(SafeHttpFetcherTest::codeOf)
                .isIn(ErrorCode.URL_BLOCKED, ErrorCode.URL_SCHEME_NOT_ALLOWED);
    }

    @Test
    void refusesARedirectLoop() {
        server.redirect("/a", 302, server.url("/b"));
        server.redirect("/b", 302, server.url("/a"));
        Wired wired = wire(propertiesWith(64 * 1024, Duration.ofSeconds(5), 3));

        assertThatThrownBy(() -> wired.fetcher().fetch(wired.validator().validate(server.url("/a"))))
                .isInstanceOf(ApiException.class)
                .extracting(SafeHttpFetcherTest::codeOf)
                .isEqualTo(ErrorCode.URL_TOO_MANY_REDIRECTS);
    }

    @ParameterizedTest
    @CsvSource({
            "401, URL_LOGIN_REQUIRED",
            "403, URL_BLOCKED_BY_SITE",
            "429, URL_BLOCKED_BY_SITE",
            "404, URL_FETCH_FAILED",
            "410, URL_FETCH_FAILED",
            "500, URL_FETCH_FAILED"
    })
    void turnsSiteRefusalsIntoSomethingTheUserCanActOn(int status, ErrorCode expected) {
        server.status("/job", status);
        Wired wired = wire();

        assertThatThrownBy(() -> wired.fetcher().fetch(wired.validator().validate(server.url("/job"))))
                .isInstanceOf(ApiException.class)
                .satisfies(thrown -> {
                    assertThat(codeOf(thrown)).isEqualTo(expected);
                    assertThat(codeOf(thrown).recoveryAction())
                            .as("every failure has to leave a way forward")
                            .containsIgnoringCase("paste");
                });
    }

    @Test
    void givesUpOnAPageThatWillNotRespondInTime() {
        server.serveAfter("/slow", HTML, "<html><body>eventually</body></html>", 1500);
        Wired wired = wire(propertiesWith(64 * 1024, Duration.ofMillis(300), 3));

        assertThatThrownBy(() -> wired.fetcher().fetch(wired.validator().validate(server.url("/slow"))))
                .isInstanceOf(ApiException.class)
                .extracting(SafeHttpFetcherTest::codeOf)
                .isEqualTo(ErrorCode.URL_TIMEOUT);
    }

    @Test
    void neverTellsTheClientWhatWentWrongInsideTheNetwork() {
        server.status("/job", 500);
        Wired wired = wire();

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> wired.fetcher().fetch(wired.validator().validate(server.url("/job"))));

        assertThat(((ApiException) thrown).detail())
                .doesNotContain("127.0.0.1")
                .doesNotContain(String.valueOf(port()));
    }
}
