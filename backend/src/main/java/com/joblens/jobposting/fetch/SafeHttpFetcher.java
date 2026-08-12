package com.joblens.jobposting.fetch;

import com.joblens.config.JoblensProperties;
import com.joblens.error.ApiException;
import com.joblens.error.ErrorCode;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fetches a public page under every containment limit the product commits to.
 *
 * <p>Redirects are followed by hand rather than by the client, because each hop is a fresh URL from
 * an untrusted source and has to pass the same validation as the first one. A client that follows
 * redirects for you will happily walk from a public page to {@code http://169.254.169.254/}.
 *
 * <p>Nothing about the caller travels outbound: no cookies, no authorization header, no session
 * state. The response is size-capped while it streams, so an endless body cannot exhaust memory
 * before the limit is noticed.
 */
@Component
public class SafeHttpFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(SafeHttpFetcher.class);

    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
            "text/html", "application/xhtml+xml", "text/plain", "application/json", "application/ld+json");

    /**
     * HTML only. Offering JSON and plain text as alternatives made content-negotiating boards
     * answer with {@code application/xml}, which is not a page and gets rejected downstream.
     */
    private static final String ACCEPT = "text/html,application/xhtml+xml;q=0.9";

    private final JoblensProperties.JobFetch limits;
    private final SafeUrlValidator urlValidator;
    private final CloseableHttpClient httpClient;
    private final PoolingHttpClientConnectionManager connectionManager;
    private final Semaphore concurrentFetches;

    public SafeHttpFetcher(JoblensProperties properties, SafeUrlValidator urlValidator,
            BlockedAddressPolicy blockedAddresses, HostResolver hostResolver) {
        this.limits = properties.jobFetch();
        this.urlValidator = urlValidator;
        this.concurrentFetches = new Semaphore(limits.maxConcurrentFetches());

        this.connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(new ValidatingDnsResolver(blockedAddresses, hostResolver))
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.of(limits.connectTimeout()))
                        .setSocketTimeout(Timeout.of(limits.responseTimeout()))
                        .build())
                .setMaxConnTotal(limits.maxConcurrentFetches() * 2)
                .setMaxConnPerRoute(limits.maxConcurrentFetches())
                .build();

        this.httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .disableRedirectHandling()
                .disableCookieManagement()
                .disableAuthCaching()
                .disableAutomaticRetries()
                .setUserAgent(limits.userAgent())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setResponseTimeout(Timeout.of(limits.responseTimeout()))
                        .setConnectionRequestTimeout(Timeout.of(limits.connectTimeout()))
                        .build())
                .build();
    }

    @PreDestroy
    void shutdown() {
        try {
            httpClient.close();
        } catch (IOException e) {
            LOG.debug("http client close failed during shutdown");
        }
        connectionManager.close();
    }

    /** @param validated a URL that has already passed {@link SafeUrlValidator} */
    public FetchedPage fetch(SafeUrlValidator.ValidatedUrl validated) {
        acquireSlot();
        long startedAt = System.nanoTime();
        try {
            return followRedirects(validated, limits.maxResponseBytes(), startedAt);
        } finally {
            concurrentFetches.release();
        }
    }

    private void acquireSlot() {
        try {
            if (!concurrentFetches.tryAcquire(1, TimeUnit.SECONDS)) {
                throw new ApiException(ErrorCode.RATE_LIMITED,
                        "JobLens is fetching too many pages right now.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(ErrorCode.URL_FETCH_FAILED, "That page could not be loaded.", e);
        }
    }

    private FetchedPage followRedirects(SafeUrlValidator.ValidatedUrl validated, long maxBytes, long startedAt) {
        SafeUrlValidator.ValidatedUrl current = validated;
        int redirects = 0;

        while (true) {
            requireTimeRemaining(startedAt);
            Attempt attempt = attempt(current, maxBytes, startedAt);

            if (attempt.redirectTo() == null) {
                return new FetchedPage(current.uri().toString(), attempt.statusCode(), attempt.contentType(),
                        attempt.body(), redirects, elapsedMs(startedAt));
            }

            if (++redirects > limits.maxRedirects()) {
                throw new ApiException(ErrorCode.URL_TOO_MANY_REDIRECTS, "That link redirected too many times.");
            }
            // Every hop is a new untrusted URL and gets the full check, not a relaxed one.
            current = urlValidator.validate(current.uri().resolve(attempt.redirectTo()).toString());
        }
    }

    private record Attempt(int statusCode, String contentType, String body, String redirectTo) {}

    private Attempt attempt(SafeUrlValidator.ValidatedUrl target, long maxBytes, long startedAt) {
        HttpGet request = new HttpGet(target.uri());
        request.setHeader("Accept", ACCEPT);
        request.setHeader("Accept-Language", "en");

        try {
            return httpClient.execute(request, response -> handle(response, maxBytes, startedAt));
        } catch (SocketTimeoutException e) {
            // ConnectTimeoutException extends SocketTimeoutException, so both arrive here.
            throw new ApiException(ErrorCode.URL_TIMEOUT, "That page took too long to respond.", e);
        } catch (ApiException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // The client's message can name internal hosts and addresses, so only its type is logged.
            LOG.warn("code={} cause={}", ErrorCode.URL_FETCH_FAILED, e.getClass().getSimpleName());
            throw new ApiException(ErrorCode.URL_FETCH_FAILED, "That page could not be loaded.", e);
        }
    }

    private Attempt handle(ClassicHttpResponse response, long maxBytes, long startedAt) throws IOException {
        int status = response.getCode();

        if (isRedirect(status)) {
            Header location = response.getFirstHeader("Location");
            if (location == null || location.getValue() == null || location.getValue().isBlank()) {
                throw new ApiException(ErrorCode.URL_FETCH_FAILED, "That page could not be loaded.");
            }
            return new Attempt(status, null, null, location.getValue());
        }

        rejectUnusableStatus(status);

        HttpEntity entity = response.getEntity();
        if (entity == null) {
            throw new ApiException(ErrorCode.JD_EXTRACTION_INSUFFICIENT, "That page returned no content.");
        }

        String contentType = requireAllowedContentType(entity.getContentType());
        rejectDeclaredOversize(entity.getContentLength(), maxBytes);

        byte[] body = readBounded(entity, maxBytes, startedAt);
        return new Attempt(status, contentType, new String(body, charsetOf(entity.getContentType())), null);
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static void rejectUnusableStatus(int status) {
        if (status >= 200 && status < 300) {
            return;
        }
        throw switch (status) {
            case 401, 407 -> new ApiException(ErrorCode.URL_LOGIN_REQUIRED,
                    "That posting is behind a sign-in page.");
            case 403, 429 -> new ApiException(ErrorCode.URL_BLOCKED_BY_SITE,
                    "That site refused an automated request.");
            case 404, 410 -> new ApiException(ErrorCode.URL_FETCH_FAILED,
                    "That posting could not be found. It may have been taken down.");
            default -> new ApiException(ErrorCode.URL_FETCH_FAILED, "That page could not be loaded.");
        };
    }

    private static String requireAllowedContentType(String declared) {
        String mimeType = declared == null ? "" : declared.split(";")[0].strip().toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.contains(mimeType)) {
            throw new ApiException(ErrorCode.URL_CONTENT_TYPE_UNSUPPORTED,
                    "That link does not point at a readable page.");
        }
        return mimeType;
    }

    private static void rejectDeclaredOversize(long contentLength, long maxBytes) {
        if (contentLength > maxBytes) {
            throw new ApiException(ErrorCode.URL_RESPONSE_TOO_LARGE, "That page is too large to process.");
        }
    }

    /** Reads up to the cap and stops. The limit is enforced while streaming, never after buffering. */
    private byte[] readBounded(HttpEntity entity, long maxBytes, long startedAt) throws IOException {
        try (InputStream stream = entity.getContent()) {
            byte[] buffer = new byte[8192];
            java.io.ByteArrayOutputStream collected = new java.io.ByteArrayOutputStream();
            long total = 0;
            int read;
            while ((read = stream.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new ApiException(ErrorCode.URL_RESPONSE_TOO_LARGE,
                            "That page is too large to process.");
                }
                requireTimeRemaining(startedAt);
                collected.write(buffer, 0, read);
            }
            return collected.toByteArray();
        }
    }

    private void requireTimeRemaining(long startedAt) {
        if (elapsedMs(startedAt) > limits.totalTimeout().toMillis()) {
            throw new ApiException(ErrorCode.URL_TIMEOUT, "That page took too long to respond.");
        }
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private static Charset charsetOf(String contentType) {
        if (contentType == null) {
            return StandardCharsets.UTF_8;
        }
        for (String part : contentType.split(";")) {
            String token = part.strip();
            if (token.toLowerCase(Locale.ROOT).startsWith("charset=")) {
                try {
                    return Charset.forName(token.substring("charset=".length()).replace("\"", "").strip());
                } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
                    return StandardCharsets.UTF_8;
                }
            }
        }
        return StandardCharsets.UTF_8;
    }
}
