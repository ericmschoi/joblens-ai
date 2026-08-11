package com.joblens.resume;

import com.joblens.document.ExtractionWarning;
import java.util.List;

/**
 * The raw result of reading a PDF, before any structural interpretation.
 *
 * @param rawText the full document text in reading order, which the user reviews and may correct
 * @param pages per-page statistics used by the review UI and by quality checks
 * @param warnings quality signals the user should check before running an analysis
 */
public record ExtractedResumeText(String rawText, List<PageInfo> pages, List<ExtractionWarning> warnings) {

    public ExtractedResumeText {
        pages = List.copyOf(pages);
        warnings = List.copyOf(warnings);
    }

    /** @param hasImages whether the page draws at least one image, which distinguishes a scanned page */
    public record PageInfo(int pageNumber, int charCount, boolean hasImages) {}
}
