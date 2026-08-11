package com.joblens.resume.model;

import java.util.List;

/**
 * The structured view of a resume that the user reviews and corrects before any analysis runs.
 *
 * <p>This is a normalization of the extracted text, not an interpretation of it. Nothing here is
 * inferred beyond what the document states, and the raw text is always returned alongside it so the
 * user can see what the structure was derived from.
 */
public record CandidateProfile(
        String summary,
        List<SkillMention> skills,
        List<WorkExperience> workExperiences,
        List<Education> education,
        List<Project> projects,
        List<Certification> certifications) {

    public CandidateProfile {
        skills = List.copyOf(skills);
        workExperiences = List.copyOf(workExperiences);
        education = List.copyOf(education);
        projects = List.copyOf(projects);
        certifications = List.copyOf(certifications);
    }

    public static CandidateProfile empty() {
        return new CandidateProfile("", List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
