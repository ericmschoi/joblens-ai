package com.joblens.jobposting;

import static org.assertj.core.api.Assertions.assertThat;

import com.joblens.document.ExtractionWarning;
import com.joblens.document.WarningCode;
import com.joblens.jobposting.model.JobPosting;
import com.joblens.testsupport.JobPostingFixtures;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class JobPostingParserTest {

    private final JobPostingTextNormalizer normalizer = new JobPostingTextNormalizer();
    private final JobPostingParser parser = new JobPostingParser();

    private ParsedJobPosting parse(String pasted) {
        return parser.parse(normalizer.normalize(pasted));
    }

    private static List<WarningCode> codesOf(ParsedJobPosting parsed) {
        return parsed.warnings().stream().map(ExtractionWarning::code).toList();
    }

    @Nested
    class WellStructuredPosting {

        private final ParsedJobPosting parsed = parse(JobPostingFixtures.WELL_STRUCTURED);

        @Test
        void readsTheHeaderDetails() {
            JobPosting posting = parsed.posting();

            assertThat(posting.title()).isEqualTo("Senior Backend Engineer");
            assertThat(posting.company()).isEqualTo("Acme Corp");
            assertThat(posting.location()).isEqualTo("Toronto, ON (Hybrid)");
            assertThat(posting.employmentType()).isEqualTo("Full-time");
            assertThat(posting.compensationText()).contains("120,000", "150,000");
        }

        @Test
        void keepsRequiredAndPreferredApart() {
            JobPosting posting = parsed.posting();

            assertThat(posting.requiredQualifications()).hasSize(4);
            assertThat(posting.requiredQualifications().getFirst())
                    .isEqualTo("5+ years of professional backend development experience");
            assertThat(posting.preferredQualifications())
                    .containsExactly("Kafka or another event streaming platform",
                            "Terraform or comparable infrastructure-as-code");
        }

        @Test
        void readsTheResponsibilities() {
            assertThat(parsed.posting().responsibilities()).hasSize(3);
            assertThat(parsed.posting().responsibilities().getFirst())
                    .isEqualTo("Design and build backend services in Java and Spring Boot");
        }

        @Test
        void keepsUnrelatedSectionsOutOfTheQualificationLists() {
            JobPosting posting = parsed.posting();

            assertThat(posting.otherSections())
                    .extracting(JobPosting.Section::heading)
                    .contains("About the role", "Benefits");
            assertThat(posting.requiredQualifications())
                    .noneMatch(item -> item.contains("health coverage") || item.contains("vacation"));
        }

        @Test
        void raisesNoWarningsForAPostingThatSaysWhatItMeans() {
            assertThat(codesOf(parsed)).isEmpty();
        }
    }

    @Nested
    class PostingThatDoesNotSeparateRequirements {

        private final ParsedJobPosting parsed = parse(JobPostingFixtures.AMBIGUOUS_QUALIFICATIONS);

        @Test
        void takesTheStricterReadingAndSaysSo() {
            assertThat(codesOf(parsed)).contains(WarningCode.REQUIRED_AND_PREFERRED_NOT_SEPARATED);
            assertThat(parsed.posting().requiredQualifications())
                    .containsExactly("3+ years building web applications",
                            "Proficiency with TypeScript and React",
                            "Experience with PostgreSQL");
        }

        @Test
        void honoursOptionalityMarkedOnTheItemItself() {
            assertThat(parsed.posting().preferredQualifications())
                    .containsExactly("Kubernetes experience is a plus", "GraphQL (preferred)");
        }

        @Test
        void stillReadsResponsibilitiesFromAnInformalHeading() {
            assertThat(parsed.posting().responsibilities()).containsExactly("Ship features end to end");
        }
    }

    @Test
    void doesNotDemoteItemsInsideAnExplicitlyRequiredSection() {
        ParsedJobPosting parsed = parse("""
                Backend Engineer
                Acme Corp

                Required Qualifications
                • Experience with a preferred vendor integration is a plus for this team
                • Strong Java
                """);

        assertThat(parsed.posting().requiredQualifications())
                .as("the employer already said these are required; demoting one would flatter the candidate")
                .hasSize(2);
        assertThat(parsed.posting().preferredQualifications()).isEmpty();
    }

    @Test
    void reportsEveryStructuralFailureForContinuousProse() {
        ParsedJobPosting parsed = parse(JobPostingFixtures.PLAIN_PROSE);

        assertThat(codesOf(parsed)).contains(
                WarningCode.NO_SECTIONS_DETECTED,
                WarningCode.NO_QUALIFICATION_SECTIONS_DETECTED,
                WarningCode.NO_RESPONSIBILITIES_DETECTED);
        assertThat(parsed.posting().requiredQualifications()).isEmpty();
    }

    @Test
    void warnsAboutPlantedInstructionsAndKeepsThemOutOfTheRequirements() {
        ParsedJobPosting parsed = parse(JobPostingFixtures.WITH_EMBEDDED_INSTRUCTIONS);

        assertThat(codesOf(parsed)).contains(WarningCode.POSSIBLE_EMBEDDED_INSTRUCTIONS);
        assertThat(parsed.posting().requiredQualifications())
                .as("a planted sentence must not become something the candidate is scored against")
                .containsExactly("5+ years of professional backend development experience",
                        "Strong Java and Spring Boot");
    }

    @Test
    void keepsGenuineRequirementsThatMerelyMentionAiConcepts() {
        ParsedJobPosting parsed = parse("""
                AI Platform Engineer
                Acme Corp

                Required Qualifications
                • Experience designing and evaluating system prompts for production features
                • Familiarity with prompt injection defences
                • Strong Java
                """);

        assertThat(parsed.posting().requiredQualifications())
                .as("mentioning the subject is not the same as issuing an instruction")
                .hasSize(3);
        assertThat(codesOf(parsed))
                .as("the user is still told, because the wording is worth a second look")
                .contains(WarningCode.POSSIBLE_EMBEDDED_INSTRUCTIONS);
    }

    @Test
    void rejoinsALineThatIsVisiblyTheContinuationOfTheBulletAboveIt() {
        ParsedJobPosting parsed = parse(JobPostingFixtures.MESSY_PASTE);

        assertThat(parsed.posting().requiredQualifications())
                .as("a wrapped bullet is one requirement, not two")
                .containsExactly("Strong Java",
                        "Experience with \"event-driven\" systems",
                        "Comfortable owning a feature end to end in production");
    }

    @Test
    void readsCompensationFromItsOwnSectionWhenTheHeaderHasNone() {
        ParsedJobPosting parsed = parse("""
                Backend Engineer
                Acme Corp

                Required Qualifications
                • Strong Java

                Salary range
                CAD 130,000 - 160,000 per year
                """);

        assertThat(parsed.posting().compensationText()).contains("130,000");
    }
}
