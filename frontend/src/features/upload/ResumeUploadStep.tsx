import { useRef, useState, type ChangeEvent } from 'react';

import { extractResume } from '../../api/joblens';
import { Actions, PrimaryButton, SecondaryButton } from '../../components/Fields';
import { useWizard } from '../../session/WizardContext';

const MAX_FILE_BYTES = 10 * 1024 * 1024;

/**
 * Step one: the resume.
 *
 * The obvious problems are caught here so the user hears about them immediately, but the server
 * checks everything again — a client-side check is a courtesy, never a control.
 */
export function ResumeUploadStep() {
  const { state, run, dispatch } = useWizard();
  const [file, setFile] = useState<File | null>(null);
  const [localError, setLocalError] = useState<string | null>(null);
  const controller = useRef<AbortController | null>(null);

  const busy = state.busy === 'extractingResume';

  function chooseFile(event: ChangeEvent<HTMLInputElement>) {
    const chosen = event.target.files?.[0] ?? null;
    setLocalError(null);
    if (chosen && chosen.size > MAX_FILE_BYTES) {
      setLocalError('This file is larger than 10 MB. Upload a smaller PDF.');
      setFile(null);
      return;
    }
    setFile(chosen);
  }

  async function upload() {
    if (!file || busy) {
      return;
    }
    controller.current = new AbortController();
    await run('extractingResume', () => extractResume(file, controller.current?.signal),
      (extraction) => dispatch({ type: 'resumeExtracted', extraction }));
  }

  return (
    <section aria-labelledby="resume-upload-heading">
      <h2 id="resume-upload-heading">Upload your resume</h2>
      <p>
        A text-based PDF, up to 10 MB. JobLens reads only what the file already contains, and the
        file is never stored.
      </p>

      <div>
        <label htmlFor="resume-file">Resume PDF</label>
        <input
          id="resume-file"
          type="file"
          accept="application/pdf,.pdf"
          onChange={chooseFile}
          disabled={busy}
        />
      </div>

      {localError && <p role="alert">{localError}</p>}

      <Actions>
        <PrimaryButton onClick={() => void upload()} disabled={!file || busy}>
          {busy ? 'Reading your resume…' : 'Read my resume'}
        </PrimaryButton>
        {busy && (
          <SecondaryButton onClick={() => controller.current?.abort()}>Cancel</SecondaryButton>
        )}
      </Actions>

      {busy && <p role="status">Reading your resume. This usually takes a moment.</p>}
    </section>
  );
}
