package com.joblens.api.resume;

import com.joblens.document.ExtractionWarning;
import com.joblens.document.ReviewStatus;
import com.joblens.resume.EvidenceAbsencePolicy;
import com.joblens.resume.ExtractedResumeText;
import com.joblens.resume.ResumeExtractionResult;
import com.joblens.resume.model.CandidateProfile;
import java.util.List;

/**
 * The response the review screen renders.
 *
 * <p>Both the raw text and the structured profile are returned, always. The user edits either one
 * and confirms the result through {@code POST /api/v1/resumes/confirm}; the version they confirm
 * becomes the authoritative input to the analysis.
 *
 * @param reviewStatus always {@code REVIEW_REQUIRED}. A 200 here means the file was read, not that
 *        the reading was correct.
 * @param evidenceAbsencePolicy always {@code MUST_BE_UNKNOWN} for an extraction result: nothing
 *        missing from this profile may be treated as a real gap until the user has confirmed it.
 */
public record ResumeExtractionResponse(
        String schemaVersion,
        String extractionId,
        ReviewStatus reviewStatus,
        EvidenceAbsencePolicy evidenceAbsencePolicy,
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
                result.reviewStatus(),
                result.evidenceAbsencePolicy(),
                result.rawText(),
                result.pages(),
                result.profile(),
                result.warnings(),
                new Stats(result.pages().size(), result.rawText().length(), result.extractionMs()));
    }
}
