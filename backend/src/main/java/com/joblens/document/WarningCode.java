package com.joblens.document;

/**
 * Extraction quality signals shown to the user before an analysis runs.
 *
 * <p>A warning is not an error. It tells the user what to check in the review step, because a
 * silently confident parser is more dangerous than a visible uncertainty. Messages are user-facing
 * English copy and never contain document content.
 */
public enum WarningCode {

    LOW_TEXT_DENSITY(Severity.WARNING,
            "Some pages contained very little text. Check that nothing important is missing."),
    POSSIBLE_MULTI_COLUMN(Severity.HIGH,
            "This layout looks like it uses columns, so lines may have been read in the wrong order. "
                    + "Check the order of the text below carefully."),
    REPEATED_HEADER_FOOTER(Severity.INFO,
            "The same header or footer appears on several pages and may be repeated in the text below."),
    BROKEN_WORDS(Severity.WARNING,
            "Some words appear to be split apart. This usually comes from the PDF's letter spacing."),
    TEXT_TRUNCATED(Severity.HIGH,
            "This document was longer than JobLens processes, so the text below is incomplete."),
    ENCRYPTED_BUT_READABLE(Severity.INFO,
            "This PDF has usage restrictions, but its text could still be read."),
    NO_SECTIONS_DETECTED(Severity.HIGH,
            "No standard resume sections were recognised, so the structured view below may be incomplete. "
                    + "The full text was still extracted."),
    NO_ROLES_DETECTED(Severity.HIGH,
            "No work experience entries could be identified. Add them in the review step, "
                    + "otherwise this analysis cannot judge your experience."),
    UNASSIGNED_TEXT_BLOCKS(Severity.WARNING,
            "Some text could not be attached to a section or a role. It is still in the full text below, "
                    + "but it is missing from the structured view."),
    LOW_CONFIDENCE_STRUCTURE(Severity.WARNING,
            "Some entries are missing a title, an employer or a reliable date range. "
                    + "Check them before running the analysis."),
    POSSIBLE_EMBEDDED_INSTRUCTIONS(Severity.HIGH,
            "This document contains text that looks like instructions to an AI system. "
                    + "JobLens treats document content as evidence only and ignores such instructions.");

    private final Severity severity;
    private final String message;

    WarningCode(Severity severity, String message) {
        this.severity = severity;
        this.message = message;
    }

    public Severity severity() {
        return severity;
    }

    public String message() {
        return message;
    }

    public enum Severity {
        INFO,
        WARNING,
        HIGH
    }
}
