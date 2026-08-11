package com.joblens.api.resume;

import com.joblens.document.ExtractionWarning;
import com.joblens.resume.ExtractedResumeText;
import com.joblens.resume.ResumeExtractionResult;
import com.joblens.resume.model.CandidateProfile;
import java.util.List;

/**
 * The response the review screen renders.
 *
 * <p>Both the raw text and the structured profile are returned. The user edits either one, and the
 * version they confirm becomes the authoritative input to the analysis.
 */
public record ResumeExtractionResponse(
        String schemaVersion,
        String extractionId,
        String rawText,
        List<ExtractedResumeText.PageInfo> pages,
        CandidateProfile candidateProfile,
        List<ExtractionWarning> extractionWarnings,
        Stats stats) {

    public static final String SCHEMA_VERSION = "resume-extraction/v1";

    public record Stats(int pageCount, int charCount, long extractionMs) {}

    public static ResumeExtractionResponse from(ResumeExtractionResult result) {
        return new ResumeExtractionResponse(
                SCHEMA_VERSION,
                result.extractionId(),
                result.rawText(),
                result.pages(),
                result.profile(),
                result.warnings(),
                new Stats(result.pages().size(), result.rawText().length(), result.extractionMs()));
    }
}
