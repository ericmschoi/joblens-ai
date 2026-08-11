package com.joblens.jobposting;

import com.joblens.document.ExtractionWarning;
import com.joblens.jobposting.model.JobPosting;
import java.util.List;

/** A structured reading of a job posting, together with what should be checked about it. */
public record ParsedJobPosting(JobPosting posting, List<ExtractionWarning> warnings) {

    public ParsedJobPosting {
        warnings = List.copyOf(warnings);
    }
}
