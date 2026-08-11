package com.joblens.api.resume;

import com.joblens.error.ApiException;
import com.joblens.error.ErrorCode;
import com.joblens.resume.ResumeExtractionService;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
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

    ResumeController(ResumeExtractionService extractionService) {
        this.extractionService = extractionService;
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
}
