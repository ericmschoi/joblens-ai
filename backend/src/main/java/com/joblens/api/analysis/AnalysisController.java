package com.joblens.api.analysis;

import com.joblens.analysis.AnalysisResult;
import com.joblens.analysis.AnalysisService;
import com.joblens.jobposting.ConfirmedJobPosting;
import com.joblens.resume.ConfirmedResume;
import com.joblens.scoring.FitScoreCalculator;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runs an analysis over two confirmed documents.
 *
 * <p>Thin, as everywhere: verification, redaction, prompting, validation and grounding all live in
 * the analysis package. The controller only maps the request onto the confirmed representations the
 * service is allowed to consume.
 */
@RestController
@RequestMapping("/api/v1/analyses")
class AnalysisController {

    private final AnalysisService analysisService;
    private final FitScoreCalculator scoreCalculator;

    AnalysisController(AnalysisService analysisService, FitScoreCalculator scoreCalculator) {
        this.analysisService = analysisService;
        this.scoreCalculator = scoreCalculator;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    AnalysisResponse analyse(@Valid @RequestBody AnalysisRequest request) {
        AnalysisResult result = analysisService.analyse(toResume(request.resume()), toJob(request.job()));
        return AnalysisResponse.from(result, scoreCalculator.calculate(result.draft(),
                result.candidateProfile(), result.resumeWarnings(), result.resumeCharacters(),
                result.groundingFailureRatio()));
    }

    /**
     * The policy fields are left for the service to recompute. Reconstructing them here from the
     * request would defeat the point of recomputing them.
     */
    private static ConfirmedResume toResume(AnalysisRequest.ResumeSubmission submission) {
        return new ConfirmedResume(submission.reviewStatus(), Instant.EPOCH,
                submission.contentFingerprint(), submission.rawText(), submission.candidateProfile(),
                submission.extractionWarnings(), null);
    }

    private static ConfirmedJobPosting toJob(AnalysisRequest.JobSubmission submission) {
        return new ConfirmedJobPosting(submission.reviewStatus(), Instant.EPOCH,
                submission.contentFingerprint(), submission.rawText(), submission.jobPosting(),
                submission.extractionWarnings(), null);
    }
}
