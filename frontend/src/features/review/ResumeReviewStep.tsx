import { useState } from 'react';

import { confirmResume } from '../../api/joblens';
import type { CandidateProfile, WorkExperience } from '../../api/types';
import { Actions, PrimaryButton, SecondaryButton, TextAreaField, TextField } from '../../components/Fields';
import { WarningList } from '../../components/WarningList';
import { useWizard } from '../../session/WizardContext';

/**
 * Step three: check what was read, and fix it.
 *
 * Mandatory by design. PDF layouts are extracted imperfectly, and the version confirmed here is the
 * only one the analysis will ever see, so this is where a bad parse gets caught rather than
 * quietly distorting a score.
 */
export function ResumeReviewStep() {
  const { state, run, dispatch } = useWizard();
  const extraction = state.resumeExtraction;

  const [rawText, setRawText] = useState(extraction?.rawText ?? '');
  const [summary, setSummary] = useState(extraction?.candidateProfile.summary ?? '');
  const [roles, setRoles] = useState<WorkExperience[]>(
    () => [...(extraction?.candidateProfile.workExperiences ?? [])]);

  if (!extraction) {
    return null;
  }
  const busy = state.busy === 'confirmingResume';

  function updateRole(index: number, changes: Partial<WorkExperience>) {
    setRoles((current) => current.map((role, i) => (i === index ? { ...role, ...changes } : role)));
  }

  async function confirm() {
    const profile: CandidateProfile = {
      ...extraction!.candidateProfile,
      summary,
      workExperiences: roles,
    };
    await run('confirmingResume',
      () => confirmResume({
        rawText,
        candidateProfile: profile,
        carriedWarnings: extraction!.extractionWarnings,
      }),
      (confirmed) => dispatch({ type: 'resumeConfirmed', confirmed }));
  }

  return (
    <section aria-labelledby="resume-review-heading">
      <h2 id="resume-review-heading">Check what we read from your resume</h2>
      <p>
        This is what the analysis will use. Correct anything that came out wrong — the version you
        confirm is the one that gets analysed.
      </p>

      <WarningList warnings={extraction.extractionWarnings} label="Resume extraction warnings" />

      <TextAreaField
        label="Extracted text"
        value={rawText}
        onChange={setRawText}
        rows={16}
        hint="The full text as it was read. Everything the analysis quotes has to appear here."
      />

      <h3>Summary</h3>
      <TextAreaField label="Professional summary" value={summary} onChange={setSummary} rows={4} />

      <h3>Roles</h3>
      {roles.length === 0 && (
        <p>
          No roles were recognised. Add them to the extracted text above, or continue — the analysis
          will report what it cannot verify as unknown rather than assuming it is missing.
        </p>
      )}
      {roles.map((role, index) => (
        <fieldset key={role.id}>
          <legend>Role {index + 1}</legend>
          <TextField
            label="Job title"
            value={role.title ?? ''}
            onChange={(value) => updateRole(index, { title: value })}
          />
          <TextField
            label="Employer"
            value={role.company ?? ''}
            onChange={(value) => updateRole(index, { company: value })}
          />
          <TextField label="Dates" value={role.dates.rawText} onChange={(value) =>
            updateRole(index, { dates: { ...role.dates, rawText: value } })} />
          <TextAreaField
            label="What you did"
            value={role.bullets.join('\n')}
            onChange={(value) => updateRole(index, { bullets: value.split('\n') })}
            rows={5}
            hint="One point per line."
          />
        </fieldset>
      ))}

      <Actions>
        <PrimaryButton onClick={() => void confirm()} disabled={busy}>
          {busy ? 'Confirming…' : 'This is correct — continue'}
        </PrimaryButton>
        <SecondaryButton onClick={() => dispatch({ type: 'wentTo', step: 'resume' })} disabled={busy}>
          Upload a different resume
        </SecondaryButton>
      </Actions>
    </section>
  );
}
