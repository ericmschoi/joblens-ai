import { render, screen, within } from '@testing-library/react';
import axe from 'axe-core';
import { describe, expect, it } from 'vitest';

import App from './App';

describe('App', () => {
  it('names the product and states its purpose', () => {
    render(<App />);

    expect(screen.getByRole('heading', { level: 1, name: 'JobLens AI' })).toBeInTheDocument();
    expect(screen.getByText(/evidence-based fit analysis/i)).toBeInTheDocument();
  });

  it('presents the three steps as an ordered list', () => {
    render(<App />);

    const steps = within(screen.getByRole('list')).getAllByRole('listitem');

    expect(steps).toHaveLength(3);
    expect(steps[0]).toHaveTextContent('Upload your resume');
    expect(steps[1]).toHaveTextContent('Add the job posting');
    expect(steps[2]).toHaveTextContent('Review, then analyse');
  });

  it('states that only documented experience is evaluated', () => {
    render(<App />);

    expect(screen.getByText(/explicitly documented in the resume/i)).toBeInTheDocument();
  });

  it('has no detectable accessibility violations', async () => {
    const { container } = render(<App />);

    const results = await axe.run(container, {
      // Contrast cannot be computed in jsdom because it has no layout or paint engine.
      // The token palette is checked directly in src/styles/tokens.test.ts instead.
      rules: { 'color-contrast': { enabled: false } },
    });

    expect(results.violations.map((violation) => violation.id)).toEqual([]);
  });
});
