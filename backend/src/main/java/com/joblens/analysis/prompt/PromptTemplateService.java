package com.joblens.analysis.prompt;

import com.joblens.analysis.AnalysisInput;
import com.joblens.error.ApiException;
import com.joblens.error.ErrorCode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

/**
 * Builds the two prompts a provider is given, from versioned assets outside the code.
 *
 * <p>Prompts are assets rather than string literals so they can be reviewed, diffed and versioned
 * like the schema they produce. A change to how the model is instructed is a change to the product.
 *
 * <p>Documents are wrapped in a delimiter containing a random nonce generated per request. A
 * document cannot close a fence it cannot predict, which is what stops planted text from escaping
 * the data section and being read as instruction.
 */
@Service
public class PromptTemplateService {

    public static final String PROMPT_VERSION = "v1";

    private static final String SYSTEM_ASSET = "prompts/" + PROMPT_VERSION + "/system.md";
    private static final String USER_ASSET = "prompts/" + PROMPT_VERSION + "/analysis.md";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String systemTemplate;
    private final String userTemplate;

    public PromptTemplateService() {
        this.systemTemplate = read(SYSTEM_ASSET);
        this.userTemplate = read(USER_ASSET);
    }

    public String systemPrompt() {
        return systemTemplate;
    }

    public String userPrompt(AnalysisInput input) {
        String nonce = newNonce();
        return userTemplate
                .replace("{{NONCE}}", nonce)
                .replace("{{RESUME}}", fence("RESUME", nonce, input.resumeText()))
                .replace("{{JOB_POSTING}}", fence("JOB_POSTING", nonce, input.jobText()))
                .replace("{{REQUIREMENT_SOURCE_RULE}}", input.requirementsMustComeFromFullText()
                        ? "The posting's qualification lists may be incomplete. Read the whole "
                                + "description and decompose requirements from it, not only from the lists."
                        : "The posting's qualification sections were recognised cleanly and may be used "
                                + "as the requirement list.")
                .replace("{{EVIDENCE_ABSENCE_RULE}}", input.absentEvidenceMustBeUnknown()
                        ? "The resume's structure is unverified. When you find no evidence for a "
                                + "requirement, report UNKNOWN. You may not report GAP in this analysis."
                        : "The resume was reviewed and confirmed by the candidate. When the resume "
                                + "clearly does not support a requirement you may report GAP.");
    }

    private static String fence(String label, String nonce, String content) {
        String marker = "-----%s %s-----".formatted(label, nonce);
        return marker + "\n" + (content == null ? "" : content) + "\n" + marker;
    }

    private static String newNonce() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String read(String asset) {
        try {
            return StreamUtils.copyToString(new ClassPathResource(asset).getInputStream(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing prompt asset: " + asset, e);
        }
    }

    /** Guards against a template that lost a placeholder in an edit. */
    public void requirePlaceholdersResolved(String prompt) {
        if (prompt.contains("{{") && prompt.contains("}}")) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "The request could not be completed.");
        }
    }
}
