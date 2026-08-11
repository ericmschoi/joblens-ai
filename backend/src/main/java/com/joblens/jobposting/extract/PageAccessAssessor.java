package com.joblens.jobposting.extract;

import java.util.List;
import java.util.Locale;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

/**
 * Works out why a page yielded no usable job description.
 *
 * <p>Some sites answer an automated request with 200 and a bot check, a CAPTCHA or a sign-in page
 * rather than an error status. Treating those as "extraction failed" would be misleading: nothing
 * failed, the site declined. JobLens says so and asks the user to paste the description. It does not
 * retry, disguise its user agent, or route around the refusal — and a browser-rendering fallback
 * must never be used to get past one either.
 *
 * <p>Detection is deliberately conservative. A real posting that happens to mention CAPTCHAs or
 * logins must not be mistaken for a wall, so body phrases are only consulted when the page produced
 * too little text to be a posting in the first place. Title phrases are specific enough to stand on
 * their own.
 */
@Component
public class PageAccessAssessor {

    /** Titles that only ever belong to an interstitial, never to a job posting. */
    private static final List<String> BOT_CHECK_TITLES = List.of(
            "just a moment", "attention required", "access denied", "access to this page has been denied",
            "are you a robot", "are you a human", "verify you are human", "security check",
            "checking your browser", "captcha", "unusual traffic", "request blocked", "bot verification",
            "pardon our interruption", "human verification");

    private static final List<String> BOT_CHECK_PHRASES = List.of(
            "enable javascript and cookies to continue", "verify you are a human",
            "verifying you are human", "complete the security check", "unusual traffic from your",
            "your request has been blocked", "detected unusual activity", "please enable cookies",
            "ray id");

    private static final List<String> LOGIN_TITLES = List.of(
            "sign in", "log in", "login", "signin", "authentication required");

    private static final List<String> LOGIN_PHRASES = List.of(
            "sign in to continue", "log in to continue", "sign in to view", "log in to view",
            "please sign in", "please log in", "you must be signed in", "create an account to view");

    /** Roots a single-page application mounts into, which are empty until scripts run. */
    private static final List<String> APP_SHELL_SELECTORS = List.of(
            "#root", "#app", "#__next", "[data-reactroot]", "[ng-app]", "#ember-basic-dropdown-wormhole");

    /**
     * @param html the page as fetched
     * @param extractedText the text that came out of it
     * @param minUsableCharacters below this, the page did not yield a posting
     */
    public PageAccess assess(String html, String extractedText, int minUsableCharacters) {
        Document document = Jsoup.parse(html);
        String title = document.title() == null ? "" : document.title().toLowerCase(Locale.ROOT);
        boolean usable = extractedText.length() >= minUsableCharacters;

        if (containsAny(title, BOT_CHECK_TITLES)) {
            return PageAccess.BOT_CHECK;
        }
        if (usable) {
            // A page with a posting's worth of text is a posting, whatever words appear in it.
            return PageAccess.READABLE;
        }

        String body = document.text().toLowerCase(Locale.ROOT);
        if (containsAny(body, BOT_CHECK_PHRASES)) {
            return PageAccess.BOT_CHECK;
        }
        if (containsAny(title, LOGIN_TITLES) || containsAny(body, LOGIN_PHRASES)
                || !document.select("input[type=password]").isEmpty()) {
            return PageAccess.LOGIN_WALL;
        }
        if (looksLikeAnAppShell(document)) {
            return PageAccess.JAVASCRIPT_REQUIRED;
        }
        return PageAccess.READABLE;
    }

    /** An empty mount point, or a page that is mostly scripts, has not rendered its content yet. */
    private static boolean looksLikeAnAppShell(Document document) {
        boolean hasEmptyMountPoint = APP_SHELL_SELECTORS.stream()
                .map(document::selectFirst)
                .anyMatch(element -> element != null && element.text().isBlank());
        boolean isMostlyScripts = document.select("script").size() >= 3
                && document.body() != null && document.body().text().length() < 200;
        return hasEmptyMountPoint || isMostlyScripts;
    }

    private static boolean containsAny(String haystack, List<String> needles) {
        return needles.stream().anyMatch(haystack::contains);
    }
}
