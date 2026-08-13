package com.joblens.jobposting;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Puts the line breaks back into a posting that arrived as one continuous run of text.
 *
 * <p>Some sources flatten a posting before JobLens ever sees it. Workday's schema.org
 * {@code description} is the clearest case: the site strips its own markup, so what arrives is four
 * thousand characters with no paragraph, no list and no newline anywhere in it. A user pasting from
 * a PDF viewer or a chat window produces the same thing. A line-based parser reads that as one line,
 * finds no headings, and reports a posting with no requirements — which is worse than useless,
 * because the analysis then has nothing to score against.
 *
 * <p>What this does is bounded on purpose. It inserts breaks only in front of vocabulary the parser
 * already recognises — section headings, field labels, and the openers that requirements and
 * responsibilities conventionally start with. It never edits, reorders or drops a word, and it
 * cannot invent a requirement: every character of the input survives in the output. Where the
 * evidence for a break is weak, no break is made and the text stays joined.
 *
 * <p>Any posting this touches carries a warning into the review step, because a recovered structure
 * is a guess about someone else's formatting and the user is the one who can confirm it.
 */
final class ContinuousTextRecovery {

    /** Below this a line is ordinary prose. Above it, the source has plainly lost its structure. */
    private static final int CONTINUOUS_LINE_LENGTH = 300;

    /** A fragment shorter than this is not a list item; splitting there would be noise. */
    private static final int MIN_ITEM_LENGTH = 15;

    /**
     * Labels that introduce a field. Written by hand rather than derived, because these are what
     * postings actually use, and a break in front of one turns a run into a readable header block.
     */
    private static final List<String> FIELD_LABELS = List.of(
            "job description", "job title", "title", "position", "role", "team", "company",
            "employer", "organisation", "organization", "location", "employment type", "job type",
            "department", "reports to", "salary", "compensation", "pay range", "posted");

    /**
     * How a requirement or a responsibility conventionally begins. A capitalised word from this list
     * in the middle of a run is the end of one item and the start of the next.
     */
    private static final List<String> ITEM_OPENERS = List.of(
            // requirements
            "strong", "solid", "excellent", "experience", "experienced", "familiarity", "familiar",
            "proficiency", "proficient", "knowledge", "understanding", "exposure", "ability",
            "comfortable", "demonstrated", "proven", "hands-on", "bachelor", "bachelor's", "master's",
            "degree", "diploma", "fluency", "passion", "willingness", "must", "should", "you",
            // responsibilities
            "design", "develop", "build", "create", "implement", "integrate", "maintain", "own",
            "lead", "manage", "deliver", "deploy", "monitor", "document", "mentor", "ensure",
            "participate", "collaborate", "partner", "contribute", "support", "improve", "optimise",
            "optimize", "troubleshoot", "debug", "review", "test", "write", "work", "drive",
            "analyse", "analyze", "translate", "help",
            // Adverbs that start a responsibility as often as a verb does.
            "continuously", "regularly", "actively", "proactively");

    /**
     * Words that make the next capitalised word part of the same thought rather than the start of a
     * new one. Without this, "Experience with Design Systems" would be split at "Design".
     */
    private static final List<String> CONTINUATION_WORDS = List.of(
            "with", "of", "in", "on", "at", "to", "for", "and", "or", "the", "a", "an", "as", "by",
            "from", "using", "such", "including", "like", "into", "across", "via", "per", "about",
            "our", "your", "their", "its", "this", "that", "these", "those", "is", "are", "be",
            "been", "being", "we", "you", "they");

    /**
     * "About Qualifacts" is as much a heading as "About us", and no vocabulary can list every
     * employer's name. A capitalised word after "About" is the section, whatever the company is
     * called.
     */
    private static final Pattern ABOUT_SOMETHING = Pattern.compile(
            "(?<=[a-z.,;:)\\]] )About\\s+(?:the\\s+)?[A-Z][\\w&.'\u2019-]{1,30}(?=[ :])");

    private static final Pattern YEARS_OF_EXPERIENCE =
            Pattern.compile("(?<=[a-z),.] )(?=\\d+\\+?\\s*(?:-\\s*\\d+\\s*)?years?\\b)");

    private ContinuousTextRecovery() {}

    /**
     * @param text the text to parse, with breaks restored where the evidence supported it
     * @param recovered whether anything was restored, which the review step has to be told about
     */
    record Result(String text, boolean recovered) {}

    static Result apply(String text, List<String> headingPhrases) {
        if (text == null || text.isBlank()) {
            return new Result(text == null ? "" : text, false);
        }

        StringBuilder rebuilt = new StringBuilder();
        boolean recovered = false;

        for (String line : text.lines().toList()) {
            if (line.strip().length() <= CONTINUOUS_LINE_LENGTH) {
                rebuilt.append(line).append('\n');
                continue;
            }
            String split = splitLine(line.strip(), headingPhrases);
            recovered |= !split.equals(line.strip());
            rebuilt.append(split).append('\n');
        }

        return new Result(rebuilt.toString().strip(), recovered);
    }

