package com.joblens.resume;

import com.joblens.document.ExtractionWarning;
import com.joblens.document.WarningCode;
import java.util.List;
import java.util.Set;

/**
 * Decides whether absent evidence may be treated as a real gap.
 *
 * <p>This exists so that the rule survives contact with the scoring code written later. Heuristic
 * parsing that misses a role produces the same observable state as a candidate who never held that
 * role, and without this distinction a parser bug would quietly cap someone's score. Scoring must
 * ask this class rather than inspect a profile and assume what it does not find is not there.
 */
public final class ResumeEvidenceReliability {

    /**
     * Warnings that mean the structured view may be missing content that is present in the document.
     * Any of them forces absent evidence to be reported as unknown rather than missing.
     */
    private static final Set<WarningCode> STRUCTURE_IS_UNCERTAIN = Set.of(
            WarningCode.NO_SECTIONS_DETECTED,
            WarningCode.NO_ROLES_DETECTED,
            WarningCode.UNASSIGNED_TEXT_BLOCKS,
            WarningCode.LOW_CONFIDENCE_STRUCTURE,
            WarningCode.TEXT_TRUNCATED,
            WarningCode.POSSIBLE_MULTI_COLUMN,
            WarningCode.BROKEN_WORDS,
            WarningCode.LOW_TEXT_DENSITY);

    private ResumeEvidenceReliability() {}

    public static EvidenceAbsencePolicy policyFor(ResumeReviewStatus status, List<ExtractionWarning> warnings) {
        if (status != ResumeReviewStatus.CONFIRMED) {
            return EvidenceAbsencePolicy.MUST_BE_UNKNOWN;
        }
        boolean uncertain = warnings.stream()
                .anyMatch(warning -> STRUCTURE_IS_UNCERTAIN.contains(warning.code()));
        return uncertain ? EvidenceAbsencePolicy.MUST_BE_UNKNOWN : EvidenceAbsencePolicy.MAY_BE_GAP;
    }
}
