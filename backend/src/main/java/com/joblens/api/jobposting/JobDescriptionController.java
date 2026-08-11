package com.joblens.api.jobposting;

import com.joblens.jobposting.JobPostingConfirmationService;
import com.joblens.jobposting.JobPostingExtractionService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Accepts a job description and returns what was read from it.
 *
 * <p>Thin by design: length rules, normalization, parsing and the review contract all live in
 * services, exactly as on the resume side.
 */
@RestController
@RequestMapping("/api/v1/job-descriptions")
class JobDescriptionController {

    private final JobPostingExtractionService extractionService;
    private final JobPostingConfirmationService confirmationService;

    JobDescriptionController(JobPostingExtractionService extractionService,
            JobPostingConfirmationService confirmationService) {
        this.extractionService = extractionService;
        this.confirmationService = confirmationService;
    }

    @PostMapping(path = "/extract", consumes = MediaType.APPLICATION_JSON_VALUE)
    JobDescriptionExtractionResponse extract(@Valid @RequestBody JobDescriptionExtractionRequest request) {
        return JobDescriptionExtractionResponse.from(request.hasUrl()
                ? extractionService.extractFromUrl(request.url())
                : extractionService.extractFromText(request.text()));
    }

    @PostMapping(path = "/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    JobDescriptionConfirmationResponse confirm(@Valid @RequestBody JobDescriptionConfirmationRequest request) {
        return JobDescriptionConfirmationResponse.from(confirmationService.confirm(
                request.rawText(), request.jobPosting(), request.confirmed(), request.carriedWarnings()));
    }
}
