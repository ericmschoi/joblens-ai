package com.joblens.analysis.model;

import java.util.List;

/**
 * How to present experience the candidate already has.
 *
 * <p>Every field is about rearranging, emphasising or rewording what the resume says. None of them
 * may introduce a technology, a metric, an employer or an achievement that is not already there.
 */
public record ResumePositioningAdvice(
        List<String> reorderSuggestions,
        List<String> emphasisSuggestions,
        List<TerminologyAlignment> terminologyAlignment,
        List<String> deemphasizeSuggestions,
        List<FaithfulRewrite> faithfulRewrites) {

    public record TerminologyAlignment(String resumeTerm, String postingTerm, String rationale) {}

    public record FaithfulRewrite(String before, String after) {}

    public ResumePositioningAdvice {
        reorderSuggestions = List.copyOf(reorderSuggestions);
        emphasisSuggestions = List.copyOf(emphasisSuggestions);
        terminologyAlignment = List.copyOf(terminologyAlignment);
        deemphasizeSuggestions = List.copyOf(deemphasizeSuggestions);
        faithfulRewrites = List.copyOf(faithfulRewrites);
    }
}
