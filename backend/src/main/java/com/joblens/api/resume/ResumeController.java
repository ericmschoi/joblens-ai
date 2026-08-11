package com.joblens.api.resume;

import com.joblens.error.ApiException;
import com.joblens.error.ErrorCode;
import com.joblens.resume.ResumeConfirmationService;
import com.joblens.resume.ResumeExtractionService;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Accepts a resume upload and returns what was read from it.
 *
 * <p>The controller only adapts HTTP to the pipeline. Validation, parsing and normalization live in
 * their own services, and the uploaded bytes are never written to disk.
 */
@RestController
@RequestMapping("/api/v1/resumes")
class ResumeController {

    private final ResumeExtractionService extractionService;
    private final ResumeConfirmationService confirmationService;

    ResumeController(ResumeExtractionService extractionService, ResumeConfirmationService confirmationService) {
        this.extractionService = extractionService;
        this.confirmationService = confirmationService;
    }

    @PostMapping(path = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResumeExtractionResponse extract(@RequestPart("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new ApiException(ErrorCode.FILE_MISSING, "The uploaded file was empty.");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(ErrorCode.FILE_MISSING, "The uploaded file could not be read.", e);
        }

        return ResumeExtractionResponse.from(extractionService.extract(content));
    }

    /**
     * Records the user's review of an extracted resume, including any corrections they made.
     *
     * <p>This is the only way to obtain a confirmed resume. Extraction alone never produces one,
     * because a successful parse is not evidence that the parse was right.
     */
    @PostMapping(path = "/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResumeConfirmationResponse confirm(@Valid @RequestBody ResumeConfirmationRequest request) {
        return ResumeConfirmationResponse.from(confirmationService.confirm(
                request.rawText(), request.candidateProfile(), request.confirmed(), request.carriedWarnings()));
    }
}
