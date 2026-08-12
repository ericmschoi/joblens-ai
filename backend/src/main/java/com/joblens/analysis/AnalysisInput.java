package com.joblens.analysis;

import com.joblens.jobposting.model.JobPosting;
import com.joblens.resume.model.CandidateProfile;

/**
 * What a provider is given: two confirmed documents, already redacted.
 *
 * <p>Both texts have been through {@link com.joblens.document.PiiRedactionService}. Nothing that
 * identifies the candidate personally reaches a provider, and the provider never sees the raw
 * upload.
 *
 * @param requirementsMustComeFromFullText true when the posting's structured lists cannot be trusted
 *        as the complete set of requirements, so decomposition has to read the whole description
 * @param absentEvidenceMustBeUnknown true when the resume is unreviewed or structurally uncertain,
 *        so a requirement with no matching evidence has to be reported unknown rather than missing
 */
public record AnalysisInput(
        String resumeText,
        CandidateProfile candidateProfile,
        String jobText,
        JobPosting jobPosting,
        boolean requirementsMustComeFromFullText,
        boolean absentEvidenceMustBeUnknown) {}
