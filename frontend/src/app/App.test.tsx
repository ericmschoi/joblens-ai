import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import axe from 'axe-core';
import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  CONFIRMED_JOB,
  CONFIRMED_RESUME,
  JOB_EXTRACTION,
  RESUME_EXTRACTION,
  stubApi,
} from '../test/apiStub';
import App from './App';

const ALL_ENDPOINTS = {
  '/resumes/extract': { body: RESUME_EXTRACTION },
  '/resumes/confirm': { body: CONFIRMED_RESUME },
  '/job-descriptions/extract': { body: JOB_EXTRACTION },
  '/job-descriptions/confirm': { body: CONFIRMED_JOB },
};

function pdf(name = 'resume.pdf', sizeBytes = 1024) {
  return new File([new Uint8Array(sizeBytes)], name, { type: 'application/pdf' });
}

async function uploadAResume(user: ReturnType<typeof userEvent.setup>) {
  await user.upload(screen.getByLabelText('Resume PDF'), pdf());
  await user.click(screen.getByRole('button', { name: 'Read my resume' }));
  await screen.findByRole('heading', { name: /Check what we read from your resume/i });
}

describe('the wizard', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('names the product and explains what it does', () => {
    stubApi(ALL_ENDPOINTS);
    render(<App />);

    expect(screen.getByRole('heading', { level: 1, name: 'JobLens AI' })).toBeInTheDocument();
    expect(screen.getByText(/evidence-based fit analysis/i)).toBeInTheDocument();
  });

  it('starts on the resume step and says nothing is stored', () => {
    stubApi(ALL_ENDPOINTS);
    render(<App />);

    expect(screen.getByRole('heading', { name: 'Upload your resume' })).toBeInTheDocument();
    expect(screen.getByText(/closing this tab ends the session/i)).toBeInTheDocument();
  });

  it('will not submit until a file has been chosen', async () => {
    stubApi(ALL_ENDPOINTS);
    render(<App />);

    expect(screen.getByRole('button', { name: 'Read my resume' })).toBeDisabled();

    await userEvent.setup().upload(screen.getByLabelText('Resume PDF'), pdf());
    expect(screen.getByRole('button', { name: 'Read my resume' })).toBeEnabled();
  });

  it('rejects an oversized file before sending it anywhere', async () => {
    const calls = stubApi(ALL_ENDPOINTS);
    render(<App />);

    await userEvent.setup().upload(screen.getByLabelText('Resume PDF'),
      pdf('huge.pdf', 11 * 1024 * 1024));

    expect(screen.getByRole('alert')).toHaveTextContent(/larger than 10 MB/i);
    expect(calls).toHaveLength(0);
  });

  it('moves to the review step and shows the extraction warnings', async () => {
    stubApi(ALL_ENDPOINTS);
    render(<App />);

    await uploadAResume(userEvent.setup());

    const warnings = screen.getByRole('list', { name: 'Resume extraction warnings' });
    expect(within(warnings).getByText(/looks like it uses columns/i)).toBeInTheDocument();
    expect(within(warnings).getByText('Check this')).toBeInTheDocument();
  });

  it('sends the corrected text rather than the extracted text', async () => {
    const calls = stubApi(ALL_ENDPOINTS);
    const user = userEvent.setup();
    render(<App />);
    await uploadAResume(user);

    const textarea = screen.getByLabelText('Extracted text');
    await user.clear(textarea);
    await user.type(textarea, 'Corrected resume text');
    await user.click(screen.getByRole('button', { name: /This is correct/i }));

    await waitFor(() => expect(calls.some((call) => call.path === '/resumes/confirm')).toBe(true));
    const confirm = calls.find((call) => call.path === '/resumes/confirm');
    expect(confirm?.body).toMatchObject({ rawText: 'Corrected resume text', confirmed: true });
  });

  it('clears the other field when the job description mode changes', async () => {
    stubApi(ALL_ENDPOINTS);
    const user = userEvent.setup();
    render(<App />);
    await uploadAResume(user);
    await user.click(screen.getByRole('button', { name: /This is correct/i }));
    await screen.findByRole('heading', { name: 'Add the job description' });

    await user.type(screen.getByLabelText('Link to the job posting'), 'https://example.com/jobs/1');
    await user.click(screen.getByRole('radio', { name: 'Paste Job Description' }));

    expect(screen.getByLabelText('Job description')).toHaveValue('');
    expect(screen.queryByLabelText('Link to the job posting')).not.toBeInTheDocument();

    await user.click(screen.getByRole('radio', { name: 'Job URL' }));
    expect(screen.getByLabelText('Link to the job posting'))
      .toHaveValue('');
  });

  it('reports a failure with something the user can do about it', async () => {
    stubApi({
      ...ALL_ENDPOINTS,
      '/resumes/extract': {
        status: 422,
        body: {
          title: 'This PDF has no readable text',
          detail: 'This file looks like a scanned PDF.',
          code: 'PDF_IMAGE_ONLY',
          recoveryAction: 'Upload a text-based PDF exported from your word processor.',
          fieldErrors: [],
          traceId: 'abc12345',
          status: 422,
          type: 'https://joblens.local/problems/pdf-image-only',
        },
      },
    });
    const user = userEvent.setup();
    render(<App />);

    await user.upload(screen.getByLabelText('Resume PDF'), pdf());
    await user.click(screen.getByRole('button', { name: 'Read my resume' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('This PDF has no readable text');
    expect(alert).toHaveTextContent('Upload a text-based PDF');
    expect(alert).toHaveTextContent('abc12345');
  });

  it('lets the user dismiss a failure and try again', async () => {
    stubApi({ ...ALL_ENDPOINTS, '/resumes/extract': { status: 500, body: {} } });
    const user = userEvent.setup();
    render(<App />);

    await user.upload(screen.getByLabelText('Resume PDF'), pdf());
    await user.click(screen.getByRole('button', { name: 'Read my resume' }));
    await screen.findByRole('alert');

    await user.click(screen.getByRole('button', { name: 'Dismiss' }));
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Read my resume' })).toBeEnabled();
  });

  it('reaches the end of the flow with both documents confirmed', async () => {
    const calls = stubApi(ALL_ENDPOINTS);
    const user = userEvent.setup();
    render(<App />);

    await uploadAResume(user);
    await user.click(screen.getByRole('button', { name: /This is correct/i }));

    await screen.findByRole('heading', { name: 'Add the job description' });
    await user.click(screen.getByRole('radio', { name: 'Paste Job Description' }));
    await user.type(screen.getByLabelText('Job description'), 'A pasted job description.');
    await user.click(screen.getByRole('button', { name: 'Read the job description' }));

    await screen.findByRole('heading', { name: /Check what we read from the posting/i });
    expect(screen.getByLabelText('Required qualifications'))
      .toHaveValue('Strong Java\nHands-on AWS');

    await user.click(screen.getByRole('button', { name: /This is correct/i }));
    await screen.findByRole('heading', { name: /Both documents are confirmed/i });

    expect(calls.map((call) => call.path)).toEqual([
      '/resumes/extract', '/resumes/confirm', '/job-descriptions/extract', '/job-descriptions/confirm',
    ]);
  });

  it('marks progress through the steps', async () => {
    stubApi(ALL_ENDPOINTS);
    render(<App />);

    const progress = screen.getByRole('list', { name: 'Progress' });
    expect(within(progress).getByText('1. Resume')).toHaveAttribute('aria-current', 'step');

    await uploadAResume(userEvent.setup());
    expect(within(progress).getByText(/2\. Review resume/)).toHaveAttribute('aria-current', 'step');
  });

  it('has no detectable accessibility violations on the first step', async () => {
    stubApi(ALL_ENDPOINTS);
    const { container } = render(<App />);

    const results = await axe.run(container, { rules: { 'color-contrast': { enabled: false } } });

    expect(results.violations.map((violation) => violation.id)).toEqual([]);
  });

  it('has no detectable accessibility violations on the review step', async () => {
    stubApi(ALL_ENDPOINTS);
    const { container } = render(<App />);
    await uploadAResume(userEvent.setup());

    const results = await axe.run(container, { rules: { 'color-contrast': { enabled: false } } });

    expect(results.violations.map((violation) => violation.id)).toEqual([]);
  });

  it('can be driven from the keyboard alone', async () => {
    stubApi(ALL_ENDPOINTS);
    const user = userEvent.setup();
    render(<App />);

    await user.upload(screen.getByLabelText('Resume PDF'), pdf());
    await user.tab();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Read my resume' })).toHaveFocus());
    await user.keyboard('{Enter}');

    await screen.findByRole('heading', { name: /Check what we read from your resume/i });
  });
});
