package com.joblens.jobposting;

import com.joblens.config.JoblensProperties;
import com.joblens.document.ReviewStatus;
import com.joblens.error.ApiException;
import com.joblens.error.ErrorCode;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Turns a pasted job description into a reviewable structure.
 *
 * <p>Pasted text is untrusted data. It is length-checked, normalized and parsed, and anything in it
 * that reads as an instruction is reported to the user rather than obeyed or removed.
 *
 * <p>Reading a posting from a URL is a separate, higher-risk capability that arrives with its own
 * safety boundary. Until then this service says so plainly instead of failing in a way the user
 * cannot act on.
 */
@Service
public class JobPostingExtractionService {

    private static final Logger LOG = LoggerFactory.getLogger(JobPostingExtractionService.class);

    private final JoblensProperties.JobPosting limits;
    private final JobPostingTextNormalizer normalizer;
    private final JobPostingParser parser;

    public JobPostingExtractionService(JoblensProperties properties, JobPostingTextNormalizer normalizer,
            JobPostingParser parser) {
        this.limits = properties.jobPosting();
        this.normalizer = normalizer;
        this.parser = parser;
    }

    public JobPostingExtractionResult extractFromText(String pastedText) {
        String extractionId = UUID.randomUUID().toString().substring(0, 8);
        long startedAt = System.nanoTime();

        String normalized = normalizer.normalize(pastedText == null ? "" : pastedText);

        if (normalized.length() < limits.minTextCharacters()) {
            throw new ApiException(ErrorCode.JD_TEXT_TOO_SHORT,
                    "Only %d characters were pasted. At least %d are needed to analyse a role."
                            .formatted(normalized.length(), limits.minTextCharacters()));
        }
        if (normalized.length() > limits.maxTextCharacters()) {
            throw new ApiException(ErrorCode.JD_TEXT_TOO_LONG,
                    "This text is %d characters. The limit is %d."
                            .formatted(normalized.length(), limits.maxTextCharacters()));
        }

        ParsedJobPosting parsed = parser.parse(normalized);
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

        LOG.info("job posting extracted extractionId={} source=TEXT characters={} required={} preferred={} "
                        + "responsibilities={} warnings={} elapsedMs={}",
                extractionId, normalized.length(),
                parsed.posting().requiredQualifications().size(),
                parsed.posting().preferredQualifications().size(),
                parsed.posting().responsibilities().size(),
                parsed.warnings().size(), elapsedMs);

        return new JobPostingExtractionResult(
                extractionId,
                JobPostingExtractionResult.SourceType.TEXT,
                ReviewStatus.REVIEW_REQUIRED,
                JobPostingReliability.policyFor(ReviewStatus.REVIEW_REQUIRED, parsed.warnings()),
                normalized,
                parsed.posting(),
                parsed.warnings(),
                elapsedMs);
    }

    /** Reading a posting from a URL needs the safe-fetch boundary, which is not built yet. */
    public JobPostingExtractionResult extractFromUrl(String url) {
        throw new ApiException(ErrorCode.JD_URL_MODE_UNAVAILABLE,
                "JobLens cannot read job pages yet.");
    }
}
