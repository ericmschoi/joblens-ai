package com.joblens.jobposting;

import com.joblens.document.ExtractionWarning;
import com.joblens.document.InstructionLikeText;
import com.joblens.document.WarningCode;
import com.joblens.jobposting.model.JobPosting;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Reads a job posting's headings and turns the text under them into structured lists.
 *
 * <p>The distinction this parser exists to preserve is required versus preferred. Required
 * qualifications are weighted far more heavily and are the only ones that can cap a score, so
 * getting the split wrong changes the answer. When a posting does not make the split itself, the
 * parser takes the stricter reading — everything is required — and says so, rather than guessing in
 * the direction that flatters the candidate.
 */
@Component
public class JobPostingParser {

    private enum Kind {
        HEADER, ABOUT, RESPONSIBILITIES, REQUIRED, AMBIGUOUS_QUALIFICATIONS, PREFERRED, COMPENSATION, OTHER
    }

    private static final Map<Kind, List<String>> HEADINGS = Map.of(
            Kind.RESPONSIBILITIES, List.of("responsibilities", "key responsibilities", "what you'll do",
                    "what you will do", "what you'll be doing", "the role", "your impact", "duties",
                    "day to day", "in this role", "role responsibilities"),
            Kind.REQUIRED, List.of("required qualifications", "minimum qualifications", "basic qualifications",
                    "requirements", "required skills", "required experience", "must have", "must-haves",
                    "what you'll need", "what you need", "what we're looking for",
                    // Workday postings commonly write the qualifier after the noun.
                    "qualifications required", "skills required", "experience required"),
            Kind.PREFERRED, List.of("preferred qualifications", "preferred", "nice to have", "nice-to-haves",
                    "nice to haves", "bonus points", "bonus", "desired qualifications", "assets",
                    "it's a plus", "pluses", "good to have", "qualifications preferred",
                    "nice to haves", "preferred skills", "preferred experience"),
            Kind.AMBIGUOUS_QUALIFICATIONS, List.of("qualifications", "skills", "skills and experience",
                    "experience", "what you bring", "who you are", "about you", "you have",
                    "qualifications and fit", "requirements and qualifications", "technical skills",
                    "what you bring to the table", "your experience", "your background",
                    "who you'll be", "who you will be", "experience and skills"),
            Kind.ABOUT, List.of("about us", "about the company", "about the role", "about the team",
                    "who we are", "overview", "the opportunity", "company overview", "why join",
                    "why join us", "what we offer", "what you'll work on", "what you will work on",
                    "benefits", "perks", "perks and benefits", "how to apply"),
            Kind.COMPENSATION, List.of("compensation", "salary", "pay range", "salary range",
                    "compensation and benefits"));

    private static final int MAX_HEADING_LENGTH = 60;
    private static final int MAX_HEADER_BLOCK_LINES = 8;
    /** A title, company or location longer than this is a sentence that has been misread as one. */
    private static final int MAX_FIELD_LENGTH = 120;

    /** {@code Label: value}, with a label short enough to be a label. */
    private static final Pattern LABELLED_FIELD =
            Pattern.compile("^([A-Za-z][A-Za-z /]{1,24}?)\\s*:\\s*(.*)$");

    /** Labels that introduce the posting rather than name a field, so they are never the title. */
    private static final List<String> BARE_LABELS = List.of(
            "job description", "description", "job title", "title", "position", "role", "overview",
            "job details", "about the job", "about this role", "summary", "job summary", "job post");

    /** Single words that are only ever a heading in a job posting. */
    private static final List<String> SAFE_SINGLE_WORD_HEADINGS = List.of(
            "responsibilities", "requirements", "qualifications", "benefits", "perks");

    /** Phrases that read as a heading on their own line but as prose in the middle of one. */
    private static final List<String> AMBIGUOUS_INLINE_PHRASES = List.of(
            "the role", "in this role", "day to day", "must have", "you have", "about you");

    /** Words that make a value a role rather than an employer. */
    private static final List<String> ROLE_WORDS = List.of(
            "engineer", "developer", "designer", "manager", "analyst", "architect", "scientist",
            "consultant", "specialist", "administrator", "technician", "intern", "junior", "senior",
            "intermediate", "staff", "principal", "lead", "full stack", "full-stack", "frontend",
            "front-end", "backend", "back-end", "devops", "programmer");

