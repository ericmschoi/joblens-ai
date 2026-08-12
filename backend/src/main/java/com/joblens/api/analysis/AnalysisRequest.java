package com.joblens.api.analysis;

import com.joblens.document.ExtractionWarning;
import com.joblens.document.ReviewStatus;
import com.joblens.jobposting.model.JobPosting;
import com.joblens.resume.model.CandidateProfile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * The two confirmed documents, posted back by the client.
 *
 * <p>Deliberately shaped like the two confirmation responses, so the client returns what it was
 * given rather than reassembling it. Nothing is stored server-side, which is why the documents make
 * this round trip at all.
 *
 * <p>The trust fields — review status and fingerprint — are checked, and the policies derived from
 * them are recomputed. Sending {@code CONFIRMED} does not make something confirmed.
 */
public record AnalysisRequest(
        @Valid @NotNull ResumeSubmission resume,
        @Valid @NotNull JobSubmission job) {

    public record ResumeSubmission(
            @NotNull ReviewStatus reviewStatus,
            @NotBlank String contentFingerprint,
            @NotBlank String rawText,
            @NotNull CandidateProfile candidateProfile,
            List<ExtractionWarning> extractionWarnings) {

        public List<ExtractionWarning> extractionWarnings() {
            return extractionWarnings == null ? List.of() : extractionWarnings;
        }
    }

    public record JobSubmission(
            @NotNull ReviewStatus reviewStatus,
            @NotBlank String contentFingerprint,
            @NotBlank String rawText,
            @NotNull JobPosting jobPosting,
            List<ExtractionWarning> extractionWarnings) {

        public List<ExtractionWarning> extractionWarnings() {
            return extractionWarnings == null ? List.of() : extractionWarnings;
        }
    }
}
