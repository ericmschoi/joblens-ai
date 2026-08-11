package com.joblens.jobposting;

import com.joblens.document.ExtractionWarning;
import com.joblens.document.ReviewStatus;
import com.joblens.jobposting.model.JobPosting;
import java.time.Instant;
import java.util.List;

/**
 * A job posting the user has reviewed and confirmed.
 *
 * <p>This, not the extraction output, is what analysis may consume. The raw text travels with it so
 * requirement decomposition can fall back to the source when the structured lists are not
 * trustworthy.
 */
public record ConfirmedJobPosting(
        ReviewStatus reviewStatus,
        Instant confirmedAt,
        String contentFingerprint,
        String rawText,
        JobPosting posting,
        List<ExtractionWarning> warnings,
        RequirementSourcePolicy requirementSourcePolicy) {

    public ConfirmedJobPosting {
        warnings = List.copyOf(warnings);
    }
}