    private static final Pattern BULLET = Pattern.compile("^-\\s+");
    private static final Pattern STRONG_SEPARATOR =
            Pattern.compile("\\s*(?:\\u2014|\\u2013|\\||\\u00B7|\\s{3,}|,\\s+(?=[A-Z][a-z]+,\\s*[A-Z]{2}\\b))\\s*");
    private static final Pattern EMPLOYMENT_TYPE = Pattern.compile(
            "\\b(full[\\s-]?time|part[\\s-]?time|contract|internship|intern|temporary|permanent|freelance)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPENSATION = Pattern.compile(
            "(?:[$\\u20AC\\u00A3]|\\b(?:CAD|USD|EUR|GBP)\\s*)\\s?[\\d][\\d,.]*(?:\\s*[kK])?"
                    + "(?:\\s*(?:-|\\u2013|\\u2014|to)\\s*(?:[$\\u20AC\\u00A3]|\\b(?:CAD|USD|EUR|GBP)\\s*)?"
                    + "[\\d][\\d,.]*(?:\\s*[kK])?)?"
                    + "(?:\\s*(?:per|/|a)\\s*(?:year|annum|hour|hr|month))?");

    /**
     * Phrases that mark an item as optional. Only applied inside a section whose heading did not
     * state which it was: when an employer writes "Required Qualifications", they have already said
     * what they mean, and demoting an item there would make the candidate look better than they are.
     */
    private static final List<String> OPTIONAL_MARKERS = List.of(
            "(preferred)", "(nice to have)", "(a plus)", "(bonus)", "(optional)",
            "nice to have", "is a plus", "are a plus", "bonus points", "would be an asset",
            "is an asset", "preferred but not required");

    /**
     * Heading phrases that are safe to look for inside a run of text.
     *
     * <p>Not the whole vocabulary. "Experience", "Skills" and "Overview" are headings at the start
     * of a line and ordinary words everywhere else — "Experience with Docker" is a requirement, not
     * a section — so a single word only qualifies if it has no other common use in a posting. The
     * words left out are still recognised as headings when they genuinely start a line.
     */
    static List<String> inlineHeadingPhrases() {
        return HEADINGS.values().stream()
                .flatMap(List::stream)
                .filter(phrase -> phrase.contains(" ") || SAFE_SINGLE_WORD_HEADINGS.contains(phrase))
                .filter(phrase -> !AMBIGUOUS_INLINE_PHRASES.contains(phrase))
                .toList();
    }

    public ParsedJobPosting parse(String normalizedText) {
        // A posting that arrived as one continuous run has to get its line breaks back before a
        // line-based parser can read it. Nothing is added or removed; see ContinuousTextRecovery.
        ContinuousTextRecovery.Result recovery =
                ContinuousTextRecovery.apply(normalizedText, inlineHeadingPhrases());

        List<String> lines = recovery.text().lines().toList();
        List<Block> blocks = splitIntoBlocks(lines);

        List<String> headerLines = blocks.stream()
                .filter(block -> block.kind() == Kind.HEADER)
                .findFirst()
                .map(Block::lines)
                .orElse(List.of());

        List<String> responsibilities = new ArrayList<>();
        List<String> required = new ArrayList<>();
        List<String> preferred = new ArrayList<>();
        List<JobPosting.Section> other = new ArrayList<>();

        boolean sawAmbiguousQualifications = false;
        boolean sawExplicitPreferred = false;

        for (Block block : blocks) {
            List<String> items = toItems(block.lines());
            switch (block.kind()) {
                case RESPONSIBILITIES -> responsibilities.addAll(items);
                case REQUIRED -> required.addAll(items);
                case PREFERRED -> {
                    sawExplicitPreferred = true;
                    preferred.addAll(items);
                }
                case AMBIGUOUS_QUALIFICATIONS -> {
                    sawAmbiguousQualifications = true;
                    for (String item : items) {
                        if (looksOptional(item)) {
                            preferred.add(item);
                        } else {
                            required.add(item);
                        }
                    }
                }
                case HEADER -> { /* parsed separately below */ }
                default -> {
                    if (!items.isEmpty()) {
                        other.add(new JobPosting.Section(block.heading(), items));
                    }
                }
            }
        }

        Header header = parseHeader(headerLines);
        String compensation = header.compensation() != null
                ? header.compensation()
                : compensationFrom(blocks);

        JobPosting posting = new JobPosting(
                header.title(), header.company(), header.location(), header.employmentType(), compensation,
                responsibilities, required, preferred, other, null);

        return new ParsedJobPosting(posting, warningsFor(blocks, posting, normalizedText,
                sawAmbiguousQualifications, sawExplicitPreferred, recovery.recovered()));
    }

