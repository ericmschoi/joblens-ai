package com.joblens.jobposting;

import com.joblens.document.ExtractionWarning;
import com.joblens.document.ReviewStatus;
import com.joblens.jobposting.extract.ExtractedPageContent;
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
 * @param fetchMetadata present only for a posting read from a URL
 */
public record JobPostingExtractionResult(
        String extractionId,
        SourceType sourceType,
        ReviewStatus reviewStatus,
        RequirementSourcePolicy requirementSourcePolicy,
        String rawText,
        JobPosting posting,
        List<ExtractionWarning> warnings,
        FetchMetadata fetchMetadata,
        long extractionMs) {

    public enum SourceType {
        TEXT,
        URL
    }

    /**
     * @param strategy which extractor produced the text, so an odd result is attributable
     * @param renderedWithBrowser always false today; JavaScript rendering arrives in a later phase
     */
    public record FetchMetadata(
            String finalUrl,
            int httpStatus,
            ExtractedPageContent.Strategy strategy,
            boolean renderedWithBrowser,
            int redirectCount,
            long fetchMs) {}

    public JobPostingExtractionResult {
        warnings = List.copyOf(warnings);
    }
}
