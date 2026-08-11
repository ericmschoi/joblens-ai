package com.joblens.api.jobposting;

import com.joblens.document.ExtractionWarning;
import com.joblens.jobposting.model.JobPosting;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * The reviewed job posting, submitted after the user has checked and corrected the extraction.
 *
 * @param confirmed must be {@code true}, for the same reason as on the resume side: confirmation is
 *        a deliberate act, never a side effect of a successful call
 */
public record JobDescriptionConfirmationRequest(
        String extractionId,
        @NotBlank(message = "Reviewed job description text is required.") String rawText,
        JobPosting jobPosting,
        List<ExtractionWarning> carriedWarnings,
        @AssertTrue(message = "Confirm the reviewed job description before continuing.") boolean confirmed) {}
