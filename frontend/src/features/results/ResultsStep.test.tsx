import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import axe from 'axe-core';
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

const ENDPOINTS = {
  '/resumes/extract': { body: RESUME_EXTRACTION },
  '/resumes/confirm': { body: CONFIRMED_RESUME },
  '/job-descriptions/extract': { body: JOB_EXTRACTION },
  '/job-descriptions/confirm': { body: CONFIRMED_JOB },
  '/analyses': { body: ANALYSIS_RESPONSE },
};

/** Walks the wizard to the results, so each test below can be about the results alone. */
async function reachTheResults(user: ReturnType<typeof userEvent.setup>) {
  stubApi(ENDPOINTS);
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
  await user.click(screen.getByRole('button', { name: 'Analyse the fit' }));
  await screen.findByRole('heading', { name: 'Your fit analysis' });
}

/** The section a heading introduces, which is what most of these assertions are about. */
function section(name: string) {
  return screen.getByRole('region', { name });
}

describe('the results page', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows the interpretation guide before any individual score', async () => {
    await reachTheResults(userEvent.setup());

    const guide = screen.getByRole('heading', { name: 'How to read these ratings' });
    const overall = screen.getByRole('heading', { name: 'Overall match' });

    expect(guide.compareDocumentPosition(overall)).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
  });

  it('lists every rating band and the caveat that goes with them', async () => {
    await reachTheResults(userEvent.setup());

    const guide = within(section('How to read these ratings'));
    expect(guide.getAllByRole('row')).toHaveLength(7);
    expect(guide.getByText('Excellent Match')).toBeInTheDocument();
    expect(guide.getByText('Poor Match')).toBeInTheDocument();
    expect(guide.getByText(/explicitly documented in the resume/i)).toBeInTheDocument();
  });

  it('states the exact score as text, not only as stars', async () => {
    await reachTheResults(userEvent.setup());

    const overall = within(section('Overall match'));
    expect(overall.getByText('3.4 / 5.0')).toBeInTheDocument();
    expect(overall.getByText(/Moderate Match/)).toBeInTheDocument();
  });

  it('gives the star rating an accessible name carrying the value', async () => {
    await reachTheResults(userEvent.setup());

    expect(screen.getByRole('img', { name: '3.4 out of 5' })).toBeInTheDocument();
  });

  it('draws the stars to the decimal rather than the nearest half', async () => {
    await reachTheResults(userEvent.setup());

    const stars = screen.getByRole('img', { name: '3.4 out of 5' });

    // 3.4 of 5 is 68 percent. Rounding to the nearest half star would draw 70.
    expect(stars.querySelector('clipPath rect')).toHaveAttribute('width', '68');
  });

  it('explains why the score is capped, with the numbers before and after', async () => {
    await reachTheResults(userEvent.setup());

    const notice = screen.getByRole('note');
    expect(notice).toHaveTextContent('A core required qualification has no supporting evidence');
    expect(notice).toHaveTextContent('The weighted total was 3.8');
    expect(notice).toHaveTextContent('shown as 3.4');
  });

  it('shows the tier, recommendation, confidence and eligibility beside the score', async () => {
    await reachTheResults(userEvent.setup());

    const overall = within(section('Overall match'));
    expect(overall.getByText('Application tier')).toBeInTheDocument();
    expect(overall.getByText('REACH')).toBeInTheDocument();
    expect(overall.getByText('Apply with conditions')).toBeInTheDocument();
    expect(overall.getByText('MEDIUM')).toBeInTheDocument();
    expect(overall.getByText(/not a prediction about being hired/i)).toBeInTheDocument();
  });

  it('says an unknown eligibility has not affected the score', async () => {
    await reachTheResults(userEvent.setup());

    expect(within(section('Overall match')).getByText(/Nothing in the documents settles this/i))
      .toBeInTheDocument();
  });

  it('renders every category card in weight order', async () => {
    await reachTheResults(userEvent.setup());

    const cards = screen.getAllByRole('article');
    expect(cards).toHaveLength(6);
    expect(cards[0]).toHaveTextContent('Core Technical Stack');
    expect(cards[0]).toHaveTextContent('Counts for 25% of the total.');
    expect(cards[5]).toHaveTextContent('Domain & Operating Environment');
  });

  it('says plainly when a category carried no weight', async () => {
    await reachTheResults(userEvent.setup());

    const domain = screen.getAllByRole('article')
      .find((card) => card.textContent?.includes('Domain & Operating Environment'));

    expect(domain).toHaveTextContent('Not rated, and given no weight in the total.');
  });

  it('keeps the reasoning available without forcing it on the reader', async () => {
    const user = userEvent.setup();
    await reachTheResults(user);

    const card = screen.getAllByRole('article')[0]!;
    const details = card.querySelector('details');
    expect(details).not.toHaveAttribute('open');
    expect(within(card).getByText(/maps to 4\.2/)).toBeInTheDocument();

    await user.click(within(card).getByText('Why this score'));
    expect(details).toHaveAttribute('open');
  });

  it('puts every disclosure in the tab order', async () => {
    const user = userEvent.setup();
    await reachTheResults(user);

    // Reaching a <summary> by tabbing is the whole reason the disclosure is native rather than a
    // div with a click handler. jsdom does not implement the Enter activation a browser gives it
    // for free, so the toggle itself is covered by the test above.
    const summaries = screen.getAllByText('Why this score');
    expect(summaries).toHaveLength(6);

    summaries[0]!.focus();
    expect(summaries[0]).toHaveFocus();

    await user.tab();
    expect(summaries[0]).not.toHaveFocus();
  });

  it('keeps gaps and unknowns in separate sections', async () => {
    await reachTheResults(userEvent.setup());

    const gaps = within(section('Critical gaps'));
    expect(gaps.getByText('Hands-on experience with AWS')).toBeInTheDocument();
    expect(gaps.queryByText('Kubernetes in production')).not.toBeInTheDocument();

    const unknowns = within(section('Not stated in your resume'));
    expect(unknowns.getByText('Kubernetes in production')).toBeInTheDocument();
    expect(unknowns.getByText(/None of these has counted against you/i)).toBeInTheDocument();
  });

  it('quotes evidence with where it came from', async () => {
    await reachTheResults(userEvent.setup());

    const strongest = section('Strongest matches');
    expect(strongest).toHaveTextContent('Shipped a payments service in Java.');
    expect(strongest).toHaveTextContent('From EXPERIENCE');
  });

  it('separates opportunity value from fit and says it did not affect the score', async () => {
    await reachTheResults(userEvent.setup());

    const opportunity = section('About the opportunity itself');
    expect(opportunity).toHaveTextContent(/none of it has affected the rating above/i);
    expect(opportunity).toHaveTextContent('JobLens does not research companies.');

    const overall = screen.getByRole('heading', { name: 'Overall match' });
    expect(overall.compareDocumentPosition(opportunity))
      .toBe(Node.DOCUMENT_POSITION_FOLLOWING);
  });

  it('promises not to invent experience in the positioning advice', async () => {
    await reachTheResults(userEvent.setup());

    expect(within(section('Positioning your resume for this role'))
      .getByText(/None of them adds experience you do not have/i)).toBeInTheDocument();
  });

  it('reports the limitations and the discarded evidence', async () => {
    await reachTheResults(userEvent.setup());

    const limitations = section('Limitations');
    expect(limitations).toHaveTextContent('A score ceiling was applied.');
    expect(limitations).toHaveTextContent('1 quoted item was discarded');
  });

  it('moves focus to the results heading when they arrive', async () => {
    await reachTheResults(userEvent.setup());

    expect(screen.getByRole('heading', { name: 'Your fit analysis' })).toHaveFocus();
  });

  it('has no detectable accessibility violations', async () => {
    await reachTheResults(userEvent.setup());

    const results = await axe.run(document.body, {
      rules: { 'color-contrast': { enabled: false } },
    });

    expect(results.violations.map((violation) => violation.id)).toEqual([]);
  });
});
