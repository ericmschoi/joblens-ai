package com.joblens.jobposting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.joblens.document.ContentFingerprint;
import com.joblens.document.ExtractionWarning;
import com.joblens.document.ReviewStatus;
import com.joblens.document.WarningCode;
import com.joblens.error.ApiException;
import com.joblens.error.ErrorCode;
import com.joblens.jobposting.model.JobPosting;
import com.joblens.testsupport.JobPostingFixtures;
import com.joblens.testsupport.JobPostingServices;
import com.joblens.testsupport.TestProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class JobPostingConfirmationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-11T12:00:00Z");

    private final ContentFingerprint fingerprints = new ContentFingerprint(JsonMapper.builder().build());
    private final JobPostingConfirmationService service = new JobPostingConfirmationService(
            TestProperties.defaults(), fingerprints, Clock.fixed(NOW, ZoneOffset.UTC));

    private final JobPostingExtractionService extraction = JobPostingServices.pasteOnly(TestProperties.defaults());

    private JobPostingExtractionResult extracted(String pasted) {
        return extraction.extractFromText(pasted);
    }

    @Test
    void confirmingRequiresAnExplicitYes() {
        JobPostingExtractionResult result = extracted(JobPostingFixtures.WELL_STRUCTURED);

        assertThatThrownBy(() -> service.confirm(result.rawText(), result.posting(), false, List.of()))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).errorCode())
                .isEqualTo(ErrorCode.REVIEW_NOT_CONFIRMED);
    }

    @Test
    void aCleanConfirmedPostingCanBeReadFromItsSections() {
        JobPostingExtractionResult result = extracted(JobPostingFixtures.WELL_STRUCTURED);

        ConfirmedJobPosting confirmed =
                service.confirm(result.rawText(), result.posting(), true, result.warnings());

        assertThat(confirmed.reviewStatus()).isEqualTo(ReviewStatus.CONFIRMED);
        assertThat(confirmed.confirmedAt()).isEqualTo(NOW);
        assertThat(confirmed.contentFingerprint()).hasSize(64);
        assertThat(confirmed.requirementSourcePolicy()).isEqualTo(RequirementSourcePolicy.STRUCTURED_SECTIONS);
    }

    @Test
    void aPostingThatNeverSeparatedItsRequirementsStaysUntrustedUntilTheUserSeparatesThem() {
        JobPostingExtractionResult result = extracted(JobPostingFixtures.AMBIGUOUS_QUALIFICATIONS);

        ConfirmedJobPosting asIs = service.confirm(result.rawText(), result.posting(), true, result.warnings());

        assertThat(asIs.warnings().stream().map(ExtractionWarning::code))
                .as("the parser split these by inline markers, which is a guess the user has not checked")
                .contains(WarningCode.REQUIRED_AND_PREFERRED_NOT_SEPARATED);
        assertThat(asIs.requirementSourcePolicy()).isEqualTo(RequirementSourcePolicy.FULL_TEXT_FALLBACK);
    }

    @Test
    void theSeparationWarningSurvivesEvenWhenTheUserRearrangesTheLists() {
        JobPostingExtractionResult result = extracted(JobPostingFixtures.AMBIGUOUS_QUALIFICATIONS);
        JobPosting rearranged = new JobPosting(
                result.posting().title(), result.posting().company(), result.posting().location(),
                null, null,
                result.posting().responsibilities(),
                List.of("3+ years building web applications", "Proficiency with TypeScript and React"),
                List.of("Experience with PostgreSQL", "Kubernetes experience", "GraphQL"),
                List.of(), null);

        ConfirmedJobPosting confirmed = service.confirm(result.rawText(), rearranged, true, result.warnings());

        assertThat(confirmed.warnings().stream().map(ExtractionWarning::code))
                .as("the employer never drew this line, and no amount of editing changes that")
                .contains(WarningCode.REQUIRED_AND_PREFERRED_NOT_SEPARATED);
        assertThat(confirmed.requirementSourcePolicy())
                .as("reading the full text as well is the right response, not a penalty")
                .isEqualTo(RequirementSourcePolicy.FULL_TEXT_FALLBACK);
    }

    @Test
    void emptyingTheQualificationListsDoesNotMakeARoleLookUndemanding() {
        JobPostingExtractionResult result = extracted(JobPostingFixtures.WELL_STRUCTURED);

        ConfirmedJobPosting confirmed =
                service.confirm(result.rawText(), JobPosting.empty(), true, List.of());

        assertThat(confirmed.warnings().stream().map(ExtractionWarning::code))
                .contains(WarningCode.NO_QUALIFICATION_SECTIONS_DETECTED);
        assertThat(confirmed.requirementSourcePolicy()).isEqualTo(RequirementSourcePolicy.FULL_TEXT_FALLBACK);
    }

    @Test
    void theFingerprintIdentifiesExactlyWhatWasConfirmed() {
        JobPostingExtractionResult result = extracted(JobPostingFixtures.WELL_STRUCTURED);
        ConfirmedJobPosting confirmed =
                service.confirm(result.rawText(), result.posting(), true, result.warnings());

        assertThat(fingerprints.matches(confirmed.contentFingerprint(), confirmed.rawText(), confirmed.posting()))
                .isTrue();
        assertThat(fingerprints.matches(confirmed.contentFingerprint(),
                confirmed.rawText() + " quietly edited", confirmed.posting()))
                .isFalse();
    }

    @Test
    void rejectsReviewedTextThatIsTooShortToAnalyse() {
        assertThatThrownBy(() -> service.confirm("Backend engineer.", JobPosting.empty(), true, List.of()))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).errorCode())
                .isEqualTo(ErrorCode.JD_TEXT_TOO_SHORT);
    }
}
