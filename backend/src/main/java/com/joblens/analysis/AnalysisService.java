package com.joblens.analysis;

import com.joblens.analysis.model.AnalysisDraft;
import com.joblens.analysis.prompt.PromptTemplateService;
import com.joblens.analysis.provider.AnalysisProvider;
import com.joblens.analysis.validate.AnalysisDraftValidator;
import com.joblens.analysis.validate.EvidenceGroundingChecker;
import com.joblens.config.JoblensProperties;
import com.joblens.document.ContentFingerprint;
import com.joblens.document.PiiRedactionService;
import com.joblens.document.ReviewStatus;
import com.joblens.error.ApiException;
import com.joblens.error.ErrorCode;
import com.joblens.jobposting.ConfirmedJobPosting;
import com.joblens.jobposting.JobPostingReliability;
import com.joblens.jobposting.RequirementSourcePolicy;
import com.joblens.resume.ConfirmedResume;
import com.joblens.resume.EvidenceAbsencePolicy;
import com.joblens.resume.ResumeEvidenceReliability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs one analysis: verify, redact, ask, validate, ground.
 *
 * <p>Nothing the client asserts about trust is believed. The confirmation status is checked, the
 * content is re-fingerprinted, and the two policies that decide whether absent evidence may count
 * as a gap are recomputed here rather than read from the request. A client that edited its payload
 * after confirming gets a clean rejection, not a sharper score.
 */
@Service
public class AnalysisService {

    private static final Logger LOG = LoggerFactory.getLogger(AnalysisService.class);

    private final JoblensProperties.Analysis settings;
    private final ContentFingerprint fingerprints;
    private final PiiRedactionService redaction;
    private final PromptTemplateService prompts;
    private final AnalysisProvider provider;
    private final AnalysisDraftValidator validator;
    private final EvidenceGroundingChecker grounding;

    public AnalysisService(JoblensProperties properties, ContentFingerprint fingerprints,
            PiiRedactionService redaction, PromptTemplateService prompts, AnalysisProvider provider,
            AnalysisDraftValidator validator, EvidenceGroundingChecker grounding) {
        this.settings = properties.analysis();
        this.fingerprints = fingerprints;
        this.redaction = redaction;
        this.prompts = prompts;
        this.provider = provider;
        this.validator = validator;
        this.grounding = grounding;
    }

    public AnalysisResult analyse(ConfirmedResume resume, ConfirmedJobPosting posting) {
        long startedAt = System.nanoTime();

        requireConfirmed(resume.reviewStatus(), posting.reviewStatus());
        requireUnchanged(resume, posting);

        // Recomputed, not taken from the request: these decide whether a missing requirement is
        // allowed to count against the candidate.
        EvidenceAbsencePolicy evidencePolicy =
                ResumeEvidenceReliability.policyFor(resume.reviewStatus(), resume.warnings());
        RequirementSourcePolicy requirementPolicy =
                JobPostingReliability.policyFor(posting.reviewStatus(), posting.warnings());

        AnalysisInput input = new AnalysisInput(
                redaction.redact(resume.rawText(), candidateNameIn(resume)),
                resume.profile(),
                redaction.redact(posting.rawText()),
                posting.posting(),
                requirementPolicy == RequirementSourcePolicy.FULL_TEXT_FALLBACK,
                evidencePolicy == EvidenceAbsencePolicy.MUST_BE_UNKNOWN);

        String systemPrompt = prompts.systemPrompt();
        String userPrompt = prompts.userPrompt(input);
        prompts.requirePlaceholdersResolved(userPrompt);

        String rawJson = callProvider(input, systemPrompt, userPrompt);
        AnalysisDraft validated = validator.validate(rawJson,
                evidencePolicy == EvidenceAbsencePolicy.MUST_BE_UNKNOWN);
        EvidenceGroundingChecker.Result grounded = grounding.check(validated, resume.rawText());

        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        LOG.info("analysis complete provider={} promptVersion={} requirements={} groundedEvidence={} "
                        + "droppedEvidence={} evidencePolicy={} requirementPolicy={} elapsedMs={}",
                provider.id(), PromptTemplateService.PROMPT_VERSION,
                grounded.draft().requirementAssessments().size(), grounded.groundedCount(),
                grounded.droppedCount(), evidencePolicy, requirementPolicy, elapsedMs);

        return new AnalysisResult(grounded.draft(), resume.profile(), resume.warnings(),
                resume.rawText().length(), provider.id(), PromptTemplateService.PROMPT_VERSION,
                grounded.groundedCount(), grounded.droppedCount(), grounded.groundingFailureRatio(),
                elapsedMs);
    }

    private String callProvider(AnalysisInput input, String systemPrompt, String userPrompt) {
        try {
            return provider.analyze(input, systemPrompt, userPrompt);
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            LOG.error("code={} provider={} cause={}", ErrorCode.AI_PROVIDER_UNAVAILABLE, settings.provider(),
                    e.getClass().getSimpleName());
            throw new ApiException(ErrorCode.AI_PROVIDER_UNAVAILABLE,
                    "The analysis service could not be reached.", e);
        }
    }

    private static void requireConfirmed(ReviewStatus resumeStatus, ReviewStatus postingStatus) {
        if (resumeStatus != ReviewStatus.CONFIRMED || postingStatus != ReviewStatus.CONFIRMED) {
            throw new ApiException(ErrorCode.REVIEW_NOT_CONFIRMED,
                    "Both the resume and the job description must be reviewed and confirmed first.");
        }
    }

    /** The digest is the only thing tying this payload to the content that was actually reviewed. */
    private void requireUnchanged(ConfirmedResume resume, ConfirmedJobPosting posting) {
        boolean resumeMatches = fingerprints.matches(
                resume.contentFingerprint(), resume.rawText(), resume.profile());
        boolean postingMatches = fingerprints.matches(
                posting.contentFingerprint(), posting.rawText(), posting.posting());

        if (!resumeMatches || !postingMatches) {
            LOG.warn("code={} resumeMatches={} postingMatches={}", ErrorCode.ANALYSIS_CONTENT_MISMATCH,
                    resumeMatches, postingMatches);
            throw new ApiException(ErrorCode.ANALYSIS_CONTENT_MISMATCH,
                    "The content submitted for analysis does not match what was confirmed.");
        }
    }

    /** Best effort: the profile does not carry a name field, so the summary's first line is used. */
    private static String candidateNameIn(ConfirmedResume resume) {
        return resume.rawText().lines()
                .map(String::strip)
                .filter(line -> !line.isBlank())
                .findFirst()
                .filter(line -> line.length() <= 60 && line.split("\\s+").length <= 4)
                .orElse(null);
    }
}
