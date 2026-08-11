package com.joblens.resume;

import com.joblens.document.ExtractionWarning;
import com.joblens.document.ReviewStatus;
import com.joblens.resume.model.CandidateProfile;
import java.time.Instant;
import java.util.List;

/**
 * A resume representation the user has reviewed and confirmed.
 *
 * <p>This, not the extraction output, is what analysis and scoring are allowed to consume. The raw
 * text travels with it so downstream evidence checks can quote the source rather than only the
 * structure derived from it.
 *
 * @param evidenceAbsencePolicy whether a requirement with no matching evidence may be judged a gap
 * @param contentFingerprint digest of {@code rawText} and {@code profile}, so a later request can be
 *        shown to be carrying this exact confirmed content
 */
public record ConfirmedResume(
        ReviewStatus reviewStatus,
        Instant confirmedAt,
        String contentFingerprint,
        String rawText,
        CandidateProfile profile,
        List<ExtractionWarning> warnings,
        EvidenceAbsencePolicy evidenceAbsencePolicy) {

    public ConfirmedResume {
        warnings = List.copyOf(warnings);
    }
}
