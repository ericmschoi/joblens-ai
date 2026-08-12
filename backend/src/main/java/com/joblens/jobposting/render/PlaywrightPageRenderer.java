package com.joblens.jobposting.render;

import com.joblens.config.JoblensProperties;
import com.joblens.error.ApiException;
import com.joblens.error.ErrorCode;
import com.joblens.jobposting.fetch.BlockedAddressPolicy;
import com.joblens.jobposting.fetch.HostResolver;
import com.joblens.jobposting.fetch.SafeUrlValidator;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Renders a page in Chromium, for postings whose content only exists after JavaScript runs.
 *
 * <p>Off by default. It is called for exactly one situation — a page assessed as
 * {@code JAVASCRIPT_REQUIRED} — and never to get past a bot check, a sign-in wall, a 403 or a 429.
 * Those are refusals, and the answer to a refusal is to ask the user to paste.
 *
 * <p>Every render gets a fresh browser context, so nothing carries between requests: no cookies, no
 * storage, no credentials. No authorization header is ever set. Requests the page makes are checked
 * against the same address policy as the plain fetcher, so a page cannot use the browser to reach
 * somewhere the HTTP client would have refused.
 *
 * <p>This is prototype-level containment, not a sandbox. It does not police Chromium's own
 * resolution path, and full egress isolation is a hardening task, not a precondition for the feature.
 */
@Component
public class PlaywrightPageRenderer {

    private static final Logger LOG = LoggerFactory.getLogger(PlaywrightPageRenderer.class);

    /** Nothing here contributes text, and skipping it makes rendering considerably faster. */
    private static final List<String> SKIPPED_RESOURCE_TYPES =
            List.of("image", "media", "font", "stylesheet");

    private final JoblensProperties.JobFetch.Browser config;
    private final String userAgent;
    private final BlockedAddressPolicy blockedAddresses;
    private final HostResolver hostResolver;
    private final Semaphore renders;
    private final Map<String, Boolean> hostDecisions = new ConcurrentHashMap<>();

    private Playwright playwright;
    private Browser browser;

    public PlaywrightPageRenderer(JoblensProperties properties, BlockedAddressPolicy blockedAddresses,
            HostResolver hostResolver) {
        this.config = properties.jobFetch().browser();
        this.userAgent = properties.jobFetch().userAgent();
        this.blockedAddresses = blockedAddresses;
        this.hostResolver = hostResolver;
        this.renders = new Semaphore(config.maxConcurrentRenders());
    }

    public boolean isEnabled() {
        return config.enabled();
    }

    /** @param target a URL that has already passed {@link SafeUrlValidator} */
    public String render(SafeUrlValidator.ValidatedUrl target) {
        acquireSlot();
        BrowserContext context = null;
        Page page = null;
        try {
            context = browser().newContext(new Browser.NewContextOptions()
                    .setUserAgent(userAgent)
                    .setJavaScriptEnabled(true)
                    .setAcceptDownloads(false)
                    .setBypassCSP(false));
            context.setDefaultTimeout(config.timeout().toMillis());
            guardRequests(context, target.uri());

            page = context.newPage();
            page.navigate(target.uri().toString(), new Page.NavigateOptions()
                    .setTimeout(config.timeout().toMillis())
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
            settle(page);
            return page.content();
        } catch (PlaywrightException e) {
            LOG.warn("code={} stage=render cause={}", ErrorCode.URL_TIMEOUT, e.getClass().getSimpleName());
            throw new ApiException(ErrorCode.URL_TIMEOUT, "That page took too long to load.", e);
        } finally {
            closeQuietly(page, context);
            renders.release();
        }
    }

    /** Network idle is a hint, not a promise. A page that never settles is still worth reading. */
    private void settle(Page page) {
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(config.timeout().toMillis() / 2.0));
        } catch (PlaywrightException e) {
            LOG.debug("page never reached network idle, reading it as it stands");
        }
    }

    /**
     * Applies the address policy to everything the page asks for, and drops resources that cannot
     * contribute text.
     */
    private void guardRequests(BrowserContext context, URI target) {
        context.route("**", route -> {
            String type = route.request().resourceType();
            if (SKIPPED_RESOURCE_TYPES.contains(type)) {
                route.abort();
                return;
            }
            if (!isHostAllowed(route.request().url(), target)) {
                route.abort();
                return;
            }
            route.resume();
        });
    }

    private boolean isHostAllowed(String requestUrl, URI target) {
        try {
            URI uri = URI.create(requestUrl);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                return false;
            }
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            if (host.equalsIgnoreCase(target.getHost())) {
                return true;
            }
            return hostDecisions.computeIfAbsent(host.toLowerCase(Locale.ROOT), this::resolvesToAnAllowedAddress);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean resolvesToAnAllowedAddress(String host) {
        try {
            InetAddress[] addresses = hostResolver.resolve(host);
            return addresses.length > 0
                    && java.util.Arrays.stream(addresses).noneMatch(blockedAddresses::isBlocked);
        } catch (Exception e) {
            return false;
        }
    }

    private void acquireSlot() {
        try {
            if (!renders.tryAcquire(2, TimeUnit.SECONDS)) {
                throw new ApiException(ErrorCode.RATE_LIMITED,
                        "JobLens is loading too many pages right now.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(ErrorCode.URL_FETCH_FAILED, "That page could not be loaded.", e);
        }
    }

    /** Chromium starts on first use, so a deployment with rendering off never launches one. */
    private synchronized Browser browser() {
        if (browser == null || !browser.isConnected()) {
            playwright = playwright == null ? Playwright.create() : playwright;
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        }
        return browser;
    }

    private static void closeQuietly(Page page, BrowserContext context) {
        try {
            if (page != null) {
                page.close();
            }
        } catch (PlaywrightException e) {
            LOG.debug("page close failed");
        }
        try {
            if (context != null) {
                context.close();
            }
        } catch (PlaywrightException e) {
            LOG.debug("context close failed");
        }
    }

    @PreDestroy
    synchronized void shutdown() {
        try {
            if (browser != null) {
                browser.close();
            }
            if (playwright != null) {
                playwright.close();
            }
        } catch (PlaywrightException e) {
            LOG.debug("browser shutdown failed");
        } finally {
            browser = null;
            playwright = null;
        }
    }
}
