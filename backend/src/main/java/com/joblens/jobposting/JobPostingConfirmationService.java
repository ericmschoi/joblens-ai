package com.joblens.jobposting;

import com.joblens.config.JoblensProperties;
import com.joblens.document.ContentFingerprint;
import com.joblens.document.ExtractionWarning;
import com.joblens.document.ReviewStatus;
import com.joblens.document.WarningCode;
import com.joblens.error.ApiException;
import com.joblens.error.ErrorCode;
import com.joblens.jobposting.model.JobPosting;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Records the user's review of an extracted job posting.
 *
 * <p>Mirrors resume confirmation deliberately. Both documents feed the same comparison, and a
 * reviewed resume compared against a guessed-at posting is no safer than the other way round.
 */
@Service
public class JobPostingConfirmationService {

    private static final Logger LOG = LoggerFactory.getLogger(JobPostingConfirmationService.class);

    private final JoblensProperties.JobPosting limits;
    private final ContentFingerprint fingerprints;
    private final Clock clock;

    public JobPostingConfirmationService(JoblensProperties properties, ContentFingerprint fingerprints,
            Clock clock) {
        this.limits = properties.jobPosting();
        this.fingerprints = fingerprints;
        this.clock = clock;
    }

    public ConfirmedJobPosting confirm(String rawText, JobPosting posting, boolean confirmed,
            List<ExtractionWarning> carriedWarnings) {

        if (!confirmed) {
            throw new ApiException(ErrorCode.REVIEW_NOT_CONFIRMED,
                    "This job description has not been confirmed as reviewed.");
        }

        String text = rawText == null ? "" : rawText.strip();
        if (text.length() < limits.minTextCharacters()) {
            throw new ApiException(ErrorCode.JD_TEXT_TOO_SHORT,
                    "The reviewed job description is too short to analyse.");
        }
        if (text.length() > limits.maxTextCharacters()) {
            throw new ApiException(ErrorCode.JD_TEXT_TOO_LONG,
                    "The reviewed job description is longer than JobLens processes.");
        }

        JobPosting reviewed = posting == null ? JobPosting.empty() : posting;

        List<ExtractionWarning> warnings = new ArrayList<>(carried(carriedWarnings));
        warnings.addAll(JobPostingStructureWarnings.forPosting(reviewed));

        RequirementSourcePolicy policy = JobPostingReliability.policyFor(ReviewStatus.CONFIRMED, warnings);

        LOG.info("job posting confirmed characters={} required={} preferred={} warnings={} "
                        + "requirementSourcePolicy={}",
                text.length(), reviewed.requiredQualifications().size(),
                reviewed.preferredQualifications().size(), warnings.size(), policy);

        return new ConfirmedJobPosting(
                ReviewStatus.CONFIRMED,
                clock.instant(),
                fingerprints.of(text, reviewed),
                text,
                reviewed,
                warnings,
                policy);
    }

    /**
     * Warnings about the reviewed structure are recomputed; everything else is carried forward.
     *
     * <p>{@code REQUIRED_AND_PREFERRED_NOT_SEPARATED} is deliberately among the ones carried. It
     * states a fact about the source posting, and that fact does not change because the parser's
     * guess at the split was confirmed. Its only effect is that requirement decomposition also reads
     * the full text, which is the right behaviour for a posting that never drew the line itself.
     */
    private static List<ExtractionWarning> carried(List<ExtractionWarning> warnings) {
        if (warnings == null) {
            return List.of();
        }
        return warnings.stream()
                .filter(warning -> !JobPostingStructureWarnings.isStructural(warning.code()))
                .toList();
    }
}
