package com.joblens.jobposting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.joblens.config.JoblensProperties;
import com.joblens.document.ReviewStatus;
import com.joblens.error.ApiException;
import com.joblens.error.ErrorCode;
import com.joblens.testsupport.JobPostingFixtures;
import com.joblens.testsupport.JobPostingServices;
import com.joblens.testsupport.TestProperties;
import org.junit.jupiter.api.Test;

class JobPostingExtractionServiceTest {

    private final JobPostingExtractionService service = JobPostingServices.pasteOnly(TestProperties.defaults());

    @Test
    void readsAPastedPostingIntoStructuredLists() {
        JobPostingExtractionResult result = service.extractFromText(JobPostingFixtures.WELL_STRUCTURED);

        assertThat(result.sourceType()).isEqualTo(JobPostingExtractionResult.SourceType.TEXT);
        assertThat(result.posting().requiredQualifications()).hasSize(4);
        assertThat(result.posting().preferredQualifications()).hasSize(2);
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void marksEveryExtractionAsNeedingReview() {
        JobPostingExtractionResult result = service.extractFromText(JobPostingFixtures.WELL_STRUCTURED);

        assertThat(result.reviewStatus()).isEqualTo(ReviewStatus.REVIEW_REQUIRED);
        assertThat(result.requirementSourcePolicy())
                .as("an unreviewed posting's lists are never the last word on what the role requires")
                .isEqualTo(RequirementSourcePolicy.FULL_TEXT_FALLBACK);
    }

    @Test
    void alwaysReturnsTheNormalizedTextEvenWhenNothingCouldBeStructured() {
        JobPostingExtractionResult result = service.extractFromText(JobPostingFixtures.PLAIN_PROSE);

        assertThat(result.posting().requiredQualifications()).isEmpty();
        assertThat(result.rawText()).contains("full-stack software engineer", "shares", "on-call duties");
    }

    @Test
    void rejectsTextTooShortToDescribeARole() {
        assertThatThrownBy(() -> service.extractFromText("Backend engineer wanted."))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).errorCode())
                .isEqualTo(ErrorCode.JD_TEXT_TOO_SHORT);
    }

    @Test
    void rejectsTextBeyondTheProcessingLimit() {
        JobPostingExtractionService constrained = JobPostingServices.pasteOnly(TestProperties.withJobPosting(new JoblensProperties.JobPosting(10, 100)));

        assertThatThrownBy(() -> constrained.extractFromText(JobPostingFixtures.WELL_STRUCTURED))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).errorCode())
                .isEqualTo(ErrorCode.JD_TEXT_TOO_LONG);
    }

}
