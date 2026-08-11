package com.joblens.resume;

import com.joblens.document.ExtractionWarning;
import com.joblens.resume.model.CandidateProfile;
import java.util.List;

/** The structured reading of a resume, plus anything the user should check about that reading. */
public record NormalizedResume(CandidateProfile profile, List<ExtractionWarning> warnings) {

    public NormalizedResume {
        warnings = List.copyOf(warnings);
    }
}
