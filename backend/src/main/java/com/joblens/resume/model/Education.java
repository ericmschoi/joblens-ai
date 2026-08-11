package com.joblens.resume.model;

import com.joblens.document.Provenance;

/** One credential or programme of study, as written in the resume. */
public record Education(
        String id,
        String institution,
        String credential,
        DateRange dates,
        Provenance provenance) {}
