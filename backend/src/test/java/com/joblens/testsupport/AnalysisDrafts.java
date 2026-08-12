package com.joblens.testsupport;

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
import java.util.List;

/** Builds analysis drafts for scoring tests, so each test states only what it is about. */
public final class AnalysisDrafts {

    private AnalysisDrafts() {}

    public static Builder requirement(String id, String text) {
        return new Builder(id, text);
    }

    /** Fluent because a requirement has eight dimensions and positional arguments hide the point. */
    public static final class Builder {

        private final String id;
        private final String text;
        private RequirementKind kind = RequirementKind.TECHNICAL;
        private Importance importance = Importance.REQUIRED;
        private Criticality criticality = Criticality.SUPPORTING;
        private String alternativeGroupId;
        private CategoryName category = CategoryName.CORE_TECHNICAL_STACK;
        private MatchStatus status = MatchStatus.STRONG_MATCH;
        private EvidenceRelation relation = EvidenceRelation.DIRECT;
        private EvidenceStrength strength = EvidenceStrength.STRONG;
        private List<EvidenceMatch> evidence = List.of();

        private Builder(String id, String text) {
            this.id = id;
            this.text = text;
        }

        public Builder kind(RequirementKind value) {
            this.kind = value;
            return this;
        }

        public Builder preferred() {
            this.importance = Importance.PREFERRED;
            return this;
        }

        public Builder core() {
            this.criticality = Criticality.CORE;
            return this;
        }

        public Builder alternativeGroup(String value) {
            this.alternativeGroupId = value;
            return this;
        }

        public Builder category(CategoryName value) {
            this.category = value;
            return this;
        }

        public Builder status(MatchStatus value) {
            this.status = value;
            return this;
        }

        public Builder relation(EvidenceRelation value) {
            this.relation = value;
            return this;
        }

        public Builder strength(EvidenceStrength value) {
            this.strength = value;
            return this;
        }

        public Builder withGroundedQuote(String quote) {
            this.evidence = List.of(new EvidenceMatch(id, importance, status, relation, strength,
                    quote, "EXPERIENCE", "supported", true));
            return this;
        }

        public RequirementAssessment build() {
            return new RequirementAssessment(id, text, kind, importance, criticality, alternativeGroupId,
                    category, status, relation, strength, evidence, "because");
        }
    }

    public static List<SubfactorJudgement> subfactors(int experienceValue, int seniorityValue) {
        return List.of(
                new SubfactorJudgement(CategoryName.EXPERIENCE_EVIDENCE, "specificity", experienceValue, "r"),
                new SubfactorJudgement(CategoryName.EXPERIENCE_EVIDENCE, "outcomes", experienceValue, "r"),
                new SubfactorJudgement(CategoryName.EXPERIENCE_EVIDENCE, "depth", experienceValue, "r"),
                new SubfactorJudgement(CategoryName.EXPERIENCE_EVIDENCE, "recency", experienceValue, "r"),
                new SubfactorJudgement(CategoryName.EXPERIENCE_EVIDENCE, "consistency", experienceValue, "r"),
                new SubfactorJudgement(CategoryName.SENIORITY_AND_OWNERSHIP, "yearsAlignment", seniorityValue, "r"),
                new SubfactorJudgement(CategoryName.SENIORITY_AND_OWNERSHIP, "systemComplexity", seniorityValue, "r"),
                new SubfactorJudgement(CategoryName.SENIORITY_AND_OWNERSHIP, "endToEndDelivery", seniorityValue, "r"),
                new SubfactorJudgement(CategoryName.SENIORITY_AND_OWNERSHIP, "ownershipScope", seniorityValue, "r"),
                new SubfactorJudgement(CategoryName.SENIORITY_AND_OWNERSHIP, "measurableOutcomes", seniorityValue, "r"),
                new SubfactorJudgement(CategoryName.SENIORITY_AND_OWNERSHIP, "leadership", seniorityValue, "r"));
    }

    public static AnalysisDraft draft(List<RequirementAssessment> assessments,
            List<SubfactorJudgement> subfactors) {

        NarrativeAssessment narrative = new NarrativeAssessment("h", "d", List.of(), List.of());
        return new AnalysisDraft(AnalysisDraft.SCHEMA_VERSION, assessments, subfactors,
                narrative, narrative, narrative,
                new OpportunityValue(
                        new Assessment(Assessment.Rating.STRONG, Assessment.Basis.INFERRED_FROM_POSTING, "e"),
                        new Assessment(Assessment.Rating.STRONG, Assessment.Basis.STATED_IN_POSTING, "e"),
                        Assessment.unknown("e")),
                new ResumePositioningAdvice(List.of(), List.of(), List.of(), List.of(), List.of()),
                new InterviewPreparation(List.of(), List.of(), List.of(), List.of()),
                "rationale", List.of());
    }
}
