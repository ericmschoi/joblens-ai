package com.joblens.jobposting;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Cleans pasted job-posting text without changing what it says.
 *
 * <p>Pasting from a web page brings non-breaking spaces, assorted bullet glyphs, zero-width
 * characters and runs of blank lines. All of that is normalized, but line structure is preserved
 * exactly: in a job posting the line break between two bullets is the difference between one
 * requirement and two, and merging them would change how many requirements the analysis counts.
 */
@Component
public class JobPostingTextNormalizer {

    private static final Pattern ZERO_WIDTH = Pattern.compile("[\\u200B-\\u200D\\uFEFF]");
    private static final Pattern BULLET_GLYPH =
            Pattern.compile("^\\s*[\\u2022\\u25CF\\u25AA\\u25E6\\u2023\\u2043\\u00B7\\u2219\\u30FB\\u2212\\u2013\\u2014*]\\s+");
    private static final Pattern TRAILING_SPACE = Pattern.compile("[ \\t]+$");
    private static final Pattern EXCESS_BLANK_LINES = Pattern.compile("\\n{3,}");

    /** The canonical bullet marker used downstream, so the parser only has one shape to recognise. */
    public static final String BULLET = "- ";

    public String normalize(String text) {
        String unified = text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace(' ', ' ')
                .replace('‘', '\'').replace('’', '\'')
                .replace('“', '"').replace('”', '"');

        unified = ZERO_WIDTH.matcher(unified).replaceAll("");

        List<String> lines = unified.lines()
                .map(line -> TRAILING_SPACE.matcher(line).replaceAll(""))
                .map(JobPostingTextNormalizer::normalizeBullet)
                .toList();

        String joined = String.join("\n", lines);
        return EXCESS_BLANK_LINES.matcher(joined).replaceAll("\n\n").strip();
    }

    private static String normalizeBullet(String line) {
        return BULLET_GLYPH.matcher(line).find() ? BULLET_GLYPH.matcher(line).replaceFirst(BULLET) : line;
    }
}
