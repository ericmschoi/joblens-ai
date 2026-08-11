package com.joblens.jobposting;

import static org.assertj.core.api.Assertions.assertThat;

import com.joblens.testsupport.JobPostingFixtures;
import org.junit.jupiter.api.Test;

class JobPostingTextNormalizerTest {

    private final JobPostingTextNormalizer normalizer = new JobPostingTextNormalizer();

    @Test
    void unifiesTheBulletGlyphsThatPastingFromAWebPageProduces() {
        String normalized = normalizer.normalize(JobPostingFixtures.MESSY_PASTE);

        assertThat(normalized.lines().filter(line -> line.startsWith("- ")))
                .hasSize(3);
        assertThat(normalized).doesNotContain("‣", "◦", "•");
    }

    @Test
    void replacesNonBreakingSpacesAndCurlyQuotes() {
        String normalized = normalizer.normalize(JobPostingFixtures.MESSY_PASTE);

        assertThat(normalized).contains("Senior Engineer", "Acme Corp", "\"event-driven\"");
        assertThat(normalized).doesNotContain(" ", "“", "”");
    }

    @Test
    void collapsesRunsOfBlankLinesWithoutJoiningContentLines() {
        String normalized = normalizer.normalize(JobPostingFixtures.MESSY_PASTE);

        assertThat(normalized).doesNotContain("\n\n\n");
        assertThat(normalized.lines().count())
                .as("line structure carries list semantics and must survive")
                .isGreaterThanOrEqualTo(7);
    }

    @Test
    void stripsTrailingSpacesAndSurroundingBlankLines() {
        String normalized = normalizer.normalize("\n\n  Backend Engineer   \n\n");

        assertThat(normalized).isEqualTo("Backend Engineer");
    }

    @Test
    void leavesAlreadyCleanTextAlone() {
        String clean = "Backend Engineer\n\nRequired Qualifications\n- Strong Java";

        assertThat(normalizer.normalize(clean)).isEqualTo(clean);
    }
}
