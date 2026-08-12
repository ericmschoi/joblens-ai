package com.joblens.analysis.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

/**
 * One atomic requirement from the posting and how the resume answers it.
 *
 * <p>This is the unit the deterministic scorer works from. The model decides what the requirement
 * is, how important it is and whether the resume meets it; it never decides what that is worth.
 *
 * @param alternativeGroupId requirements that satisfy each other — "Java, C# or Go" — share a group,
 *        and the scorer counts the group once rather than as several separate gaps
 * @param primaryCategory which rated category this requirement contributes to
 */
public record RequirementAssessment(
        String id,
        String requirementText,
        RequirementKind kind,
        Importance importance,
        Criticality criticality,
        String alternativeGroupId,
        CategoryName primaryCategory,
        MatchStatus status,
        EvidenceRelation relation,
        EvidenceStrength evidenceStrength,
        List<EvidenceMatch> evidence,
        String rationale) {

    public RequirementAssessment {
        evidence = List.copyOf(evidence);
    }

    /** Derived, so it must not appear in the JSON contract a provider is validated against. */
    @JsonIgnore
    public boolean isCoreRequirement() {
        return importance == Importance.REQUIRED && criticality == Criticality.CORE;
    }

    public RequirementAssessment withEvidence(List<EvidenceMatch> replacement) {
        return new RequirementAssessment(id, requirementText, kind, importance, criticality,
                alternativeGroupId, primaryCategory, status, relation, evidenceStrength, replacement, rationale);
    }

    public RequirementAssessment withStatus(MatchStatus replacement, String why) {
        return new RequirementAssessment(id, requirementText, kind, importance, criticality,
                alternativeGroupId, primaryCategory, replacement, relation, evidenceStrength, evidence,
                why == null ? rationale : why);
    }
}
