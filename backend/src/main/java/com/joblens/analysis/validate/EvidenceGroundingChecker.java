package com.joblens.analysis.validate;

import com.joblens.analysis.model.AnalysisDraft;
import com.joblens.analysis.model.EvidenceMatch;
import com.joblens.analysis.model.MatchStatus;
import com.joblens.analysis.model.RequirementAssessment;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Checks that every quote attributed to the resume is actually in the resume.
 *
 * <p>This is the deterministic answer to fabricated evidence. A model can be told not to invent
 * quotes, and mostly will not, but "mostly" is not a property you can put in front of someone
 * deciding whether to apply for a job. A quote that is not in the document is dropped, and a
 * requirement left with no surviving evidence cannot go on claiming to be matched.
 */
@Component
public class EvidenceGroundingChecker {

    private static final Logger LOG = LoggerFactory.getLogger(EvidenceGroundingChecker.class);

    /** Very short quotes match by accident; they are not worth treating as evidence either way. */
    private static final int MIN_QUOTE_LENGTH = 12;

    public Result check(AnalysisDraft draft, String resumeText) {
        String haystack = normalise(resumeText);
        List<RequirementAssessment> checked = new ArrayList<>();
        int kept = 0;
        int dropped = 0;

        for (RequirementAssessment assessment : draft.requirementAssessments()) {
            List<EvidenceMatch> grounded = new ArrayList<>();
            for (EvidenceMatch evidence : assessment.evidence()) {
                if (isGrounded(evidence.resumeQuote(), haystack)) {
                    grounded.add(evidence.withGrounded(true));
                    kept++;
                } else {
                    dropped++;
                }
            }
            checked.add(withoutUnsupportedClaim(assessment, grounded));
        }

        if (dropped > 0) {
            LOG.warn("dropped ungrounded evidence count={} kept={}", dropped, kept);
        }
        return new Result(draft.withRequirementAssessments(checked), kept, dropped);
    }

    /**
     * A requirement whose evidence all failed the check cannot still be a match. It becomes unknown
     * rather than a gap, because a fabricated quote says nothing about what the candidate has done.
     */
    private static RequirementAssessment withoutUnsupportedClaim(RequirementAssessment assessment,
            List<EvidenceMatch> grounded) {

        boolean claimedAMatch = assessment.status() == MatchStatus.STRONG_MATCH
                || assessment.status() == MatchStatus.PARTIAL_MATCH;
        boolean lostAllEvidence = grounded.isEmpty() && !assessment.evidence().isEmpty();

        RequirementAssessment withEvidence = assessment.withEvidence(grounded);
        if (claimedAMatch && lostAllEvidence) {
            return withEvidence.withStatus(MatchStatus.UNKNOWN,
                    "Reported as unknown: the supporting quotes could not be found in the resume, so "
                            + "this match is not supported by the document.");
        }
        return withEvidence;
    }

    private static boolean isGrounded(String quote, String haystack) {
        if (quote == null || quote.isBlank()) {
            return false;
        }
        String needle = normalise(quote);
        if (needle.length() < MIN_QUOTE_LENGTH) {
            return false;
        }
        return haystack.contains(needle);
    }

    /** Whitespace and case differ harmlessly between a document and a quotation of it. */
    private static String normalise(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT);
    }

    /**
     * @param groundingFailureRatio how much of the evidence had to be discarded, which feeds the
     *        confidence the user is shown
     */
    public record Result(AnalysisDraft draft, int groundedCount, int droppedCount) {

        public double groundingFailureRatio() {
            int total = groundedCount + droppedCount;
            return total == 0 ? 0.0 : (double) droppedCount / total;
        }
    }
}