    // --- sectioning ------------------------------------------------------------------------------

    private record Block(Kind kind, String heading, List<String> lines) {}

    private static List<Block> splitIntoBlocks(List<String> lines) {
        List<Block> blocks = new ArrayList<>();
        Kind currentKind = Kind.HEADER;
        String currentHeading = null;
        List<String> current = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Optional<Kind> heading = headingKind(line, i + 1 < lines.size() ? lines.get(i + 1) : null);
            if (heading.isPresent()) {
                blocks.add(new Block(currentKind, currentHeading, List.copyOf(current)));
                current = new ArrayList<>();
                currentKind = heading.get();
                currentHeading = line.strip().replaceAll("[:\\s]+$", "");
                continue;
            }
            if (!line.isBlank()) {
                current.add(line);
            }
        }
        blocks.add(new Block(currentKind, currentHeading, List.copyOf(current)));
        return blocks;
    }

    private static Optional<Kind> headingKind(String line, String nextLine) {
        String stripped = line.strip();
        if (stripped.isEmpty() || stripped.length() > MAX_HEADING_LENGTH || BULLET.matcher(stripped).find()) {
            return Optional.empty();
        }

        // Boards write "Qualifications & Fit" as often as "Qualifications and Fit".
        String candidate = stripped.replaceAll("[:\\s]+$", "").toLowerCase(Locale.ROOT)
                .replace(" & ", " and ");
        for (Map.Entry<Kind, List<String>> entry : HEADINGS.entrySet()) {
            if (entry.getValue().contains(candidate)) {
                return Optional.of(entry.getKey());
            }
        }

        // "About Qualifacts" names a section the same way "About us" does, and no list can hold
        // every employer's name.
        if (candidate.matches("about\\s+\\S+(\\s+\\S+)?")) {
            return Optional.of(Kind.ABOUT);
        }

        // An unlisted short label immediately above a bullet list is still a heading. Recognising it
        // keeps "Benefits" or "How to apply" out of the qualification lists.
        boolean introducesAList = nextLine != null && BULLET.matcher(nextLine.strip()).find();
        boolean looksLikeALabel = !stripped.endsWith(".") && !stripped.endsWith(",")
                && stripped.split("\\s+").length <= 6;
        return introducesAList && looksLikeALabel ? Optional.of(Kind.OTHER) : Optional.empty();
    }

    /**
     * Turns lines into items, re-joining lines that are visibly a continuation of the one above.
     *
     * <p>Lines that issue instructions to an automated reader are left out of the structured lists.
     * They remain in the raw text and raise a warning, but a sentence planted in a posting must not
     * become a requirement that a candidate is then scored against.
     */
    private static List<String> toItems(List<String> lines) {
        List<String> items = new ArrayList<>();
        boolean previousLineWasDropped = false;

        for (String line : lines) {
            String stripped = line.strip();
            if (stripped.isEmpty()) {
                previousLineWasDropped = false;
                continue;
            }

            boolean isBullet = BULLET.matcher(stripped).find();
            String text = isBullet ? BULLET.matcher(stripped).replaceFirst("").strip() : stripped;
            boolean looksLikeAContinuation = !isBullet && Character.isLowerCase(text.charAt(0));

            // A dropped instruction can wrap onto the next line. That remainder has to go with it,
            // or it would be appended to whichever legitimate requirement came before.
            if (InstructionLikeText.isImperative(stripped) || (previousLineWasDropped && looksLikeAContinuation)) {
                previousLineWasDropped = true;
                continue;
            }
            previousLineWasDropped = false;

            if (looksLikeAContinuation && !items.isEmpty()) {
                items.set(items.size() - 1, items.getLast() + " " + text);
            } else {
                items.add(text);
            }
        }
        return items;
    }

    private static boolean looksOptional(String item) {
        String lower = item.toLowerCase(Locale.ROOT);
        return OPTIONAL_MARKERS.stream().anyMatch(lower::contains);
    }

    // --- header ----------------------------------------------------------------------------------

    private record Header(String title, String company, String location, String employmentType,
                          String compensation) {}

    /**
     * Reads the block above the first recognised heading.
     *
     * <p>Position is the weakest signal a posting offers and it is used last. A label the posting
     * wrote itself — "Company: Qualifacts" — is what it says it is, so labels win outright. Only
     * when a posting labels nothing does this fall back to reading the first lines by position, and
     * even then it refuses to fill a field it cannot support: a wrong company is worse than a blank
     * one, because the review step shows a blank as something to fix and a wrong value as a fact.
     */
    private static Header parseHeader(List<String> headerLines) {
        List<String> lines = headerLines.size() > MAX_HEADER_BLOCK_LINES
                ? headerLines.subList(0, MAX_HEADER_BLOCK_LINES)
                : headerLines;
        if (lines.isEmpty()) {
            return new Header(null, null, null, null, null);
        }

        Map<String, String> labelled = labelledFields(lines);
        String title = firstOf(labelled, "job title", "title", "position", "role");
        String company = firstOf(labelled, "company", "employer", "organisation", "organization");
        String location = firstOf(labelled, "location");
        String employmentType = firstOf(labelled, "employment type", "job type");

        // "Job Description: Senior Engineer" names the role; "Job Description:" alone introduces it.
        if (title == null) {
            title = firstOf(labelled, "job description", "description");
        }
        if (title == null) {
            title = titleByPosition(lines);
        }
        if (company == null && location == null) {
            Header positional = companyAndLocationByPosition(lines);
            company = positional.company();
            location = positional.location();
        }

        String block = String.join("\n", lines);
        Matcher employment = EMPLOYMENT_TYPE.matcher(block);
        Matcher compensation = COMPENSATION.matcher(block);

        return new Header(
                title,
                company,
                location,
                employmentType != null ? employmentType
                        : employment.find() ? employment.group().strip() : null,
                compensation.find() ? compensation.group().strip() : null);
    }

    /** Every {@code Label: value} in the header block, lower-cased label to value. */
    private static Map<String, String> labelledFields(List<String> lines) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String line : lines) {
            Matcher matcher = LABELLED_FIELD.matcher(line.strip());
            if (!matcher.matches()) {
                continue;
            }
            String label = matcher.group(1).strip().toLowerCase(Locale.ROOT);
            String value = matcher.group(2).strip();
            if (!value.isEmpty() && value.length() <= MAX_FIELD_LENGTH) {
                fields.putIfAbsent(label, value);
            }
        }
        return fields;
    }

    private static String firstOf(Map<String, String> fields, String... labels) {
        for (String label : labels) {
            String value = fields.get(label);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * The first line that is a plausible job title. A bare label is skipped rather than reported as
     * the role, and a line long enough to be a paragraph is not a title at all.
     */
    private static String titleByPosition(List<String> lines) {
        for (String line : lines) {
            String candidate = line.strip().replaceAll("[:\\s]+$", "");
            if (candidate.isEmpty() || isBareLabel(candidate)) {
                continue;
            }
            // A run that lost its line breaks can leave a whole paragraph here. Take the first
            // sentence if that is title-sized, and otherwise leave the field for the user to fill.
            if (candidate.length() > MAX_FIELD_LENGTH) {
                String firstSentence = candidate.split("(?<=[.!?])\\s+", 2)[0].strip();
                return firstSentence.length() <= MAX_FIELD_LENGTH ? firstSentence : null;
            }
            return candidate;
        }
        return null;
    }

    private static boolean isBareLabel(String candidate) {
        String lower = candidate.toLowerCase(Locale.ROOT);
        return BARE_LABELS.contains(lower);
    }

    /**
     * The "Company — City, Province" line some postings put under the title.
     *
     * <p>Accepted only when one side actually looks like a location. Splitting on a dash without
     * that check is how "Junior–Intermediate Software Engineer (Full Stack)" became a company called
     * "Junior" in a place called "Intermediate Software Engineer".
     */
    private static Header companyAndLocationByPosition(List<String> lines) {
        if (lines.size() < 2) {
            return new Header(null, null, null, null, null);
        }
        String line = lines.get(1).strip();
        if (line.length() > MAX_FIELD_LENGTH) {
            return new Header(null, null, null, null, null);
        }

        List<String> parts = List.of(STRONG_SEPARATOR.split(line)).stream()
                .map(String::strip)
                .filter(part -> !part.isEmpty())
                .toList();

        if (parts.isEmpty()) {
            return new Header(null, null, null, null, null);
        }
        if (parts.size() == 1) {
            String only = parts.getFirst();
            if (looksLikeALocation(only)) {
                return new Header(null, null, only, null, null);
            }
            return new Header(null, looksLikeARole(only) ? null : only, null, null, null);
        }

        String first = parts.getFirst();
        String second = parts.get(1);

        if (looksLikeALocation(first)) {
            return new Header(null, null, first, null, null);
        }
        if (looksLikeALocation(second)) {
            // Only now is the first part a company: something else on the line is a place.
            return new Header(null, looksLikeARole(first) ? null : first, second, null, null);
        }
        // Neither part is a place. This is a title that happened to contain a dash, not a header.
        return new Header(null, null, null, null, null);
    }

    /**
     * Whether a value reads as a job title rather than an employer. A dash inside a role — "Junior –
     * Intermediate", "Engineer II - Platform" — is the common way this parser used to be fooled.
     */
    private static boolean looksLikeARole(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return ROLE_WORDS.stream().anyMatch(lower::contains);
    }

    private static boolean looksLikeALocation(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("remote") || lower.contains("hybrid") || lower.contains("on-site")
                || lower.contains("onsite") || value.matches(".*,\\s*[A-Z]{2}\\b.*");
    }

    private static String compensationFrom(List<Block> blocks) {
        return blocks.stream()
                .filter(block -> block.kind() == Kind.COMPENSATION)
                .flatMap(block -> block.lines().stream())
                .map(COMPENSATION::matcher)
                .filter(Matcher::find)
                .map(Matcher::group)
                .map(String::strip)
                .findFirst()
                .orElse(null);
    }

    // --- warnings --------------------------------------------------------------------------------

    private static List<ExtractionWarning> warningsFor(List<Block> blocks, JobPosting posting,
            String text, boolean sawAmbiguousQualifications, boolean sawExplicitPreferred,
            boolean structureRecovered) {

        List<ExtractionWarning> warnings = new ArrayList<>();

        // Said first, because it explains the state of everything below it.
        if (structureRecovered) {
            warnings.add(ExtractionWarning.of(WarningCode.STRUCTURE_RECOVERED_FROM_CONTINUOUS_TEXT));
        }
        boolean anyHeadingRecognised = blocks.stream().anyMatch(block -> block.kind() != Kind.HEADER);
        if (!anyHeadingRecognised) {
            warnings.add(ExtractionWarning.of(WarningCode.NO_POSTING_SECTIONS_DETECTED));
        }
        if (posting.requiredQualifications().isEmpty() && posting.preferredQualifications().isEmpty()) {
            warnings.add(ExtractionWarning.of(WarningCode.NO_QUALIFICATION_SECTIONS_DETECTED));
        }
        if (posting.responsibilities().isEmpty()) {
            warnings.add(ExtractionWarning.of(WarningCode.NO_RESPONSIBILITIES_DETECTED));
        }
        // An ambiguous heading is only a problem when nothing else in the posting drew the line.
        if (sawAmbiguousQualifications && !sawExplicitPreferred) {
            warnings.add(ExtractionWarning.of(WarningCode.REQUIRED_AND_PREFERRED_NOT_SEPARATED));
        }
        if (InstructionLikeText.isPresentIn(text)) {
            warnings.add(ExtractionWarning.of(WarningCode.POSSIBLE_EMBEDDED_INSTRUCTIONS));
        }
        return List.copyOf(new LinkedHashMap<>(indexed(warnings)).values());
    }

    private static Map<WarningCode, ExtractionWarning> indexed(List<ExtractionWarning> warnings) {
        Map<WarningCode, ExtractionWarning> unique = new LinkedHashMap<>();
        warnings.forEach(warning -> unique.putIfAbsent(warning.code(), warning));
        return unique;
    }
}
