package com.joblens.analysis.model;

import java.util.List;

/** A prose judgement, tied back to the evidence it rests on. */
public record NarrativeAssessment(
        String headline,
        String detail,
        List<String> supportingEvidenceIds,
        List<String> concerns) {

    public NarrativeAssessment {
        supportingEvidenceIds = List.copyOf(supportingEvidenceIds);
        concerns = List.copyOf(concerns);
    }
}
