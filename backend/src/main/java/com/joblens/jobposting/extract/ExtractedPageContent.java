package com.joblens.jobposting.extract;

/**
 * What a page yielded, and how.
 *
 * @param strategy which extractor produced this, reported to the user so an unreliable reading is
 *        attributable rather than mysterious
 * @param text the posting text, ready for the shared parser
 * @param title structured metadata, when the page published it; {@code null} when it did not
 */
public record ExtractedPageContent(
        Strategy strategy,
        String text,
        String title,
        String company,
        String location,
        String employmentType,
        String compensationText) {

    public enum Strategy {
        /** schema.org JobPosting data the site published for search engines. The most reliable source. */
        JSON_LD,
        ATS_GREENHOUSE,
        ATS_LEVER,
        ATS_ASHBY,
        ATS_WORKDAY,
        /** Ordinary HTML, read structurally. */
        GENERIC_HTML
    }

    public static ExtractedPageContent generic(String text) {
        return new ExtractedPageContent(Strategy.GENERIC_HTML, text, null, null, null, null, null);
    }
}
