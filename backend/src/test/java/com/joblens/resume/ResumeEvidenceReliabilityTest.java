package com.joblens.resume;

import static org.assertj.core.api.Assertions.assertThat;

import com.joblens.document.ReviewStatus;

import com.joblens.document.ExtractionWarning;
import com.joblens.document.WarningCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The rule these tests protect: a parser that missed something must never look like a candidate who
 * lacks it. Scoring may only treat absent evidence as a real gap when the representation has been
 * reviewed and carries no structural uncertainty.
 */
class ResumeEvidenceReliabilityTest {

    @Test
    void anUnreviewedResumeCanNeverProduceADefiniteGap() {
        EvidenceAbsencePolicy policy = ResumeEvidenceReliability.policyFor(
                ReviewStatus.REVIEW_REQUIRED, List.of());

        assertThat(policy)
                .as("extraction output is a machine's guess, so nothing missing from it is confirmed missing")
                .isEqualTo(EvidenceAbsencePolicy.MUST_BE_UNKNOWN);
    }

    @Test
    void aCleanConfirmedResumeAllowsAbsentEvidenceToCountAsAGap() {
        EvidenceAbsencePolicy policy = ResumeEvidenceReliability.policyFor(
                ReviewStatus.CONFIRMED, List.of());

        assertThat(policy).isEqualTo(EvidenceAbsencePolicy.MAY_BE_GAP);
    }

    @ParameterizedTest
    @EnumSource(value = WarningCode.class, names = {
            "NO_SECTIONS_DETECTED",
            "NO_ROLES_DETECTED",
            "UNASSIGNED_TEXT_BLOCKS",
            "LOW_CONFIDENCE_STRUCTURE",
            "TEXT_TRUNCATED",
            "POSSIBLE_MULTI_COLUMN",
            "BROKEN_WORDS",
            "LOW_TEXT_DENSITY"
    })
    void structuralUncertaintySurvivesConfirmationAndStillBlocksGaps(WarningCode code) {
        EvidenceAbsencePolicy policy = ResumeEvidenceReliability.policyFor(
                ReviewStatus.CONFIRMED, List.of(ExtractionWarning.of(code)));

        assertThat(policy)
                .as("%s means content may be missing from the structured view, not from the career", code)
                .isEqualTo(EvidenceAbsencePolicy.MUST_BE_UNKNOWN);
    }

    @Test
    void aWarningAboutHowTheFileWasReadDoesNotBlockGapsOnItsOwn() {
        EvidenceAbsencePolicy policy = ResumeEvidenceReliability.policyFor(
                ReviewStatus.CONFIRMED,
                List.of(ExtractionWarning.of(WarningCode.ENCRYPTED_BUT_READABLE),
                        ExtractionWarning.of(WarningCode.REPEATED_HEADER_FOOTER)));

        assertThat(policy)
                .as("these describe the file, not gaps in the structured view")
                .isEqualTo(EvidenceAbsencePolicy.MAY_BE_GAP);
    }

    @Test
    void oneUncertainWarningAmongManyIsEnoughToBlockGaps() {
        EvidenceAbsencePolicy policy = ResumeEvidenceReliability.policyFor(
                ReviewStatus.CONFIRMED,
                List.of(ExtractionWarning.of(WarningCode.ENCRYPTED_BUT_READABLE),
                        ExtractionWarning.of(WarningCode.POSSIBLE_MULTI_COLUMN),
                        ExtractionWarning.of(WarningCode.REPEATED_HEADER_FOOTER)));

        assertThat(policy).isEqualTo(EvidenceAbsencePolicy.MUST_BE_UNKNOWN);
    }
}
