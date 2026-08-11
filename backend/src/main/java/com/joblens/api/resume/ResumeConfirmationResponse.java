package com.joblens.api.resume;

import com.joblens.document.ExtractionWarning;
import com.joblens.resume.ConfirmedResume;
import com.joblens.resume.EvidenceAbsencePolicy;
import com.joblens.resume.ResumeReviewStatus;
import com.joblens.resume.model.CandidateProfile;
import java.time.Instant;
import java.util.List;

/**
 * The confirmed resume the client holds and later submits for analysis.
 *
 * @param contentFingerprint digest of the confirmed text and profile. Analysis recomputes it and
 *        refuses to score content that does not match what was reviewed.
 * @param evidenceAbsencePolicy whether a requirement with no matching evidence may be judged a gap.
 *        Still {@code MUST_BE_UNKNOWN} when the confirmed content carries structural uncertainty.
 */
public record ResumeConfirmationResponse(
        String schemaVersion,
        ResumeReviewStatus reviewStatus,
        Instant confirmedAt,
        String contentFingerprint,
        EvidenceAbsencePolicy evidenceAbsencePolicy,
        String rawText,
        CandidateProfile candidateProfile,
        List<ExtractionWarning> extractionWarnings) {

    public static final String SCHEMA_VERSION = "resume-confirmation/v1";

    public static ResumeConfirmationResponse from(ConfirmedResume confirmed) {
        return new ResumeConfirmationResponse(
                SCHEMA_VERSION,
                confirmed.reviewStatus(),
                confirmed.confirmedAt(),
                confirmed.contentFingerprint(),
                confirmed.evidenceAbsencePolicy(),
                confirmed.rawText(),
                confirmed.profile(),
                confirmed.warnings());
    }
}
