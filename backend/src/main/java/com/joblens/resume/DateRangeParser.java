package com.joblens.resume;

import com.joblens.resume.model.DateRange;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognises the date ranges resumes actually use, and says how confident it is.
 *
 * <p>A range that only names years is parsed, but marked {@link DateRange.Confidence#LOW}, because
 * "2021 - 2023" could be anything from thirteen months to thirty-five. Seniority judgements depend
 * on these numbers, so overstating precision here would quietly distort a score later.
 */
public final class DateRangeParser {

    private static final Map<String, String> MONTHS = Map.ofEntries(
            Map.entry("jan", "01"), Map.entry("feb", "02"), Map.entry("mar", "03"),
            Map.entry("apr", "04"), Map.entry("may", "05"), Map.entry("jun", "06"),
            Map.entry("jul", "07"), Map.entry("aug", "08"), Map.entry("sep", "09"),
            Map.entry("oct", "10"), Map.entry("nov", "11"), Map.entry("dec", "12"));

    private static final String MONTH_YEAR = "(?:[A-Za-z]{3,9}\\.?\\s+\\d{4})";
    private static final String NUMERIC_MONTH_YEAR = "(?:\\d{1,2}[/.-]\\d{4})";
    private static final String YEAR_ONLY = "(?:\\d{4})";
    private static final String ENDPOINT = "(?:" + MONTH_YEAR + "|" + NUMERIC_MONTH_YEAR + "|" + YEAR_ONLY + ")";
    private static final String PRESENT = "(?:present|current|now|ongoing)";
    private static final String SEPARATOR = "\\s*(?:-|–|—|to|until)\\s*";

    private static final Pattern RANGE = Pattern.compile(
            "(" + ENDPOINT + ")" + SEPARATOR + "(" + PRESENT + "|" + ENDPOINT + ")",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern MONTH_YEAR_PARTS =
            Pattern.compile("([A-Za-z]{3,9})\\.?\\s+(\\d{4})");
    private static final Pattern NUMERIC_PARTS = Pattern.compile("(\\d{1,2})[/.-](\\d{4})");
    private static final Pattern YEAR_PARTS = Pattern.compile("^(\\d{4})$");

    private static final List<String> PRESENT_WORDS = List.of("present", "current", "now", "ongoing");

    private DateRangeParser() {}

    /** @return the first date range in {@code line}, or empty when the line contains none */
    public static Optional<DateRange> findIn(String line) {
        Matcher matcher = RANGE.matcher(line);
        if (!matcher.find()) {
            return Optional.empty();
        }

        String raw = matcher.group().strip();
        String startText = matcher.group(1).strip();
        String endText = matcher.group(2).strip();
        boolean current = PRESENT_WORDS.contains(endText.toLowerCase(Locale.ROOT));

        String start = toYearMonth(startText);
        String end = current ? null : toYearMonth(endText);

        boolean precise = isPrecise(startText) && (current || isPrecise(endText));
        DateRange.Confidence confidence =
                precise && start != null ? DateRange.Confidence.HIGH : DateRange.Confidence.LOW;

        return Optional.of(new DateRange(raw, start, end, current, confidence));
    }

    /** @return the matched range text, so a caller can strip it out of a heading line */
    public static Optional<String> findRawIn(String line) {
        Matcher matcher = RANGE.matcher(line);
        return matcher.find() ? Optional.of(matcher.group().strip()) : Optional.empty();
    }

    private static boolean isPrecise(String endpoint) {
        return !YEAR_PARTS.matcher(endpoint).matches();
    }

    private static String toYearMonth(String endpoint) {
        Matcher monthYear = MONTH_YEAR_PARTS.matcher(endpoint);
        if (monthYear.matches()) {
            String month = MONTHS.get(monthYear.group(1).toLowerCase(Locale.ROOT).substring(0, 3));
            return month == null ? null : monthYear.group(2) + "-" + month;
        }

        Matcher numeric = NUMERIC_PARTS.matcher(endpoint);
        if (numeric.matches()) {
            int month = Integer.parseInt(numeric.group(1));
            if (month < 1 || month > 12) {
                return null;
            }
            return numeric.group(2) + "-" + "%02d".formatted(month);
        }

        Matcher yearOnly = YEAR_PARTS.matcher(endpoint);
        if (yearOnly.matches()) {
            return yearOnly.group(1) + "-01";
        }
        return null;
    }
}
