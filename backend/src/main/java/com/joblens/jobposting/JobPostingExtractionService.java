package com.joblens.jobposting;

import com.joblens.config.JoblensProperties;
import com.joblens.document.ReviewStatus;
import com.joblens.error.ApiException;
import com.joblens.error.ErrorCode;
import com.joblens.jobposting.extract.ExtractedPageContent;
import com.joblens.jobposting.extract.PageAccess;
import com.joblens.jobposting.extract.PageAccessAssessor;
import com.joblens.jobposting.extract.PageContentExtractor;
import com.joblens.jobposting.fetch.FetchedPage;
import com.joblens.jobposting.fetch.SafeHttpFetcher;
import com.joblens.jobposting.fetch.SafeUrlValidator;
import com.joblens.jobposting.model.JobPosting;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Turns a job description into a reviewable structure, whether it was pasted or fetched.
 *
 * <p>Both routes converge on the same normalizer and parser, so a posting read from a page is held
 * to exactly the same standard as one pasted by hand — including the review contract, the warnings
 * and the requirement-source policy.
 *
 * <p>Every failure on the URL route offers pasting as a way forward. JobLens does not claim to read
 * every job page, and a posting behind a login or a bot wall is not a dead end for the user.
 */
@Service
public class JobPostingExtractionService {

    private static final Logger LOG = LoggerFactory.getLogger(JobPostingExtractionService.class);

    private final JoblensProperties.JobPosting limits;
    private final JobPostingTextNormalizer normalizer;
    private final JobPostingParser parser;
    private final SafeUrlValidator urlValidator;
    private final SafeHttpFetcher fetcher;
    private final PageContentExtractor pageContentExtractor;
    private final PageAccessAssessor pageAccessAssessor;

    public JobPostingExtractionService(JoblensProperties properties, JobPostingTextNormalizer normalizer,
            JobPostingParser parser, SafeUrlValidator urlValidator, SafeHttpFetcher fetcher,
            PageContentExtractor pageContentExtractor, PageAccessAssessor pageAccessAssessor) {
        this.limits = properties.jobPosting();
        this.normalizer = normalizer;
        this.parser = parser;
        this.urlValidator = urlValidator;
        this.fetcher = fetcher;
        this.pageContentExtractor = pageContentExtractor;
        this.pageAccessAssessor = pageAccessAssessor;
    }

    public JobPostingExtractionResult extractFromText(String pastedText) {
        String extractionId = newExtractionId();
        long startedAt = System.nanoTime();

        String normalized = requireUsableLength(normalizer.normalize(pastedText == null ? "" : pastedText),
                ErrorCode.JD_TEXT_TOO_SHORT);
        ParsedJobPosting parsed = parser.parse(normalized);

        return result(extractionId, JobPostingExtractionResult.SourceType.TEXT, normalized, parsed.posting(),
                parsed, null, startedAt);
    }

    public JobPostingExtractionResult extractFromUrl(String url) {
        String extractionId = newExtractionId();
        long startedAt = System.nanoTime();

        SafeUrlValidator.ValidatedUrl target = urlValidator.validate(url);
        FetchedPage page = fetcher.fetch(target);
        ExtractedPageContent content = pageContentExtractor.extract(page.body());
        String normalized = normalizer.normalize(content.text());

        PageAccess access = pageAccessAssessor.assess(page.body(), normalized, limits.minTextCharacters());
        if (access != PageAccess.READABLE) {
            throw refusal(access, extractionId);
        }

        requireUsableLength(normalized, ErrorCode.JD_EXTRACTION_INSUFFICIENT);
        ParsedJobPosting parsed = parser.parse(normalized);
        JobPosting posting = merge(parsed.posting(), content, page.finalUrl());

        JobPostingExtractionResult.FetchMetadata metadata = new JobPostingExtractionResult.FetchMetadata(
                page.finalUrl(), page.statusCode(), content.strategy(), false,
                page.redirectCount(), page.fetchMs());

        return result(extractionId, JobPostingExtractionResult.SourceType.URL, normalized, posting,
                parsed, metadata, startedAt);
    }

