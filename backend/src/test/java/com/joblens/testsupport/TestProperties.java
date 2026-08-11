package com.joblens.testsupport;

import com.joblens.config.JoblensProperties;
import java.time.Duration;
import java.util.List;

/** Configuration for unit tests, mirroring the defaults in {@code application.yml}. */
public final class TestProperties {

    public static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;
    public static final int MAX_PAGE_COUNT = 15;
    public static final int MIN_USABLE_CHARACTERS = 400;
    public static final int MAX_EXTRACTED_CHARACTERS = 500_000;

    private TestProperties() {}

    public static JoblensProperties defaults() {
        return withResume(new JoblensProperties.Resume(
                MAX_FILE_SIZE_BYTES, MAX_PAGE_COUNT, MIN_USABLE_CHARACTERS, MAX_EXTRACTED_CHARACTERS));
    }

    public static final int MIN_JOB_TEXT_CHARACTERS = 200;
    public static final int MAX_JOB_TEXT_CHARACTERS = 40_000;

    public static JoblensProperties withResume(JoblensProperties.Resume resume) {
        return build(resume, defaultJobPosting(), defaultJobFetch());
    }

    public static JoblensProperties withJobPosting(JoblensProperties.JobPosting jobPosting) {
        return build(defaultResume(), jobPosting, defaultJobFetch());
    }

    public static JoblensProperties withJobFetch(JoblensProperties.JobFetch jobFetch) {
        return build(defaultResume(), defaultJobPosting(), jobFetch);
    }

    public static JoblensProperties.JobFetch defaultJobFetch() {
        return new JoblensProperties.JobFetch(
                Duration.ofSeconds(2),
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                2L * 1024 * 1024,
                3,
                List.of(80, 443),
                4,
                "JobLensBot/0.1 (+https://joblens.local/bot)");
    }

    public static JoblensProperties.Resume defaultResume() {
        return new JoblensProperties.Resume(
                MAX_FILE_SIZE_BYTES, MAX_PAGE_COUNT, MIN_USABLE_CHARACTERS, MAX_EXTRACTED_CHARACTERS);
    }

    public static JoblensProperties.JobPosting defaultJobPosting() {
        return new JoblensProperties.JobPosting(MIN_JOB_TEXT_CHARACTERS, MAX_JOB_TEXT_CHARACTERS);
    }

    private static JoblensProperties build(JoblensProperties.Resume resume,
            JoblensProperties.JobPosting jobPosting, JoblensProperties.JobFetch jobFetch) {
        return new JoblensProperties(
                resume,
                jobPosting,
                jobFetch,
                new JoblensProperties.Cors(List.of("http://localhost:5173")),
                new JoblensProperties.Analysis("fake"));
    }
}
