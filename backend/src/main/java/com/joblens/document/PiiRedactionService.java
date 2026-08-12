package com.joblens.document;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Removes personal identifiers that an analysis has no use for, before any text leaves the process.
 *
 * <p>An employer's name, a project, a metric and a date range are all career evidence and are kept.
 * A home address, a phone number and an email address are not evidence of anything the product
 * scores, so they are replaced. The point is that enabling a real provider should not also mean
 * handing a third party a contact list.
 *
 * <p>Replacement is with a visible token rather than deletion, so the model can still tell that a
 * contact block existed and does not try to read the surrounding lines as prose.
 */
@Service
public class PiiRedactionService {

    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    /** Deliberately broad: North American, international and dotted forms all appear on resumes. */
    private static final Pattern PHONE = Pattern.compile(
            "(?<![\\w-])(?:\\+?\\d{1,3}[\\s.-]?)?(?:\\(\\d{2,4}\\)|\\d{2,4})[\\s.-]?\\d{3}[\\s.-]?\\d{4}(?![\\w-])");

    private static final Pattern STREET_ADDRESS = Pattern.compile(
            "\\b\\d{1,6}\\s+[A-Za-z0-9.'-]+(?:\\s+[A-Za-z0-9.'-]+){0,3}\\s+"
                    + "(?:street|st|avenue|ave|road|rd|boulevard|blvd|drive|dr|lane|ln|court|ct|way|"
                    + "place|pl|terrace|ter|crescent|cres)\\b\\.?"
                    + "(?:\\s*(?:apt|apartment|unit|suite|ste)\\.?\\s*[\\w-]+)?",
            Pattern.CASE_INSENSITIVE);

    /** Canadian postal codes and US ZIP+4. */
    private static final Pattern POSTAL_CODE = Pattern.compile(
            "\\b(?:[A-Za-z]\\d[A-Za-z][ -]?\\d[A-Za-z]\\d|\\d{5}(?:-\\d{4})?)\\b");

    private static final List<Replacement> REPLACEMENTS = List.of(
            new Replacement(EMAIL, "[EMAIL]"),
            new Replacement(STREET_ADDRESS, "[ADDRESS]"),
            new Replacement(PHONE, "[PHONE]"),
            new Replacement(POSTAL_CODE, "[POSTAL CODE]"));

    private record Replacement(Pattern pattern, String token) {}

    /**
     * @param candidateName the name as extracted, replaced wherever it appears. Analysis never needs
     *        to know who the candidate is, and a name in the prompt invites the model to guess at
     *        things a name should not tell it.
     */
    public String redact(String text, String candidateName) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String redacted = text;
        for (Replacement replacement : REPLACEMENTS) {
            redacted = replacement.pattern().matcher(redacted).replaceAll(replacement.token());
        }
        return replaceName(redacted, candidateName);
    }

    public String redact(String text) {
        return redact(text, null);
    }

    private static String replaceName(String text, String candidateName) {
        if (candidateName == null || candidateName.isBlank() || candidateName.length() < 3) {
            return text;
        }
        String redacted = text.replace(candidateName, "[CANDIDATE]");
        // Also the parts, since resumes repeat a first name inside prose.
        for (String part : candidateName.split("\\s+")) {
            if (part.length() >= 3) {
                redacted = redacted.replaceAll("\\b" + Pattern.quote(part) + "\\b", "[CANDIDATE]");
            }
        }
        return redacted;
    }
}
