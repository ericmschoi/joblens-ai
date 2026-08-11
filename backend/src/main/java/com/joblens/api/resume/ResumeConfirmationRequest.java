package com.joblens.api.resume;

import com.joblens.document.ExtractionWarning;
import com.joblens.resume.model.CandidateProfile;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * The reviewed resume, submitted after the user has checked and corrected the extraction.
 *
 * @param rawText the text as the user left it; corrections are theirs to make
 * @param candidateProfile the structured view as the user left it
 * @param carriedWarnings warnings from the extraction step. Signals about how the file was read are
 *        kept; signals about the structure are recomputed from what was actually submitted.
 * @param confirmed must be {@code true}. A separate flag rather than an implicit consequence of
 *        calling this endpoint, so that confirmation is always a deliberate act.
 */
public record ResumeConfirmationRequest(
        String extractionId,
        @NotBlank(message = "Reviewed resume text is required.") String rawText,
        CandidateProfile candidateProfile,
        List<ExtractionWarning> carriedWarnings,
        @AssertTrue(message = "Confirm the reviewed resume before continuing.") boolean confirmed) {}
