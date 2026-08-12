package com.joblens.analysis.model;

/**
 * One piece of resume evidence, tied to the requirement it supports.
 *
 * @param resumeQuote text taken from the resume. Checked against the submitted document before the
 *        result is used, so a quote the model invented cannot reach the user.
 * @param sourceLocator where in the resume it came from, so a reader can go and look
 * @param grounded set by the server, never by the model
 */
public record EvidenceMatch(
        String requirementId,
        Importance importance,
        MatchStatus status,
        EvidenceRelation relation,
        EvidenceStrength strength,
        String resumeQuote,
        String sourceLocator,
        String rationale,
        boolean grounded) {

    public EvidenceMatch withGrounded(boolean value) {
        return new EvidenceMatch(requirementId, importance, status, relation, strength, resumeQuote,
                sourceLocator, rationale, value);
    }
}
