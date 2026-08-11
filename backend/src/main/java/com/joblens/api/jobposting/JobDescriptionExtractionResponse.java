package com.joblens.api.jobposting;

import com.joblens.document.ExtractionWarning;
import com.joblens.document.ReviewStatus;
import com.joblens.jobposting.JobPostingExtractionResult;
import com.joblens.jobposting.RequirementSourcePolicy;
import com.joblens.jobposting.model.JobPosting;
import java.util.List;

/**
 * The response the job-posting review screen renders.
 *
 * @param reviewStatus always {@code REVIEW_REQUIRED}
 * @param requirementSourcePolicy always {@code FULL_TEXT_FALLBACK} here: an unreviewed posting's
 *        qualification lists may not be treated as the complete set of requirements
 */
public record JobDescriptionExtractionResponse(
        String schemaVersion,
        String extractionId,
        JobPostingExtractionResult.SourceType sourceType,
        ReviewStatus reviewStatus,
        RequirementSourcePolicy requirementSourcePolicy,
        String rawText,
        JobPosting jobPosting,
        List<ExtractionWarning> extractionWarnings,
        JobPostingExtractionResult.FetchMetadata fetchMetadata,
        Stats stats) {

    public static final String SCHEMA_VERSION = "job-extraction/v1";

    public record Stats(int charCount, int requiredCount, int preferredCount, int responsibilityCount,
                        long extractionMs) {}

    public static JobDescriptionExtractionResponse from(JobPostingExtractionResult result) {
        JobPosting posting = result.posting();
        return new JobDescriptionExtractionResponse(
                SCHEMA_VERSION,
                result.extractionId(),
                result.sourceType(),
                result.reviewStatus(),
                result.requirementSourcePolicy(),
                result.rawText(),
                posting,
                result.warnings(),
                result.fetchMetadata(),
                new Stats(result.rawText().length(),
                        posting.requiredQualifications().size(),
                        posting.preferredQualifications().size(),
                        posting.responsibilities().size(),
                        result.extractionMs()));
    }
}
