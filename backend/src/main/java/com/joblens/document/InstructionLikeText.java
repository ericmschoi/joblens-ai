package com.joblens.document;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Detects text that is addressed to a model rather than to a reader.
 *
 * <p>Both resumes and job postings are untrusted input, and both can carry text planted to steer an
 * analysis. Matches are never removed: the user is told, and the analysis prompt fences document
 * content as evidence rather than instruction. Silently deleting the text would hide a deliberate
 * act from the person who most needs to know about it.
 */
public final class InstructionLikeText {

    /**
     * Commands aimed at an automated reader. Text matching one of these is reported and is kept out
     * of any derived structure, because a planted sentence must not become a requirement the
     * candidate is then measured against.
     */
    private static final List<Pattern> IMPERATIVE = List.of(
            Pattern.compile("ignore (all |any )?(the )?(previous|prior|above) instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("disregard (the )?(previous|prior|above)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(rate|score|grade) this (candidate|resume|applicant|posting) (as|a) ",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("you are (now )?an? (ai|assistant|language model)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(output|respond|reply|answer) (only |with )?(in )?(json|the following)",
                    Pattern.CASE_INSENSITIVE));

    /**
     * Weaker signals worth telling the user about but not worth acting on. "System prompt" is a real
     * skill on real job postings, so matching it warns rather than removes.
     */
    private static final List<Pattern> DESCRIPTIVE = List.of(
            Pattern.compile("system (prompt|message)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("as an? (ai|language model)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bprompt injection\\b", Pattern.CASE_INSENSITIVE));

    private InstructionLikeText() {}

    /** Whether the text is worth warning the user about. */
    public static boolean isPresentIn(String text) {
        return isImperative(text) || DESCRIPTIVE.stream().anyMatch(pattern -> pattern.matcher(text).find());
    }

    /**
     * Whether the text gives an instruction, as opposed to mentioning the subject. Only imperative
     * text is excluded from structured output; everything else stays exactly where the author put it.
     */
    public static boolean isImperative(String text) {
        return IMPERATIVE.stream().anyMatch(pattern -> pattern.matcher(text).find());
    }
}
