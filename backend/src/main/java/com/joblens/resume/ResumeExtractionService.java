package com.joblens.resume;

import com.joblens.document.ExtractionWarning;
import com.joblens.document.ReviewStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs the resume pipeline: validate, extract, normalize.
 *
 * <p>The three steps stay separate services so each can be tested and reasoned about on its own.
 * Log lines carry the extraction id, page count and character count only - never the document, a
 * fragment of it, or the filename, which frequently contains the candidate's name.
 */
@Service
public class ResumeExtractionService {

    private static final Logger LOG = LoggerFactory.getLogger(ResumeExtractionService.class);

    private final PdfValidationService validation;
    private final PdfTextExtractionService extraction;
    private final ResumeNormalizer normalizer;

    public ResumeExtractionService(PdfValidationService validation, PdfTextExtractionService extraction,
            ResumeNormalizer normalizer) {
        this.validation = validation;
        this.extraction = extraction;
        this.normalizer = normalizer;
    }

    public ResumeExtractionResult extract(byte[] content) {
        String extractionId = UUID.randomUUID().toString().substring(0, 8);
        long startedAt = System.nanoTime();

        validation.validate(content);
        ExtractedResumeText text = extraction.extract(content);
        NormalizedResume normalized = normalizer.normalize(text.rawText());

        List<ExtractionWarning> warnings = new ArrayList<>(text.warnings());
        warnings.addAll(normalized.warnings());

        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        LOG.info("resume extracted extractionId={} pages={} characters={} warnings={} elapsedMs={}",
                extractionId, text.pages().size(), text.rawText().length(), warnings.size(), elapsedMs);

        // Extraction output is never confirmed, so absent evidence can only ever mean "unknown" here.
        return new ResumeExtractionResult(
                extractionId,
                ReviewStatus.REVIEW_REQUIRED,
                ResumeEvidenceReliability.policyFor(ReviewStatus.REVIEW_REQUIRED, warnings),
                text.rawText(),
                text.pages(),
                normalized.profile(),
                warnings,
                elapsedMs);
    }
}
