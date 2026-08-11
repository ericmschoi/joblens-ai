package com.joblens.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
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
        @Valid Resume resume, @Valid JobPosting jobPosting, @Valid Cors cors, @Valid Analysis analysis) {

    public record Resume(
            @Positive long maxFileSizeBytes,
            @Positive int maxPageCount,
            @Positive int minUsableCharacters,
            @Positive int maxExtractedCharacters) {}

    public record JobPosting(
            @Positive int minTextCharacters,
            @Positive int maxTextCharacters) {}

    public record Cors(@NotEmpty List<String> allowedOrigins) {}

    public record Analysis(@NotEmpty String provider) {}
}
