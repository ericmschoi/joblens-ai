package com.joblens.analysis.provider;

import com.joblens.analysis.AnalysisInput;
import com.joblens.analysis.model.AnalysisDraft;
import com.joblens.analysis.model.Assessment;
import com.joblens.analysis.model.CategoryName;
import com.joblens.analysis.model.Criticality;
import com.joblens.analysis.model.EvidenceMatch;
import com.joblens.analysis.model.EvidenceRelation;
import com.joblens.analysis.model.EvidenceStrength;
import com.joblens.analysis.model.Importance;
import com.joblens.analysis.model.InterviewPreparation;
import com.joblens.analysis.model.MatchStatus;
import com.joblens.analysis.model.NarrativeAssessment;
import com.joblens.analysis.model.OpportunityValue;
import com.joblens.analysis.model.RequirementAssessment;
import com.joblens.analysis.model.RequirementKind;
import com.joblens.analysis.model.ResumePositioningAdvice;
import com.joblens.analysis.model.SubfactorJudgement;
import com.joblens.jobposting.model.JobPosting;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Deterministic in-process provider, and the default.
 *
 * <p>It exists so the application runs end to end with no API key and no outbound AI traffic, and
 * so every test that touches analysis is repeatable. It is not a simulation of a language model: it
 * matches requirement wording against the resume by keyword and is honest about how little that
 * proves. Where a real model would judge, this one reports {@code UNKNOWN}.
 *
 * <p>It returns JSON through the same boundary a real provider would, so validation, grounding and
 * scoring are exercised for real rather than bypassed.
 */
@Component
@ConditionalOnProperty(name = "joblens.analysis.provider", havingValue = "fake", matchIfMissing = true)
public class FakeAnalysisProvider implements AnalysisProvider {

    public static final String ID = "fake";

    private static final int MAX_REQUIREMENTS_PER_LIST = 20;
    private static final int CORE_REQUIREMENT_COUNT = 4;
    private static final Pattern SENTENCE = Pattern.compile("(?<=[.!?])\\s+|\\n");
    private static final Pattern WORD = Pattern.compile("[^A-Za-z0-9+#.]+");

    /** Words that appear in every posting and identify nothing. */
    private static final Set<String> STOPWORDS = Set.of(
            "with", "and", "the", "for", "years", "year", "experience", "strong", "using", "work",
            "working", "ability", "skills", "knowledge", "understanding", "professional", "hands",
            "excellent", "good", "plus", "such", "including", "other", "must", "have", "from", "that",
            "this", "into", "across", "within", "their", "your", "team", "teams", "role");

    private static final Set<String> DOMAIN_HINTS = Set.of(
            "aws", "azure", "gcp", "cloud", "kubernetes", "docker", "terraform", "production",
            "reliability", "scale", "uptime", "incident", "on-call", "observability", "monitoring");

    private final ObjectMapper objectMapper;

    public FakeAnalysisProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean sendsContentOffHost() {
        return false;
    }

    @Override
    public String analyze(AnalysisInput input, String systemPrompt, String userPrompt) {
        return objectMapper.writeValueAsString(draftFor(input));
    }

    private AnalysisDraft draftFor(AnalysisInput input) {
        List<String> sentences = sentencesOf(input.resumeText());
        JobPosting posting = input.jobPosting();
        List<RequirementAssessment> assessments = new ArrayList<>();

        int index = 0;
        for (String requirement : capped(posting.requiredQualifications())) {
            index++;
            assessments.add(assess("req-" + index, requirement, Importance.REQUIRED,
                    index <= CORE_REQUIREMENT_COUNT ? Criticality.CORE : Criticality.SUPPORTING,
                    categoryFor(requirement), sentences, input.absentEvidenceMustBeUnknown()));
        }
        for (String requirement : capped(posting.preferredQualifications())) {
            index++;
            assessments.add(assess("req-" + index, requirement, Importance.PREFERRED,
                    Criticality.SUPPORTING, categoryFor(requirement), sentences,
                    input.absentEvidenceMustBeUnknown()));
        }
        for (String responsibility : capped(posting.responsibilities())) {
            index++;
            assessments.add(assess("req-" + index, responsibility, Importance.REQUIRED,
                    Criticality.SUPPORTING, CategoryName.ROLE_AND_RESPONSIBILITY_ALIGNMENT, sentences,
                    input.absentEvidenceMustBeUnknown()));
        }

        if (assessments.isEmpty()) {
            assessments.add(assess("req-1", "Unspecified requirements", Importance.REQUIRED,
                    Criticality.SUPPORTING, CategoryName.ROLE_AND_RESPONSIBILITY_ALIGNMENT, sentences,
                    true));
        }

        return new AnalysisDraft(
                AnalysisDraft.SCHEMA_VERSION,
                assessments,
                subfactorsFor(input),
                narrative("Role alignment", "The posting's responsibilities were compared with the "
                        + "roles described in the resume."),
                narrative("Seniority alignment", "Level was read from documented scope and ownership "
                        + "rather than from years alone."),
                narrative("Realistic competitiveness", "This reading is based only on what the resume "
                        + "states. A local analysis provider cannot weigh nuance the way a full "
                        + "analysis will."),
                opportunityValue(posting),
                positioning(posting),
                interviewPreparation(assessments),
                "Produced by the local analysis provider. It matches requirement wording against the "
                        + "resume and reports anything it cannot verify as unknown.",
                List.of("This analysis came from JobLens's built-in local provider, which compares "
                        + "wording rather than meaning. Judgements are conservative by design."));
    }

