package com.joblens.jobposting.extract;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The applicant tracking systems most postings are hosted on, and where each one keeps its content.
 *
 * <p>A dedicated reading beats the generic one because these pages wrap the description in a lot of
 * board chrome — related jobs, application forms, company blurbs — that a general-purpose extractor
 * cannot reliably tell apart from the posting itself.
 *
 * <p>Selectors are listed most specific first and are all optional. When none of them match, the
 * caller falls back to the generic path rather than returning something wrong, so a redesign
 * degrades quietly instead of breaking.
 */
public enum AtsSite {

    GREENHOUSE(
            ExtractedPageContent.Strategy.ATS_GREENHOUSE,
            List.of("boards.greenhouse.io", "job-boards.greenhouse.io", ".greenhouse.io"),
            List.of(".job__title h1", "#header .app-title", "h1.section-header", "h1"),
            List.of(".job__location div", "#header .location", ".location"),
            List.of(".job__description", "#content", ".job-post-content", "#main"),
            Pattern.compile("^/([^/?#]+)")),

    LEVER(
            ExtractedPageContent.Strategy.ATS_LEVER,
            List.of("jobs.lever.co", "jobs.eu.lever.co"),
            List.of(".posting-headline h2", "h2"),
            List.of(".posting-categories .location", ".location", ".posting-category.location"),
            List.of("div[data-qa=job-description]", ".section-wrapper.page-full-width", ".content",
                    ".posting-page"),
            Pattern.compile("^/([^/?#]+)")),

    ASHBY(
            ExtractedPageContent.Strategy.ATS_ASHBY,
            List.of("jobs.ashbyhq.com"),
            List.of("h1", "[class*=_jobPostingHeader] h1"),
            List.of("[class*=_location]", "[class*=_jobPostingHeaderSubtitle]"),
            List.of("[class*=_descriptionText]", "[class*=_jobPostingContent]", "#job-description"),
            Pattern.compile("^/([^/?#]+)")),

    WORKDAY(
            ExtractedPageContent.Strategy.ATS_WORKDAY,
            List.of(".myworkdayjobs.com", ".myworkdaysite.com"),
            List.of("[data-automation-id=jobPostingHeader]", "h1"),
            List.of("[data-automation-id=locations]", "[data-automation-id=location]"),
            List.of("[data-automation-id=jobPostingDescription]", "[data-automation-id=jobPostingPage]"),
            Pattern.compile("^/([^/?#]+)"));

    private final ExtractedPageContent.Strategy strategy;
    private final List<String> hostSuffixes;
    private final List<String> titleSelectors;
    private final List<String> locationSelectors;
    private final List<String> bodySelectors;
    private final Pattern companyInPath;

    AtsSite(ExtractedPageContent.Strategy strategy, List<String> hostSuffixes, List<String> titleSelectors,
            List<String> locationSelectors, List<String> bodySelectors, Pattern companyInPath) {
        this.strategy = strategy;
        this.hostSuffixes = hostSuffixes;
        this.titleSelectors = titleSelectors;
        this.locationSelectors = locationSelectors;
        this.bodySelectors = bodySelectors;
        this.companyInPath = companyInPath;
    }

    public static Optional<AtsSite> forUrl(URI url) {
        String host = url.getHost() == null ? "" : url.getHost().toLowerCase(Locale.ROOT);
        return java.util.Arrays.stream(values())
                .filter(site -> site.matches(host))
                .findFirst();
    }

    private boolean matches(String host) {
        return hostSuffixes.stream()
                .anyMatch(suffix -> suffix.startsWith(".") ? host.endsWith(suffix) : host.equals(suffix));
    }

    public ExtractedPageContent.Strategy strategy() {
        return strategy;
    }

    public List<String> titleSelectors() {
        return titleSelectors;
    }

    public List<String> locationSelectors() {
        return locationSelectors;
    }

    public List<String> bodySelectors() {
        return bodySelectors;
    }

    /**
     * These boards put the employer in the URL, which is more reliable than any element on the page:
     * {@code jobs.lever.co/acme/…} is Acme whatever the markup calls itself.
     */
    public Optional<String> companyFrom(URI url) {
        if (this == WORKDAY) {
            // Workday puts the employer in the subdomain: acme.wd1.myworkdayjobs.com.
            String host = url.getHost() == null ? "" : url.getHost();
            int firstDot = host.indexOf('.');
            return firstDot > 0 ? Optional.of(humanise(host.substring(0, firstDot))) : Optional.empty();
        }
        String path = url.getPath() == null ? "" : url.getPath();
        var matcher = companyInPath.matcher(path);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String slug = matcher.group(1);
        if (slug == null || slug.isBlank() || slug.equals("jobs")) {
            return Optional.empty();
        }
        return Optional.of(humanise(slug));
    }

    private static String humanise(String slug) {
        String spaced = slug.replace('-', ' ').replace('_', ' ').strip();
        return java.util.Arrays.stream(spaced.split("\\s+"))
                .map(word -> word.isEmpty() ? word
                        : Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .reduce((a, b) -> a + " " + b)
                .orElse(spaced);
    }
}