    private static String splitLine(String line, List<String> headingPhrases) {
        String withHeadings = breakBeforeHeadings(line, headingPhrases);
        String withLabels = breakBeforeLabels(withHeadings);

        StringBuilder result = new StringBuilder();
        for (String part : withLabels.split("\n", -1)) {
            result.append(part.length() > MIN_ITEM_LENGTH * 4 ? breakBetweenItems(part) : part)
                    .append('\n');
        }
        return result.toString().strip();
    }

    /**
     * A heading in the middle of a run becomes a line of its own, which is what the parser reads.
     *
     * <p>Longest match wins, and each heading found is set aside before the rest of the line is
     * scanned again. Both matter: "Required Qualifications" has to win over "Qualifications", or the
     * word "Required" is left behind as a requirement of its own.
     */
    private static String breakBeforeHeadings(String line, List<String> headingPhrases) {
        List<String> longestFirst = headingPhrases.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();

        StringBuilder result = new StringBuilder();
        String remaining = line;

        while (true) {
            Match earliest = earliestHeading(remaining, longestFirst);
            if (earliest == null) {
                result.append(remaining);
                return result.toString();
            }
            result.append(remaining, 0, earliest.start()).append('\n')
                    .append(remaining, earliest.start(), earliest.end()).append('\n');
            remaining = remaining.substring(earliest.end()).replaceFirst("^[ :]+", "");
        }
    }

    private record Match(int start, int end) {}

    private static Match earliestHeading(String text, List<String> longestFirst) {
        Match earliest = null;

        Matcher about = ABOUT_SOMETHING.matcher(text);
        if (about.find()) {
            earliest = new Match(about.start(), about.end());
        }

        for (String phrase : longestFirst) {
            Pattern pattern = Pattern.compile(
                    "(?<=[a-z.,;:)\\]] )(" + Pattern.quote(phrase) + ")(?=[ :])",
                    Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(text);
            if (!matcher.find()) {
                continue;
            }
            // Earlier wins; at the same position the longer phrase wins, which the ordering gives.
            if (earliest == null || matcher.start() < earliest.start()) {
                earliest = new Match(matcher.start(), matcher.end());
            }
        }
        return earliest;
    }

    /** "… (Full Stack) Team: iQ AI Company: Qualifacts" is four fields, not one sentence. */
    private static String breakBeforeLabels(String line) {
        String result = line;
        for (String label : FIELD_LABELS) {
            Pattern pattern = Pattern.compile(
                    "(?<=[a-zA-Z0-9.,;:)\\]] )(" + Pattern.quote(label) + "\\s*:)",
                    Pattern.CASE_INSENSITIVE);
            result = pattern.matcher(result).replaceAll("\n$1");
        }
        return result;
    }

    /**
     * Breaks a run of list items apart at the point where one item ends and the next begins.
     *
     * <p>Three signals, all conservative: a sentence that ended, a bullet character the flattening
     * left behind, and a capitalised opener from the vocabulary above that is not preceded by a word
     * which would make it part of the same phrase.
     */
    private static String breakBetweenItems(String part) {
        String result = part
                // A bullet character survived the flattening: that is an explicit boundary.
                .replaceAll("\\s*[\u2022\u00B7\u25CF\u25AA\u2023\u2043]\\s*", "\n")
                // A finished sentence followed by a capital letter.
                .replaceAll("(?<=[a-z0-9)\\]])\\.\\s+(?=[A-Z])", ".\n");

        result = YEARS_OF_EXPERIENCE.matcher(result).replaceAll("\n");

        for (String opener : ITEM_OPENERS) {
            Pattern pattern = Pattern.compile(
                    "(?<=[a-z0-9)\\]] )(" + capitalisedForm(opener) + "\\b)(?=\\s)",
                    Pattern.UNICODE_CASE);
            Matcher matcher = pattern.matcher(result);
            StringBuilder rebuilt = new StringBuilder();
            int last = 0;
            while (matcher.find()) {
                if (precededByAContinuationWord(result, matcher.start())
                        || matcher.start() - last < MIN_ITEM_LENGTH) {
                    continue;
                }
                rebuilt.append(result, last, matcher.start()).append('\n');
                last = matcher.start();
            }
            rebuilt.append(result.substring(last));
            result = rebuilt.toString();
        }
        return trimShortFragments(result);
    }

    private static String capitalisedForm(String opener) {
        return Character.toUpperCase(opener.charAt(0)) + Pattern.quote(opener.substring(1));
    }

    private static boolean precededByAContinuationWord(String text, int position) {
        String before = text.substring(0, Math.max(0, position - 1));
        int wordStart = before.lastIndexOf(' ') + 1;
        String previousWord = before.substring(wordStart)
                .replaceAll("[^A-Za-z']", "")
                .toLowerCase(Locale.ROOT);
        return CONTINUATION_WORDS.contains(previousWord);
    }

    /** A break that produced a fragment too short to be an item was a mistake; undo it. */
    private static String trimShortFragments(String text) {
        String[] parts = text.split("\n");
        StringBuilder result = new StringBuilder(parts.length > 0 ? parts[0] : "");
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].strip().length() < MIN_ITEM_LENGTH) {
                result.append(' ').append(parts[i].strip());
            } else {
                result.append('\n').append(parts[i]);
            }
        }
        return result.toString();
    }
}
