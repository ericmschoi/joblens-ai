package com.joblens.document;

/**
 * A single extraction quality signal.
 *
 * @param code stable identifier clients can branch on
 * @param severity how strongly the user should be nudged to check this
 * @param message user-facing English copy; never contains document content
 * @param page 1-based page the warning refers to, or {@code null} when it applies to the document
 */
public record ExtractionWarning(
        WarningCode code, WarningCode.Severity severity, String message, Integer page) {

    public static ExtractionWarning of(WarningCode code) {
        return new ExtractionWarning(code, code.severity(), code.message(), null);
    }

    public static ExtractionWarning onPage(WarningCode code, int page) {
        return new ExtractionWarning(code, code.severity(), code.message(), page);
    }
}
