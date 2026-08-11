import { describe, expect, it } from 'vitest';

import { ApiError, toProblemDetail } from './problem';

describe('toProblemDetail', () => {
  it('reads a well-formed problem detail from the backend', () => {
    const problem = toProblemDetail(
      {
        type: 'https://joblens.local/problems/pdf-image-only',
        title: 'This PDF has no readable text',
        status: 422,
        detail: 'This file has no extractable text.',
        code: 'PDF_IMAGE_ONLY',
        recoveryAction: 'Upload a text-based PDF.',
        fieldErrors: [],
        traceId: 'a1b2c3d4',
      },
      422,
    );

    expect(problem.code).toBe('PDF_IMAGE_ONLY');
    expect(problem.status).toBe(422);
    expect(problem.detail).toBe('This file has no extractable text.');
    expect(problem.traceId).toBe('a1b2c3d4');
  });

  it('keeps only well-formed field errors', () => {
    const problem = toProblemDetail(
      {
        code: 'VALIDATION_FAILED',
        fieldErrors: [{ field: 'title', message: 'Enter a job title.' }, { field: 42 }, 'nonsense'],
      },
      400,
    );

    expect(problem.fieldErrors).toEqual([{ field: 'title', message: 'Enter a job title.' }]);
  });

  it('falls back to generic copy when the body is not a problem detail', () => {
    const problem = toProblemDetail('<html>502 Bad Gateway</html>', 502);

    expect(problem.code).toBe('INTERNAL_ERROR');
    expect(problem.status).toBe(502);
    expect(problem.detail).toBe('The request could not be completed.');
    expect(problem.traceId).toBeNull();
  });

  it('does not surface an unexpected body as user-facing text', () => {
    const problem = toProblemDetail({ detail: '', title: '' }, 500);

    expect(problem.detail).toBe('The request could not be completed.');
    expect(problem.title).toBe('Something went wrong');
  });
});

describe('ApiError', () => {
  it('carries the problem and uses its detail as the message', () => {
    const problem = toProblemDetail({ code: 'AI_TIMEOUT', detail: 'The analysis took too long.' }, 504);
    const error = new ApiError(problem);

    expect(error).toBeInstanceOf(Error);
    expect(error.name).toBe('ApiError');
    expect(error.message).toBe('The analysis took too long.');
    expect(error.problem.code).toBe('AI_TIMEOUT');
  });
});
