package com.joblens.jobposting.model;

import java.util.List;

/**
 * A job posting normalized into the parts an analysis needs to reason about.
 *
 * <p>The split between {@code requiredQualifications} and {@code preferredQualifications} is the
 * most consequential thing in this record. Required items are weighted far more heavily and are the
 * only ones that can ever cap a score, so a posting that blurs the two is reported rather than
 * guessed at.
 *
 * <p>{@code otherSections} keeps everything that was recognised as a section but is not one of the
 * three the analysis reasons over — benefits, equal-opportunity statements, application steps. It
 * exists so that content is visible in the structured view rather than silently dropped.
 */
public record JobPosting(
        String title,
        String company,
        String location,
        String employmentType,
        String compensationText,
        List<String> responsibilities,
        List<String> requiredQualifications,
        List<String> preferredQualifications,
        List<Section> otherSections,
        String sourceUrl) {

    public JobPosting {
        responsibilities = List.copyOf(responsibilities);
        requiredQualifications = List.copyOf(requiredQualifications);
        preferredQualifications = List.copyOf(preferredQualifications);
        otherSections = List.copyOf(otherSections);
    }

    public record Section(String heading, List<String> lines) {
        public Section {
            lines = List.copyOf(lines);
        }
    }

    public static JobPosting empty() {
        return new JobPosting(null, null, null, null, null,
                List.of(), List.of(), List.of(), List.of(), null);
    }
}