    // --- requirements ----------------------------------------------------------------------------

    private static RequirementAssessment assess(String id, String requirement, Importance importance,
            Criticality criticality, CategoryName category, List<String> resumeSentences,
            boolean absentEvidenceMustBeUnknown) {

        List<String> terms = distinctiveTerms(requirement);
        Optional<String> supporting = resumeSentences.stream()
                .filter(sentence -> mentionsAny(sentence, terms))
                .findFirst();

        if (supporting.isEmpty()) {
            // Keyword matching cannot tell "not present" from "worded differently", so it never
            // claims a gap on its own. A real provider decides that; this one declines to.
            return new RequirementAssessment(id, requirement, kindFor(requirement), importance,
                    criticality, null, category, MatchStatus.UNKNOWN, EvidenceRelation.NONE,
                    EvidenceStrength.NONE, List.of(),
                    absentEvidenceMustBeUnknown
                            ? "No matching wording was found, and the resume is not confirmed, so this "
                                    + "is reported as unknown."
                            : "No matching wording was found in the resume. Whether the experience "
                                    + "exists under different wording is not something this provider "
                                    + "can determine.");
        }

        String quote = supporting.get().strip();
        EvidenceMatch evidence = new EvidenceMatch(id, importance, MatchStatus.STRONG_MATCH,
                EvidenceRelation.DIRECT, EvidenceStrength.MODERATE, quote, "RESUME",
                "The resume describes work using this wording.", false);

        return new RequirementAssessment(id, requirement, kindFor(requirement), importance, criticality,
                null, category, MatchStatus.STRONG_MATCH, EvidenceRelation.DIRECT,
                EvidenceStrength.MODERATE, List.of(evidence),
                "Matched on wording shared between the requirement and the resume.");
    }

    private static List<String> distinctiveTerms(String requirement) {
        return java.util.Arrays.stream(WORD.split(requirement.toLowerCase(Locale.ROOT)))
                .filter(word -> word.length() > 3)
                .filter(word -> !STOPWORDS.contains(word))
                .distinct()
                .limit(6)
                .toList();
    }

    private static boolean mentionsAny(String sentence, List<String> terms) {
        String lower = sentence.toLowerCase(Locale.ROOT);
        return terms.stream().anyMatch(lower::contains);
    }

    private static RequirementKind kindFor(String requirement) {
        String lower = requirement.toLowerCase(Locale.ROOT);
        if (lower.contains("degree") || lower.contains("bachelor") || lower.contains("master")) {
            return RequirementKind.EDUCATION;
        }
        if (lower.contains("authoriz") || lower.contains("authoris") || lower.contains("visa")
                || lower.contains("licen")) {
            return RequirementKind.LEGAL_ELIGIBILITY;
        }
        if (lower.matches(".*\\b\\d+\\+?\\s*years?\\b.*")) {
            return RequirementKind.EXPERIENCE;
        }
        return DOMAIN_HINTS.stream().anyMatch(lower::contains)
                ? RequirementKind.DOMAIN
                : RequirementKind.TECHNICAL;
    }

    private static CategoryName categoryFor(String requirement) {
        return kindFor(requirement) == RequirementKind.DOMAIN
                ? CategoryName.DOMAIN_AND_OPERATING_ENVIRONMENT
                : CategoryName.CORE_TECHNICAL_STACK;
    }

    // --- subfactors ------------------------------------------------------------------------------

