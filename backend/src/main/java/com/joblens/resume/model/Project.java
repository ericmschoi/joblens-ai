package com.joblens.resume.model;

import com.joblens.document.Provenance;
import java.util.List;

/** One project described in the resume. */
public record Project(String id, String name, List<String> bullets, Provenance provenance) {

    public Project {
        bullets = List.copyOf(bullets);
    }
}
