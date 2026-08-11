package com.joblens.resume;

import com.joblens.document.ExtractionWarning;
import com.joblens.document.WarningCode;
import com.joblens.resume.model.CandidateProfile;
import com.joblens.resume.model.DateRange;
import com.joblens.resume.model.WorkExperience;
import java.util.ArrayList;
import java.util.List;

/**
 * Quality signals that can be derived from a candidate profile alone.
 *
 * <p>Both extraction and confirmation use these, so a profile the user edited by hand is held to the
 * same standard as one the parser produced. Correcting a resume by deleting the parts that failed to
 * parse should not silently make the result look reliable.
 */
final class ResumeStructureWarnings {

    /** Warnings that describe the structured view and must be recomputed after the user edits it. */
    private static final java.util.Set<WarningCode> STRUCTURAL = java.util.Set.of(
            WarningCode.NO_ROLES_DETECTED, WarningCode.LOW_CONFIDENCE_STRUCTURE);

    private ResumeStructureWarnings() {}

    static boolean isStructural(WarningCode code) {
        return STRUCTURAL.contains(code);
    }

    static List<ExtractionWarning> forProfile(CandidateProfile profile) {
        List<ExtractionWarning> warnings = new ArrayList<>();

        if (profile.workExperiences().isEmpty()) {
            warnings.add(ExtractionWarning.of(WarningCode.NO_ROLES_DETECTED));
        }

        long weakEntries = profile.workExperiences().stream().filter(ResumeStructureWarnings::isWeak).count();
        if (weakEntries > 0) {
            warnings.add(ExtractionWarning.counted(WarningCode.LOW_CONFIDENCE_STRUCTURE, (int) weakEntries));
        }
        return warnings;
    }

    private static boolean isWeak(WorkExperience experience) {
        return isBlank(experience.title())
                || isBlank(experience.company())
                || experience.bullets().isEmpty()
                || experience.dates().parseConfidence() != DateRange.Confidence.HIGH;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
