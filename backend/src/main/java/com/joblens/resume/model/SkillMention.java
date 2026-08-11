package com.joblens.resume.model;

import com.joblens.document.Provenance;

/**
 * One occurrence of a skill in the resume, together with where it was found.
 *
 * <p>{@link Origin} is the reason this type exists. A technology listed only in a skills section is
 * weaker evidence than the same technology described inside a role or a project, and the scoring
 * rules depend on being able to tell those apart deterministically rather than trusting a model's
 * impression.
 */
public record SkillMention(String id, String name, Origin origin, Provenance provenance) {

    public enum Origin {
        SKILLS_LIST,
        SUMMARY,
        WORK_EXPERIENCE,
        PROJECT,
        CERTIFICATION
    }
}
