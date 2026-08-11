package com.joblens.resume.model;

/**
 * A date range as it appeared in the resume, plus a normalized reading when one could be derived.
 *
 * <p>The raw text is always preserved. Resume dates are written in many shapes, and a confident
 * mis-parse is worse than an honest {@code LOW} confidence, because seniority judgements depend on
 * these values.
 *
 * @param rawText the text exactly as written, for example {@code "Mar 2021 - Present"}
 * @param startYearMonth normalized {@code YYYY-MM}, or {@code null} when it could not be derived
 * @param endYearMonth normalized {@code YYYY-MM}, {@code null} when current or underivable
 * @param current whether the range is open-ended
 * @param parseConfidence {@code HIGH} when both ends parsed cleanly
 */
public record DateRange(
        String rawText,
        String startYearMonth,
        String endYearMonth,
        boolean current,
        Confidence parseConfidence) {

    public enum Confidence {
        HIGH,
        LOW
    }

    public static DateRange unparsed(String rawText) {
        return new DateRange(rawText, null, null, false, Confidence.LOW);
    }
}
