package com.joblens.document;

/**
 * Whether a resume representation has actually been reviewed by the person it describes.
 *
 * <p>Extraction produces {@link #REVIEW_REQUIRED} and can never produce anything else. A successful
 * HTTP response means the file was read, not that the reading was correct, and the difference
 * matters because structural parsing is heuristic. Only an explicit confirmation, submitted through
 * the confirmation endpoint, yields {@link #CONFIRMED}.
 */
public enum ReviewStatus {

    /** Produced by extraction. The structure is a machine's guess and has not been checked. */
    REVIEW_REQUIRED,

    /** The user reviewed the extracted content, corrected it if needed, and confirmed it. */
    CONFIRMED
}
