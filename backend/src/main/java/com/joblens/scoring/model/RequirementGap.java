package com.joblens.scoring.model;

import com.joblens.analysis.model.Criticality;
import com.joblens.analysis.model.Importance;

/**
 * A requirement the resume does not answer, described so the user can act on it.
 *
 * <p>Used for both real gaps and unknowns; the two are kept in separate lists so the difference
 * stays visible right through to the screen.
 */
public record RequirementGap(
        String requirementId,
        String requirementText,
        Importance importance,
        Criticality criticality,
        String whyItMatters,
        String suggestedMitigation) {}
