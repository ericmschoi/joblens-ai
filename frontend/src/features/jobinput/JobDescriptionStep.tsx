import { useRef, useState } from 'react';

import { extractJobDescription } from '../../api/joblens';
import { Actions, PrimaryButton, SecondaryButton, TextAreaField, TextField } from '../../components/Fields';
import { useWizard } from '../../session/WizardContext';

type Mode = 'url' | 'text';

/**
 * Step two: the job description, by link or by pasting.
 *
 * Switching mode clears the other field. Leaving a stale URL behind while the user pastes text is
 * how you end up analysing a posting nobody chose.
 */
export function JobDescriptionStep() {
  const { state, run, dispatch } = useWizard();
  const [mode, setMode] = useState<Mode>('url');
  const [url, setUrl] = useState('');
  const [text, setText] = useState('');
  const controller = useRef<AbortController | null>(null);

  const busy = state.busy === 'extractingJob';
  const canSubmit = mode === 'url' ? url.trim().length > 0 : text.trim().length > 0;

  function switchTo(next: Mode) {
    setMode(next);
    setUrl('');
    setText('');
  }

  async function submit() {
    if (!canSubmit || busy) {
      return;
    }
    controller.current = new AbortController();
    const source = mode === 'url' ? { url: url.trim() } : { text };
    await run('extractingJob',
      () => extractJobDescription(source, controller.current?.signal),
      (extraction) => dispatch({ type: 'jobExtracted', extraction }));
  }

  return (
    <section aria-labelledby="job-input-heading">
      <h2 id="job-input-heading">Add the job description</h2>
      <p>Give JobLens a link to the posting, or paste the description itself.</p>

      <div role="group" aria-label="How to provide the job description">
        <label>
          <input
            type="radio"
            name="job-source"
            value="url"
            checked={mode === 'url'}
            onChange={() => switchTo('url')}
            disabled={busy}
          />
          Job URL
        </label>
        <label>
          <input
            type="radio"
            name="job-source"
            value="text"
            checked={mode === 'text'}
            onChange={() => switchTo('text')}
            disabled={busy}
          />
          Paste Job Description
        </label>
      </div>

      {mode === 'url' ? (
        <TextField
          label="Link to the job posting"
          value={url}
          onChange={setUrl}
          hint="A public link to the posting, starting with https://. Some sites block automated
                reading; if that happens, paste the description instead."
        />
      ) : (
        <TextAreaField
          label="Job description"
          value={text}
          onChange={setText}
          rows={14}
          hint="Paste the posting itself, including responsibilities and qualifications."
        />
      )}

      <Actions>
        <PrimaryButton onClick={() => void submit()} disabled={!canSubmit || busy}>
          {busy ? 'Reading the posting…' : 'Read the job description'}
        </PrimaryButton>
        {busy && (
          <SecondaryButton onClick={() => controller.current?.abort()}>Cancel</SecondaryButton>
        )}
      </Actions>

      {busy && <p role="status">Reading the job description.</p>}
    </section>
  );
}
