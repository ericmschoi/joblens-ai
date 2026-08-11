package com.joblens.jobposting;

import static org.assertj.core.api.Assertions.assertThat;

import com.joblens.document.ExtractionWarning;
import com.joblens.document.ReviewStatus;
import com.joblens.document.WarningCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The posting-side counterpart of the resume evidence rule: a requirement the parser missed is not
 * a requirement the employer does not have.
 */
class JobPostingReliabilityTest {

    @Test
    void anUnreviewedPostingCannotBeTreatedAsACompleteRequirementList() {
        assertThat(JobPostingReliability.policyFor(ReviewStatus.REVIEW_REQUIRED, List.of()))
                .isEqualTo(RequirementSourcePolicy.FULL_TEXT_FALLBACK);
    }

    @Test
    void aCleanConfirmedPostingCanBeReadFromItsStructuredSections() {
        assertThat(JobPostingReliability.policyFor(ReviewStatus.CONFIRMED, List.of()))
                .isEqualTo(RequirementSourcePolicy.STRUCTURED_SECTIONS);
    }

    @ParameterizedTest
    @EnumSource(value = WarningCode.class, names = {
            "NO_QUALIFICATION_SECTIONS_DETECTED",
            "REQUIRED_AND_PREFERRED_NOT_SEPARATED",
            "NO_SECTIONS_DETECTED",
            "TEXT_TRUNCATED"
    })
    void structuralUncertaintyForcesRequirementsToBeReadFromTheFullText(WarningCode code) {
        assertThat(JobPostingReliability.policyFor(ReviewStatus.CONFIRMED, List.of(ExtractionWarning.of(code))))
                .as("%s means the lists may be incomplete", code)
                .isEqualTo(RequirementSourcePolicy.FULL_TEXT_FALLBACK);
    }

    @Test
    void missingResponsibilitiesAloneDoesNotInvalidateTheQualificationLists() {
        assertThat(JobPostingReliability.policyFor(ReviewStatus.CONFIRMED,
                List.of(ExtractionWarning.of(WarningCode.NO_RESPONSIBILITIES_DETECTED))))
                .as("a posting can legitimately list requirements without listing duties")
                .isEqualTo(RequirementSourcePolicy.STRUCTURED_SECTIONS);
    }
}
