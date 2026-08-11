package com.joblens.resume.model;

import com.joblens.document.Provenance;
import java.util.List;

/** One role held by the candidate, as written in the resume. */
public record WorkExperience(
        String id,
        String company,
        String title,
        String location,
        DateRange dates,
        List<String> bullets,
        Provenance provenance) {

    public WorkExperience {
        bullets = List.copyOf(bullets);
    }
}
