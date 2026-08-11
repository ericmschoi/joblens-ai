package com.joblens.testsupport;

import com.joblens.config.JoblensProperties;
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

    public static JoblensProperties withResume(JoblensProperties.Resume resume) {
        return new JoblensProperties(
                resume,
                new JoblensProperties.Cors(List.of("http://localhost:5173")),
                new JoblensProperties.Analysis("fake"));
    }
}
