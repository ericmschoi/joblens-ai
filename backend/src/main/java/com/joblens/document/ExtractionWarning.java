package com.joblens.document;

/**
 * A single extraction quality signal.
 *
 * <p>{@code code} and {@code message} are deliberately separate. Clients branch on the code, which
 * is stable; the message is display copy that can be reworded without breaking anything. Anything a
 * client might want to act on structurally belongs in {@code count} or {@code page}, never inside
 * the message text.
 *
 * @param code stable identifier clients branch on
 * @param severity how strongly the user should be nudged to check this
 * @param message user-facing English copy; never contains document content
 * @param page 1-based page the warning refers to, or {@code null} when it applies to the document
 * @param count how many times the condition was found, when counting it is meaningful
 */
public record ExtractionWarning(
        WarningCode code, WarningCode.Severity severity, String message, Integer page, Integer count) {

    public static ExtractionWarning of(WarningCode code) {
        return new ExtractionWarning(code, code.severity(), code.message(), null, null);
    }

    public static ExtractionWarning onPage(WarningCode code, int page) {
        return new ExtractionWarning(code, code.severity(), code.message(), page, null);
    }

    public static ExtractionWarning counted(WarningCode code, int count) {
        return new ExtractionWarning(code, code.severity(), code.message(), null, count);
    }
}
