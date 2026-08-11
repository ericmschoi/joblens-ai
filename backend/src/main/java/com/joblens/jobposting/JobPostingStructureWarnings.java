package com.joblens.jobposting;

import com.joblens.document.ExtractionWarning;
import com.joblens.document.WarningCode;
import com.joblens.jobposting.model.JobPosting;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Quality signals derivable from a job posting alone.
 *
 * <p>Recomputed after the user edits, so a posting whose sections they fixed stops carrying stale
 * warnings, and one they left broken keeps carrying honest ones.
 */
final class JobPostingStructureWarnings {

    private static final Set<WarningCode> STRUCTURAL = Set.of(
            WarningCode.NO_QUALIFICATION_SECTIONS_DETECTED,
            WarningCode.NO_RESPONSIBILITIES_DETECTED);

    private JobPostingStructureWarnings() {}

    static boolean isStructural(WarningCode code) {
        return STRUCTURAL.contains(code);
    }

    static List<ExtractionWarning> forPosting(JobPosting posting) {
        List<ExtractionWarning> warnings = new ArrayList<>();
        if (posting.requiredQualifications().isEmpty() && posting.preferredQualifications().isEmpty()) {
            warnings.add(ExtractionWarning.of(WarningCode.NO_QUALIFICATION_SECTIONS_DETECTED));
        }
        if (posting.responsibilities().isEmpty()) {
            warnings.add(ExtractionWarning.of(WarningCode.NO_RESPONSIBILITIES_DETECTED));
        }
        return warnings;
    }

}
