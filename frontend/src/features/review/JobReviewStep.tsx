import { useState } from 'react';

import { confirmJobDescription } from '../../api/joblens';
import type { JobPosting } from '../../api/types';
import { Actions, PrimaryButton, SecondaryButton, TextAreaField, TextField } from '../../components/Fields';
import { WarningList } from '../../components/WarningList';
import { useWizard } from '../../session/WizardContext';

const toLines = (values: readonly string[]) => values.join('\n');
const fromLines = (value: string) => value.split('\n').map((line) => line.trim()).filter(Boolean);

/**
 * Step four: check the posting.
 *
 * The split between required and preferred is the most consequential thing on this screen. Required
 * items are weighted far more heavily and are the only ones that can cap a score, so moving one
 * between the two lists changes the answer — which is exactly why the user gets to do it.
 */
export function JobReviewStep() {
  const { state, run, dispatch } = useWizard();
  const extraction = state.jobExtraction;

  const [rawText, setRawText] = useState(extraction?.rawText ?? '');
  const [posting, setPosting] = useState<JobPosting>(
    () => extraction?.jobPosting ?? ({} as JobPosting));

  if (!extraction) {
    return null;
  }
  const busy = state.busy === 'confirmingJob';

  function update(changes: Partial<JobPosting>) {
    setPosting((current) => ({ ...current, ...changes }));
  }

  async function confirm() {
    await run('confirmingJob',
      () => confirmJobDescription({
        rawText,
        jobPosting: posting,
        carriedWarnings: extraction!.extractionWarnings,
      }),
      (confirmed) => dispatch({ type: 'jobConfirmed', confirmed }));
  }

  return (
    <section aria-labelledby="job-review-heading">
      <h2 id="job-review-heading">Check what we read from the posting</h2>
      <p>
        Required qualifications count for far more than preferred ones, and only required ones can
        cap a score. If the posting was read wrongly, move things between the lists below.
      </p>

      <WarningList warnings={extraction.extractionWarnings} label="Job description warnings" />

      <TextField label="Job title" value={posting.title ?? ''}
        onChange={(value) => update({ title: value })} />
      <TextField label="Company" value={posting.company ?? ''}
        onChange={(value) => update({ company: value })} />
      <TextField label="Location" value={posting.location ?? ''}
        onChange={(value) => update({ location: value })} />

      <TextAreaField
        label="Required qualifications"
        value={toLines(posting.requiredQualifications ?? [])}
        onChange={(value) => update({ requiredQualifications: fromLines(value) })}
        rows={8}
        hint="One per line. These are weighted most heavily."
      />
      <TextAreaField
        label="Preferred qualifications"
        value={toLines(posting.preferredQualifications ?? [])}
        onChange={(value) => update({ preferredQualifications: fromLines(value) })}
        rows={6}
        hint="One per line. Nice to have rather than essential."
      />
      <TextAreaField
        label="Responsibilities"
        value={toLines(posting.responsibilities ?? [])}
        onChange={(value) => update({ responsibilities: fromLines(value) })}
        rows={6}
        hint="One per line."
      />

      <TextAreaField
        label="Full job description text"
        value={rawText}
        onChange={setRawText}
        rows={12}
        hint="Used when the sections above could not be separated reliably."
      />

      <Actions>
        <PrimaryButton onClick={() => void confirm()} disabled={busy}>
          {busy ? 'Confirming…' : 'This is correct — continue'}
        </PrimaryButton>
        <SecondaryButton onClick={() => dispatch({ type: 'wentTo', step: 'job' })} disabled={busy}>
          Use a different job description
        </SecondaryButton>
      </Actions>
    </section>
  );
}