    private static List<SubfactorJudgement> subfactorsFor(AnalysisInput input) {
        int roles = input.candidateProfile().workExperiences().size();
        long bullets = input.candidateProfile().workExperiences().stream()
                .mapToLong(role -> role.bullets().size()).sum();
        boolean hasNumbers = Pattern.compile("\\d+\\s*%|\\d[\\d,]{2,}").matcher(input.resumeText()).find();
        boolean hasProjects = !input.candidateProfile().projects().isEmpty();

        int depth = scale(bullets, 3, 6, 10);
        int outcomes = hasNumbers ? 3 : 1;

        return List.of(
                new SubfactorJudgement(CategoryName.EXPERIENCE_EVIDENCE, "specificity", depth,
                        "Based on how much detail the documented roles carry."),
                new SubfactorJudgement(CategoryName.EXPERIENCE_EVIDENCE, "outcomes", outcomes,
                        hasNumbers ? "The resume quantifies some results."
                                : "The resume describes work without quantifying results."),
                new SubfactorJudgement(CategoryName.EXPERIENCE_EVIDENCE, "depth",
                        hasProjects ? Math.min(depth + 1, SubfactorJudgement.MAX_VALUE) : depth,
                        hasProjects ? "Projects add detail beyond the roles themselves."
                                : "Detail comes from the roles alone."),
                new SubfactorJudgement(CategoryName.EXPERIENCE_EVIDENCE, "recency", 2,
                        "Recency is not assessed by the local provider."),
                new SubfactorJudgement(CategoryName.EXPERIENCE_EVIDENCE, "consistency",
                        scale(roles, 1, 2, 3), "Based on the number of documented roles."),
                new SubfactorJudgement(CategoryName.SENIORITY_AND_OWNERSHIP, "yearsAlignment", 2,
                        "Years were not compared numerically by the local provider."),
                new SubfactorJudgement(CategoryName.SENIORITY_AND_OWNERSHIP, "systemComplexity", depth,
                        "Inferred from the detail in the documented roles."),
                new SubfactorJudgement(CategoryName.SENIORITY_AND_OWNERSHIP, "endToEndDelivery",
                        scale(bullets, 2, 5, 9), "Inferred from the breadth of documented work."),
                new SubfactorJudgement(CategoryName.SENIORITY_AND_OWNERSHIP, "ownershipScope",
                        scale(roles, 1, 2, 4), "Inferred from role progression."),
                new SubfactorJudgement(CategoryName.SENIORITY_AND_OWNERSHIP, "measurableOutcomes",
                        outcomes, hasNumbers ? "Some outcomes are quantified." : "Outcomes are not quantified."),
                new SubfactorJudgement(CategoryName.SENIORITY_AND_OWNERSHIP, "leadership", 1,
                        "Leadership is not assessed by the local provider."));
    }

    private static int scale(long value, long low, long medium, long high) {
        if (value >= high) {
            return 4;
        }
        if (value >= medium) {
            return 3;
        }
        if (value >= low) {
            return 2;
        }
        return value > 0 ? 1 : 0;
    }

    // --- narrative sections -----------------------------------------------------------------------

    private static NarrativeAssessment narrative(String headline, String detail) {
        return new NarrativeAssessment(headline, detail, List.of(), List.of());
    }

    private static OpportunityValue opportunityValue(JobPosting posting) {
        boolean statesPay = posting.compensationText() != null && !posting.compensationText().isBlank();
        return new OpportunityValue(
                new Assessment(Assessment.Rating.UNKNOWN, Assessment.Basis.INFERRED_FROM_POSTING,
                        "Career growth is not assessed by the local provider."),
                statesPay
                        ? new Assessment(Assessment.Rating.MODERATE, Assessment.Basis.STATED_IN_POSTING,
                                "The posting states compensation: " + posting.compensationText())
                        : Assessment.unknown("The posting does not state compensation."),
                Assessment.unknown("JobLens does not research companies, so nothing is claimed here."));
    }

    private static ResumePositioningAdvice positioning(JobPosting posting) {
        List<ResumePositioningAdvice.TerminologyAlignment> alignment = capped(
                posting.requiredQualifications()).stream()
                .limit(3)
                .map(requirement -> new ResumePositioningAdvice.TerminologyAlignment(
                        "your wording", requirement,
                        "Use the posting's phrasing where your experience genuinely matches it."))
                .toList();

        return new ResumePositioningAdvice(
                List.of("Lead with the roles closest to this posting."),
                List.of("Bring the work that matches the required qualifications to the top of each role."),
                alignment,
                List.of("Shorten detail that has nothing to do with this role."),
                List.of());
    }

    private static InterviewPreparation interviewPreparation(List<RequirementAssessment> assessments) {
        List<InterviewPreparation.GapToExplain> toExplain = assessments.stream()
                .filter(assessment -> assessment.status() == MatchStatus.UNKNOWN)
                .filter(RequirementAssessment::isCoreRequirement)
                .limit(3)
                .map(assessment -> new InterviewPreparation.GapToExplain(assessment.requirementText(),
                        "Be ready to say plainly what your experience with this is."))
                .toList();

        return new InterviewPreparation(
                List.of(new InterviewPreparation.LikelyQuestion(
                        "Walk me through a project you owned end to end.",
                        "The posting asks for ownership through to production.", List.of())),
                List.of("Have one concrete story ready for each required qualification you match."),
                toExplain,
                List.of("What does the first ninety days look like for this role?"));
    }

    private static List<String> capped(List<String> items) {
        return items.size() > MAX_REQUIREMENTS_PER_LIST ? items.subList(0, MAX_REQUIREMENTS_PER_LIST) : items;
    }

    private static List<String> sentencesOf(String text) {
        return java.util.Arrays.stream(SENTENCE.split(text == null ? "" : text))
                .map(String::strip)
                .filter(sentence -> sentence.length() >= 20)
                .toList();
    }
}
