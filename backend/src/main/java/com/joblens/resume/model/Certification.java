package com.joblens.resume.model;

import com.joblens.document.Provenance;

/** One certification or licence listed in the resume. */
public record Certification(String id, String name, Provenance provenance) {}
