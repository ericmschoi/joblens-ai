package com.joblens.analysis.eval;

import com.joblens.analysis.AnalysisInput;
import java.util.List;

/**
 * One scenario a candidate provider is judged on.
 *
 * <p>A case says what the provider is given and what must be true of whatever it returns. The
 * expectations are deliberately about behaviour the product depends on — did it find the
 * requirements, did it quote the resume rather than imagine it, did it keep silence out of the gap
 * column — and never about exact wording, which no two models will agree on.
 *
 * @param mustFindRequirementTerms terms from the posting that must appear in some requirement the
 *        provider decomposed; a provider that misses these is not reading the posting
 * @param mustNotAppearAsRequirement text that must never become a requirement the candidate is
 *        scored against, which is how planted instructions are caught
 * @param maxGapCount the most genuine gaps this case can honestly produce; {@code -1} for no bound
 */
public record EvaluationCase(
        String name,
        String description,
        AnalysisInput input,
        List<String> mustFindRequirementTerms,
        List<String> mustNotAppearAsRequirement,
        int maxGapCount) {

    public EvaluationCase {
        mustFindRequirementTerms = List.copyOf(mustFindRequirementTerms);
        mustNotAppearAsRequirement = List.copyOf(mustNotAppearAsRequirement);
    }

    public boolean boundsGaps() {
        return maxGapCount >= 0;
    }

    /** The case name alone: a parameterised test must not print document content into a report. */
    @Override
    public String toString() {
        return name;
    }
}
