package com.joblens.resume;

import com.joblens.document.ExtractionWarning;
import com.joblens.resume.model.CandidateProfile;
import java.util.List;

/**
 * Everything the review step needs about one uploaded resume.
 *
 * <p>Nothing here is stored. {@code extractionId} correlates log lines for one request and has no
 * meaning afterwards, which is why the client, not the server, holds the document between steps.
 */
public record ResumeExtractionResult(
        String extractionId,
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
