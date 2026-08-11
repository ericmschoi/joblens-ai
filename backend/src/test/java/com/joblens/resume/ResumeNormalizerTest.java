package com.joblens.resume;

import static org.assertj.core.api.Assertions.assertThat;

import com.joblens.document.ExtractionWarning;
import com.joblens.document.WarningCode;
import com.joblens.resume.model.CandidateProfile;
import com.joblens.resume.model.DateRange;
import com.joblens.resume.model.SkillMention;
import com.joblens.resume.model.WorkExperience;
import com.joblens.testsupport.PdfFixtureFactory;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResumeNormalizerTest {

    private static final String RESUME = String.join("\n", PdfFixtureFactory.ONE_COLUMN_RESUME);

    private final ResumeNormalizer normalizer = new ResumeNormalizer();

    private CandidateProfile profile() {
        return normalizer.normalize(RESUME).profile();
    }

    @Test
    void readsTheSummarySection() {
        assertThat(profile().summary()).contains("six years", "customer-facing web applications");
    }

    @Test
    void readsEachRoleWithItsTitleCompanyAndDates() {
        List<WorkExperience> experiences = profile().workExperiences();

        assertThat(experiences).hasSize(2);

        WorkExperience current = experiences.getFirst();
        assertThat(current.title()).isEqualTo("Senior Software Engineer");
        assertThat(current.company()).isEqualTo("Northwind Systems");
        assertThat(current.dates().current()).isTrue();
        assertThat(current.dates().startYearMonth()).isEqualTo("2021-03");
        assertThat(current.bullets()).hasSize(3);
        assertThat(current.bullets().getFirst()).startsWith("Designed and shipped");

        WorkExperience previous = experiences.get(1);
        assertThat(previous.title()).isEqualTo("Software Engineer");
        assertThat(previous.company()).isEqualTo("Lakeshore Digital");
        assertThat(previous.dates().endYearMonth()).isEqualTo("2021-02");
        assertThat(previous.bullets()).hasSize(2);
    }

    @Test
    void keepsProvenanceSoEvidenceCanBeTracedBackToTheDocument() {
        WorkExperience current = profile().workExperiences().getFirst();

        assertThat(current.provenance().section()).isEqualTo("EXPERIENCE");
        assertThat(current.provenance().sourceQuote()).contains("Northwind Systems");
        assertThat(current.provenance().lineEnd()).isGreaterThanOrEqualTo(current.provenance().lineStart());
    }

    @Test
    void readsEducationWithTheInstitutionFromTheFollowingLine() {
        assertThat(profile().education()).singleElement().satisfies(education -> {
            assertThat(education.credential()).contains("Bachelor of Science");
            assertThat(education.institution()).isEqualTo("University of Waterloo");
            assertThat(education.dates().startYearMonth()).isEqualTo("2014-09");
        });
    }

    @Test
    void readsProjectsAndCertifications() {
        CandidateProfile profile = profile();

        assertThat(profile.projects()).singleElement().satisfies(project -> {
            assertThat(project.name()).isEqualTo("Ledger Reconciler");
            assertThat(project.bullets()).singleElement().asString().contains("reconciles ledger exports");
        });
        assertThat(profile.certifications()).singleElement().satisfies(certification ->
                assertThat(certification.name()).isEqualTo("AWS Certified Developer - Associate"));
    }

    @Test
    void readsTheSkillsList() {
        List<String> listed = profile().skills().stream()
                .filter(skill -> skill.origin() == SkillMention.Origin.SKILLS_LIST)
                .map(SkillMention::name)
                .toList();

        assertThat(listed).contains("Java", "Spring Boot", "React", "TypeScript", "PostgreSQL", "Kafka");
    }

    @Test
    void distinguishesASkillDemonstratedInRealWorkFromOneOnlyListed() {
        List<SkillMention> skills = profile().skills();

        assertThat(originsOf(skills, "Spring Boot"))
                .as("Spring Boot is described inside a role, so it is demonstrated evidence")
                .contains(SkillMention.Origin.SKILLS_LIST, SkillMention.Origin.WORK_EXPERIENCE);

        assertThat(originsOf(skills, "Kafka"))
                .as("Kafka appears only in the skills list, which is weaker evidence")
                .containsExactly(SkillMention.Origin.SKILLS_LIST);
    }

    @Test
    void recordsSkillsDemonstratedInProjectsSeparatelyFromRoles() {
        assertThat(originsOf(profile().skills(), "Java"))
                .contains(SkillMention.Origin.WORK_EXPERIENCE, SkillMention.Origin.PROJECT);
    }

    @Test
    void pointsDemonstratedSkillsAtTheSentenceThatProvesThem() {
        SkillMention demonstrated = profile().skills().stream()
                .filter(skill -> skill.name().equals("React"))
                .filter(skill -> skill.origin() == SkillMention.Origin.WORK_EXPERIENCE)
                .findFirst()
                .orElseThrow();

        assertThat(demonstrated.provenance().sourceQuote()).contains("Rebuilt the partner onboarding flow");
    }

    @Test
    void warnsWhenNoSectionsCanBeRecognisedInsteadOfReturningAConfidentEmptyProfile() {
        NormalizedResume result = normalizer.normalize("""
                Alex Morgan
                Some prose about a career that uses no recognisable headings at all.
                More prose that continues in the same manner.
                """);

        assertThat(codesOf(result)).contains(WarningCode.NO_SECTIONS_DETECTED, WarningCode.NO_ROLES_DETECTED);
        assertThat(result.profile().workExperiences()).isEmpty();
    }

    @Test
    void warnsSeparatelyWhenSectionsAreFoundButNoRolesAreIn() {
        NormalizedResume result = normalizer.normalize("""
                SKILLS
                Java, Spring Boot, React

                EDUCATION
                Bachelor of Science, Computer Science   Sep 2014 - Apr 2018
                University of Waterloo
                """);

        assertThat(codesOf(result))
                .as("sections parsed fine, so this is a different failure from finding no sections at all")
                .contains(WarningCode.NO_ROLES_DETECTED)
                .doesNotContain(WarningCode.NO_SECTIONS_DETECTED);
    }

    @Test
    void warnsWithACountWhenTextInsideASectionBelongsToNoRole() {
        NormalizedResume result = normalizer.normalize("""
                EXPERIENCE
                An introductory paragraph about my career.
                Another line of context.
                A third line of context.
                A fourth line of context.
                Senior Software Engineer, Northwind Systems   Mar 2021 - Present
                - Shipped a payments service.
                """);

        assertThat(result.warnings())
                .filteredOn(warning -> warning.code() == WarningCode.UNASSIGNED_TEXT_BLOCKS)
                .singleElement()
                .satisfies(warning -> assertThat(warning.count()).isEqualTo(3));
    }

    @Test
    void warnsWhenARoleHasNoReliableDateRange() {
        NormalizedResume result = normalizer.normalize("""
                EXPERIENCE
                Senior Software Engineer, Northwind Systems   2021 - 2023
                - Shipped a payments service.
                """);

        assertThat(result.warnings())
                .filteredOn(warning -> warning.code() == WarningCode.LOW_CONFIDENCE_STRUCTURE)
                .singleElement()
                .satisfies(warning -> assertThat(warning.count()).isEqualTo(1));
    }

    @Test
    void aCleanResumeRaisesNoStructuralWarningsAtAll() {
        assertThat(codesOf(normalizer.normalize(RESUME)))
                .as("warnings the user can safely ignore train them to ignore all warnings")
                .doesNotContain(
                        WarningCode.NO_SECTIONS_DETECTED,
                        WarningCode.NO_ROLES_DETECTED,
                        WarningCode.UNASSIGNED_TEXT_BLOCKS,
                        WarningCode.LOW_CONFIDENCE_STRUCTURE);
    }

    private static List<WarningCode> codesOf(NormalizedResume result) {
        return result.warnings().stream().map(ExtractionWarning::code).toList();
    }

    @Test
    void handlesTheLayoutThatPutsTheCompanyOnItsOwnLine() {
        CandidateProfile alternative = normalizer.normalize("""
                EXPERIENCE
                Northwind Systems
                Senior Software Engineer | Mar 2021 - Present
                - Shipped a payments service.
                """).profile();

        assertThat(alternative.workExperiences()).singleElement().satisfies(experience -> {
            assertThat(experience.title()).isEqualTo("Senior Software Engineer");
            assertThat(experience.company()).isEqualTo("Northwind Systems");
            assertThat(experience.dates().parseConfidence()).isEqualTo(DateRange.Confidence.HIGH);
        });
    }

    private static List<SkillMention.Origin> originsOf(List<SkillMention> skills, String name) {
        return skills.stream()
                .filter(skill -> skill.name().equalsIgnoreCase(name))
                .map(SkillMention::origin)
                .distinct()
                .toList();
    }
}
