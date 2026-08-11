package com.joblens.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.joblens.document.ExtractionWarning;
import com.joblens.document.WarningCode;
import com.joblens.error.ApiException;
import com.joblens.error.ErrorCode;
import com.joblens.resume.model.CandidateProfile;
import com.joblens.resume.model.DateRange;
import com.joblens.resume.model.WorkExperience;
import com.joblens.testsupport.PdfFixtureFactory;
import com.joblens.testsupport.TestProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ResumeConfirmationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-11T12:00:00Z");
    private static final String RESUME = String.join("\n", PdfFixtureFactory.ONE_COLUMN_RESUME);

    private final ResumeContentFingerprint fingerprints =
            new ResumeContentFingerprint(JsonMapper.builder().build());
    private final ResumeConfirmationService service = new ResumeConfirmationService(
            TestProperties.defaults(), fingerprints, Clock.fixed(NOW, ZoneOffset.UTC));

    private final CandidateProfile parsedProfile = new ResumeNormalizer().normalize(RESUME).profile();

    @Test
    void confirmingRequiresAnExplicitYes() {
        assertThatThrownBy(() -> service.confirm(RESUME, parsedProfile, false, List.of()))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).errorCode())
                .isEqualTo(ErrorCode.REVIEW_NOT_CONFIRMED);
    }

    @Test
    void aConfirmedResumeCarriesTheReviewedContentAndAFingerprint() {
        ConfirmedResume confirmed = service.confirm(RESUME, parsedProfile, true, List.of());

        assertThat(confirmed.reviewStatus()).isEqualTo(ResumeReviewStatus.CONFIRMED);
        assertThat(confirmed.confirmedAt()).isEqualTo(NOW);
        assertThat(confirmed.rawText()).contains("Northwind Systems");
        assertThat(confirmed.profile().workExperiences()).hasSize(2);
        assertThat(confirmed.contentFingerprint()).hasSize(64);
        assertThat(confirmed.evidenceAbsencePolicy()).isEqualTo(EvidenceAbsencePolicy.MAY_BE_GAP);
    }

    @Test
    void theFingerprintIdentifiesExactlyWhatWasConfirmed() {
        ConfirmedResume confirmed = service.confirm(RESUME, parsedProfile, true, List.of());

        assertThat(fingerprints.matches(confirmed.contentFingerprint(), confirmed.rawText(), confirmed.profile()))
                .isTrue();
        assertThat(fingerprints.matches(confirmed.contentFingerprint(), RESUME + " edited later", parsedProfile))
                .as("content changed after confirmation must not verify")
                .isFalse();
    }

    @Test
    void keepsWarningsAboutHowTheFileWasReadButRecomputesWarningsAboutTheStructure() {
        List<ExtractionWarning> carried = List.of(
                ExtractionWarning.of(WarningCode.REPEATED_HEADER_FOOTER),
                ExtractionWarning.of(WarningCode.NO_ROLES_DETECTED));

        ConfirmedResume confirmed = service.confirm(RESUME, parsedProfile, true, carried);

        List<WarningCode> codes = confirmed.warnings().stream().map(ExtractionWarning::code).toList();
        assertThat(codes)
                .as("the user added the roles during review, so the stale structural warning must go")
                .doesNotContain(WarningCode.NO_ROLES_DETECTED);
        assertThat(codes).contains(WarningCode.REPEATED_HEADER_FOOTER);
    }

    @Test
    void deletingTheUnparseablePartsDoesNotMakeTheResultLookReliable() {
        CandidateProfile emptied = CandidateProfile.empty();

        ConfirmedResume confirmed = service.confirm(RESUME, emptied, true, List.of());

        assertThat(confirmed.warnings().stream().map(ExtractionWarning::code))
                .contains(WarningCode.NO_ROLES_DETECTED);
        assertThat(confirmed.evidenceAbsencePolicy())
                .as("a profile with no roles cannot support a confident gap judgement")
                .isEqualTo(EvidenceAbsencePolicy.MUST_BE_UNKNOWN);
    }

    @Test
    void flagsRolesThatAreStillMissingATitleEmployerOrReliableDates() {
        CandidateProfile weak = new CandidateProfile("", List.of(),
                List.of(new WorkExperience("exp-1", null, "Engineer", null,
                        DateRange.unparsed("some time ago"), List.of("Did things."), null)),
                List.of(), List.of(), List.of());

        ConfirmedResume confirmed = service.confirm(RESUME, weak, true, List.of());

        assertThat(confirmed.warnings())
                .filteredOn(warning -> warning.code() == WarningCode.LOW_CONFIDENCE_STRUCTURE)
                .singleElement()
                .satisfies(warning -> assertThat(warning.count()).isEqualTo(1));
        assertThat(confirmed.evidenceAbsencePolicy()).isEqualTo(EvidenceAbsencePolicy.MUST_BE_UNKNOWN);
    }

    @Test
    void rejectsReviewedTextThatIsTooShortToAnalyse() {
        assertThatThrownBy(() -> service.confirm("Alex Morgan", parsedProfile, true, List.of()))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).errorCode())
                .isEqualTo(ErrorCode.RESUME_TEXT_TOO_SHORT);
    }

    @Test
    void rejectsReviewedTextLongerThanJobLensProcesses() {
        String tooLong = "x".repeat(TestProperties.MAX_EXTRACTED_CHARACTERS + 1);

        assertThatThrownBy(() -> service.confirm(tooLong, parsedProfile, true, List.of()))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).errorCode())
                .isEqualTo(ErrorCode.ANALYSIS_INPUT_TOO_LARGE);
    }
}
