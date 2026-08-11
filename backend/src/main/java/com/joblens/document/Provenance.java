package com.joblens.document;

/**
 * Where a piece of normalized content came from in the original document.
 *
 * <p>Provenance is what makes an analysis auditable: every claim the product later makes about a
 * candidate can be traced back to a location in the resume the user actually confirmed.
 *
 * @param page 1-based page number, or {@code null} when the content spans pages
 * @param section the detected section heading, for example {@code EXPERIENCE}
 * @param lineStart 0-based index of the first source line, inclusive
 * @param lineEnd 0-based index of the last source line, inclusive
 * @param sourceQuote the verbatim source text, kept so evidence can be checked against the document
 */
public record Provenance(Integer page, String section, int lineStart, int lineEnd, String sourceQuote) {

    public static Provenance of(Integer page, String section, int lineStart, int lineEnd, String sourceQuote) {
        return new Provenance(page, section, lineStart, lineEnd, sourceQuote);
    }
}
