package com.joblens.jobposting;

import com.joblens.document.ExtractionWarning;
import com.joblens.document.ReviewStatus;
import com.joblens.document.WarningCode;
import java.util.List;
import java.util.Set;

/**
 * Decides whether the structured qualification lists may be treated as the complete requirements.
 *
 * <p>Scoring must ask this rather than read the lists and assume what is not in them is not
 * required. A posting whose headings the parser did not recognise looks identical to a posting with
 * no requirements, and only one of those should produce an easy score.
 */
public final class JobPostingReliability {

    private static final Set<WarningCode> STRUCTURE_IS_UNCERTAIN = Set.of(
            WarningCode.NO_QUALIFICATION_SECTIONS_DETECTED,
            WarningCode.REQUIRED_AND_PREFERRED_NOT_SEPARATED,
            WarningCode.NO_SECTIONS_DETECTED,
            WarningCode.TEXT_TRUNCATED);

    private JobPostingReliability() {}

    public static RequirementSourcePolicy policyFor(ReviewStatus status, List<ExtractionWarning> warnings) {
        if (status != ReviewStatus.CONFIRMED) {
            return RequirementSourcePolicy.FULL_TEXT_FALLBACK;
        }
        boolean uncertain = warnings.stream()
                .anyMatch(warning -> STRUCTURE_IS_UNCERTAIN.contains(warning.code()));
        return uncertain ? RequirementSourcePolicy.FULL_TEXT_FALLBACK : RequirementSourcePolicy.STRUCTURED_SECTIONS;
    }
}