    /**
     * Turns a page that declined into an answer the user can act on.
     *
     * <p>A refusal is accepted, never worked around. JobLens does not retry with a different user
     * agent, and the browser-rendering fallback planned for a later phase is for pages that simply
     * need JavaScript — never for getting past a bot check, a sign-in or an explicit denial.
     *
     * <p>{@code pageAccess} is logged so that pages needing rendering can be told apart from pages
     * that said no.
     */
    private ApiException refusal(PageAccess access, String extractionId) {
        LOG.info("job posting page not readable extractionId={} pageAccess={}", extractionId, access);

        return switch (access) {
            case BOT_CHECK -> new ApiException(ErrorCode.URL_BLOCKED_BY_SITE,
                    "That site answered with an automated-access check instead of the posting.");
            case LOGIN_WALL -> new ApiException(ErrorCode.URL_LOGIN_REQUIRED,
                    "That posting is behind a sign-in page.");
            case JAVASCRIPT_REQUIRED -> new ApiException(ErrorCode.JD_EXTRACTION_INSUFFICIENT,
                    "That page builds its content in the browser, so the page itself contained no "
                            + "job description to read.");
            case READABLE -> new ApiException(ErrorCode.INTERNAL_ERROR,
                    "The request could not be completed.");
        };
    }

    /**
     * A page that yields almost no text is not an error to hide behind a stack trace. It usually
     * means the posting needs JavaScript, sits behind a wall, or is not a posting at all.
     */
    private String requireUsableLength(String normalized, ErrorCode tooShortCode) {
        if (normalized.length() < limits.minTextCharacters()) {
            throw new ApiException(tooShortCode, tooShortCode == ErrorCode.JD_TEXT_TOO_SHORT
                    ? "Only %d characters were pasted. At least %d are needed to analyse a role."
                            .formatted(normalized.length(), limits.minTextCharacters())
                    : "Only %d characters of job description could be read from that page."
                            .formatted(normalized.length()));
        }
        if (normalized.length() > limits.maxTextCharacters()) {
            throw new ApiException(ErrorCode.JD_TEXT_TOO_LONG,
                    "This text is %d characters. The limit is %d."
                            .formatted(normalized.length(), limits.maxTextCharacters()));
        }
        return normalized;
    }

    /** Structured data the site published beats anything inferred from its markup. */
    private static JobPosting merge(JobPosting parsed, ExtractedPageContent content, String finalUrl) {
        return new JobPosting(
                preferring(content.title(), parsed.title()),
                preferring(content.company(), parsed.company()),
                preferring(content.location(), parsed.location()),
                preferring(content.employmentType(), parsed.employmentType()),
                preferring(content.compensationText(), parsed.compensationText()),
                parsed.responsibilities(),
                parsed.requiredQualifications(),
                parsed.preferredQualifications(),
                parsed.otherSections(),
                finalUrl);
    }

    private static String preferring(String structured, String parsed) {
        return structured != null && !structured.isBlank() ? structured : parsed;
    }

    private JobPostingExtractionResult result(String extractionId,
            JobPostingExtractionResult.SourceType sourceType, String rawText, JobPosting posting,
            ParsedJobPosting parsed, JobPostingExtractionResult.FetchMetadata metadata, long startedAt) {

        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        LOG.info("job posting extracted extractionId={} source={} strategy={} characters={} required={} "
                        + "preferred={} responsibilities={} warnings={} elapsedMs={}",
                extractionId, sourceType, metadata == null ? "PASTED" : metadata.strategy(), rawText.length(),
                posting.requiredQualifications().size(), posting.preferredQualifications().size(),
                posting.responsibilities().size(), parsed.warnings().size(), elapsedMs);

        return new JobPostingExtractionResult(
                extractionId,
                sourceType,
                ReviewStatus.REVIEW_REQUIRED,
                JobPostingReliability.policyFor(ReviewStatus.REVIEW_REQUIRED, parsed.warnings()),
                rawText,
                posting,
                parsed.warnings(),
                metadata,
                elapsedMs);
    }

    private static String newExtractionId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
