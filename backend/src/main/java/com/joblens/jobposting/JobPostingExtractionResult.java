package com.joblens.jobposting;

import com.joblens.document.ExtractionWarning;
import com.joblens.document.ReviewStatus;
import com.joblens.jobposting.model.JobPosting;
import java.util.List;

/**
 * Everything the review step needs about one job posting.
 *
 * <p>As with a resume, {@code rawText} is always present whatever happened to the structured parse,
 * {@code reviewStatus} is always {@link ReviewStatus#REVIEW_REQUIRED}, and
 * {@code requirementSourcePolicy} tells later stages how much the structured lists can be trusted.
 *
 * @param sourceType how the posting arrived, so the review screen can label it honestly
 */
public record JobPostingExtractionResult(
        String extractionId,
        SourceType sourceType,
        ReviewStatus reviewStatus,
        RequirementSourcePolicy requirementSourcePolicy,
        String rawText,
        JobPosting posting,
        List<ExtractionWarning> warnings,
        long extractionMs) {

    public enum SourceType {
        TEXT,
        URL
    }

    public JobPostingExtractionResult {
        warnings = List.copyOf(warnings);
    }
}
