package com.joblens.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Application-owned configuration. Every operational limit and policy value that later phases
 * introduce (upload limits, fetch timeouts, scoring ceilings) belongs here rather than in prompt
 * assets or UI code, so that behaviour stays reviewable and testable.
 */
@Validated
@ConfigurationProperties(prefix = "joblens")
public record JoblensProperties(
        @Valid Api api, @Valid Resume resume, @Valid JobPosting jobPosting, @Valid JobFetch jobFetch,
        @Valid Cors cors, @Valid Analysis analysis, @Valid Scoring scoring) {

    /**
     * Bounds on the HTTP surface itself, as opposed to on any one document. A JSON body is fully
     * buffered and parsed before a controller runs, so it needs a limit of its own.
     */
    public record Api(@Positive long maxJsonRequestBytes) {}

    public record Resume(
            @Positive long maxFileSizeBytes,
            @Positive int maxPageCount,
            @Positive int minUsableCharacters,
            @Positive int maxExtractedCharacters) {}

    public record JobPosting(
            @Positive int minTextCharacters,
            @Positive int maxTextCharacters) {}

    /** Containment limits for fetching a user-supplied URL. Every field bounds a failure mode. */
    public record JobFetch(
            Duration connectTimeout,
            Duration responseTimeout,
            Duration totalTimeout,
            @Positive long maxResponseBytes,
            @Positive int maxRedirects,
            @NotEmpty List<Integer> allowedPorts,
            @Positive int maxConcurrentFetches,
            @NotEmpty String userAgent,
            @Valid Browser browser) {

        /** Rendering is opt-in. A deployment that never sets this never launches a browser. */
        public record Browser(
                boolean enabled,
                Duration timeout,
                @Positive int maxConcurrentRenders) {}
    }

    /**
     * Score ceilings for genuine core-required gaps. Configuration, never hardcoded in prompts or
     * UI code, so the policy can be reviewed and changed in one place.
     */
    public record Scoring(
            BigDecimal oneCoreGapCeiling,
            BigDecimal twoCoreGapsCeiling,
            BigDecimal threeOrMoreCoreGapsCeiling,
            BigDecimal notEligibleCeiling) {}

    public record Cors(@NotEmpty List<String> allowedOrigins) {}

    public record Analysis(@NotEmpty String provider) {}
}
