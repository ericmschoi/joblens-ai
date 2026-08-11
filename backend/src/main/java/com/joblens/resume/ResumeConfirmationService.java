package com.joblens.resume;

import com.joblens.config.JoblensProperties;
import com.joblens.document.ExtractionWarning;
import com.joblens.error.ApiException;
import com.joblens.error.ErrorCode;
import com.joblens.resume.model.CandidateProfile;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Turns a reviewed resume into the only representation analysis is allowed to consume.
 *
 * <p>Confirmation is an act, not a side effect. Extraction never produces a confirmed resume, no
 * matter how clean the parse looked, because only the candidate can say whether the machine read
 * their document correctly.
 *
 * <p>The submitted profile is re-checked rather than taken at face value. A user who resolves a
 * parsing problem by deleting the part that failed should not end up with a result that claims to be
 * reliable.
 */
@Service
public class ResumeConfirmationService {

    private static final Logger LOG = LoggerFactory.getLogger(ResumeConfirmationService.class);

    private final JoblensProperties.Resume limits;
    private final ResumeContentFingerprint fingerprints;
    private final Clock clock;

    public ResumeConfirmationService(JoblensProperties properties, ResumeContentFingerprint fingerprints,
            Clock clock) {
        this.limits = properties.resume();
        this.fingerprints = fingerprints;
        this.clock = clock;
    }

    /**
     * @param confirmed must be explicitly true; the field exists so that confirmation cannot happen
     *        by default, by omission, or as a consequence of a successful extraction call
     */
    public ConfirmedResume confirm(String rawText, CandidateProfile profile, boolean confirmed,
            List<ExtractionWarning> carriedWarnings) {

        if (!confirmed) {
            throw new ApiException(ErrorCode.REVIEW_NOT_CONFIRMED,
                    "This resume has not been confirmed as reviewed.");
        }

        String text = rawText == null ? "" : rawText.strip();
        if (text.length() < limits.minUsableCharacters()) {
            throw new ApiException(ErrorCode.RESUME_TEXT_TOO_SHORT,
                    "The reviewed resume text is too short to analyse.");
        }
        if (text.length() > limits.maxExtractedCharacters()) {
            throw new ApiException(ErrorCode.ANALYSIS_INPUT_TOO_LARGE,
                    "The reviewed resume text is longer than JobLens processes.");
        }

        CandidateProfile reviewed = profile == null ? CandidateProfile.empty() : profile;

        // Warnings that describe how the file was read are carried forward; warnings about the
        // structure are recomputed against what the user actually submitted.
        List<ExtractionWarning> warnings = new java.util.ArrayList<>(
                carriedWarnings == null ? List.of() : carriedWarnings.stream()
                        .filter(warning -> !ResumeStructureWarnings.isStructural(warning.code()))
                        .toList());
        warnings.addAll(ResumeStructureWarnings.forProfile(reviewed));

        EvidenceAbsencePolicy policy =
                ResumeEvidenceReliability.policyFor(ResumeReviewStatus.CONFIRMED, warnings);

        LOG.info("resume confirmed characters={} roles={} warnings={} evidenceAbsencePolicy={}",
                text.length(), reviewed.workExperiences().size(), warnings.size(), policy);

        return new ConfirmedResume(
                ResumeReviewStatus.CONFIRMED,
                clock.instant(),
                fingerprints.of(text, reviewed),
                text,
                reviewed,
                warnings,
                policy);
    }
}
