package com.joblens.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
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
        @Valid Resume resume, @Valid JobPosting jobPosting, @Valid JobFetch jobFetch,
        @Valid Cors cors, @Valid Analysis analysis) {

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
            @NotEmpty String userAgent) {}

    public record Cors(@NotEmpty List<String> allowedOrigins) {}

    public record Analysis(@NotEmpty String provider) {}
}
