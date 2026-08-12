import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import App from '../../app/App';
import {
  ANALYSIS_RESPONSE,
  CONFIRMED_JOB,
  CONFIRMED_RESUME,
  JOB_EXTRACTION,
  RESUME_EXTRACTION,
  stubApi,
} from '../../test/apiStub';

const DOCUMENTS = {
  '/resumes/extract': { body: RESUME_EXTRACTION },
  '/resumes/confirm': { body: CONFIRMED_RESUME },
  '/job-descriptions/extract': { body: JOB_EXTRACTION },
  '/job-descriptions/confirm': { body: CONFIRMED_JOB },
};

/** Both documents confirmed, stopped just before the analysis runs. */
async function reachTheAnalysisStep(user: ReturnType<typeof userEvent.setup>) {
  render(<App />);

  await user.upload(screen.getByLabelText('Resume PDF'),
    new File([new Uint8Array(1024)], 'resume.pdf', { type: 'application/pdf' }));
  await user.click(screen.getByRole('button', { name: 'Read my resume' }));
  await screen.findByRole('heading', { name: /Check what we read from your resume/i });
  await user.click(screen.getByRole('button', { name: /This is correct/i }));

  await screen.findByRole('heading', { name: 'Add the job description' });
  await user.click(screen.getByRole('radio', { name: 'Paste Job Description' }));
  await user.type(screen.getByLabelText('Job description'), 'A pasted posting.');
  await user.click(screen.getByRole('button', { name: 'Read the job description' }));

  await screen.findByRole('heading', { name: /Check what we read from the posting/i });
  await user.click(screen.getByRole('button', { name: /This is correct/i }));
  await screen.findByRole('heading', { name: 'Ready to analyse' });
}

describe('recovering from a failed analysis', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('says what went wrong and leaves the confirmed documents intact', async () => {
    const user = userEvent.setup();
    stubApi({ ...DOCUMENTS, '/analyses': { status: 503, body: {
      title: 'The analysis service is unavailable',
      detail: 'The analysis service is unavailable.',
      code: 'AI_PROVIDER_UNAVAILABLE',
      recoveryAction: 'Wait a moment and run the analysis again.',
      fieldErrors: [], traceId: 'abc12345', status: 503,
      type: 'https://joblens.local/problems/ai-provider-unavailable',
    } } });

    await reachTheAnalysisStep(user);
    await user.click(screen.getByRole('button', { name: 'Analyse the fit' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(/analysis service is unavailable/i);
    expect(screen.getByRole('button', { name: 'Analyse the fit' })).toBeEnabled();
  });

  it('succeeds on a retry without re-uploading anything', async () => {
    const user = userEvent.setup();
    const calls = stubApi({ ...DOCUMENTS, '/analyses': { status: 503, body: {} } });

    await reachTheAnalysisStep(user);
    await user.click(screen.getByRole('button', { name: 'Analyse the fit' }));
    await screen.findByRole('alert');

    stubApi({ ...DOCUMENTS, '/analyses': { body: ANALYSIS_RESPONSE } });
    await user.click(screen.getByRole('button', { name: 'Analyse the fit' }));

    await screen.findByRole('heading', { name: 'Your fit analysis' });
    // The documents were confirmed once and are held in memory; a retry re-sends only the analysis.
    expect(calls.filter((call) => call.path === '/resumes/extract')).toHaveLength(1);
    expect(calls.filter((call) => call.path === '/job-descriptions/confirm')).toHaveLength(1);
  });

  it('starting over clears the analysis and the confirmed documents', async () => {
    const user = userEvent.setup();
    stubApi({ ...DOCUMENTS, '/analyses': { body: ANALYSIS_RESPONSE } });

    await reachTheAnalysisStep(user);
    await user.click(screen.getByRole('button', { name: 'Analyse the fit' }));
    await screen.findByRole('heading', { name: 'Your fit analysis' });

    await user.click(screen.getByRole('button', { name: /Start over/i }));

    expect(screen.getByRole('heading', { name: 'Upload your resume' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Read my resume' })).toBeDisabled();
  });
});
