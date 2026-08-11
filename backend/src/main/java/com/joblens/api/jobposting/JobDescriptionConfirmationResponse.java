package com.joblens.api.jobposting;

import com.joblens.document.ExtractionWarning;
import com.joblens.document.ReviewStatus;
import com.joblens.jobposting.ConfirmedJobPosting;
import com.joblens.jobposting.RequirementSourcePolicy;
import com.joblens.jobposting.model.JobPosting;
import java.time.Instant;
import java.util.List;

/** The confirmed job posting the client holds and later submits for analysis. */
public record JobDescriptionConfirmationResponse(
        String schemaVersion,
        ReviewStatus reviewStatus,
        Instant confirmedAt,
        String contentFingerprint,
        RequirementSourcePolicy requirementSourcePolicy,
        String rawText,
        JobPosting jobPosting,
        List<ExtractionWarning> extractionWarnings) {

    public static final String SCHEMA_VERSION = "job-confirmation/v1";

    public static JobDescriptionConfirmationResponse from(ConfirmedJobPosting confirmed) {
        return new JobDescriptionConfirmationResponse(
                SCHEMA_VERSION,
                confirmed.reviewStatus(),
                confirmed.confirmedAt(),
                confirmed.contentFingerprint(),
                confirmed.requirementSourcePolicy(),
                confirmed.rawText(),
                confirmed.posting(),
                confirmed.warnings());
    }
}
