package com.joblens.resume;

import com.joblens.document.ExtractionWarning;
import com.joblens.resume.model.CandidateProfile;
import java.util.List;

/**
 * Everything the review step needs about one uploaded resume.
 *
 * <p>Nothing here is stored. {@code extractionId} correlates log lines for one request and has no
 * meaning afterwards, which is why the client, not the server, holds the document between steps.
 *
 * <p>{@code rawText} is always present, whatever happened to the structured parse. The structure is
 * a heuristic reading of the text and can be wrong or incomplete; the text is what was actually in
 * the document, and it is the fallback the user reviews against.
 *
 * <p>{@code reviewStatus} is always {@link ResumeReviewStatus#REVIEW_REQUIRED}. Extraction cannot
 * produce a confirmed resume.
 */
public record ResumeExtractionResult(
        String extractionId,
        ResumeReviewStatus reviewStatus,
        EvidenceAbsencePolicy evidenceAbsencePolicy,
        String rawText,
        List<ExtractedResumeText.PageInfo> pages,
        CandidateProfile profile,
        List<ExtractionWarning> warnings,
        long extractionMs) {

    public ResumeExtractionResult {
        pages = List.copyOf(pages);
        warnings = List.copyOf(warnings);
    }
}
