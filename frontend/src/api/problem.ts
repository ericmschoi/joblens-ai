/**
 * Client-side mirror of the backend's RFC 9457 problem-detail contract.
 *
 * The backend guarantees that `detail` and `recoveryAction` are user-safe English copy, so they can
 * be rendered directly. Anything that does not match the contract is replaced with a generic
 * message rather than shown to the user, because an off-contract body may be produced by a proxy or
 * an error page rather than by JobLens.
 */

export interface FieldError {
  readonly field: string;
  readonly message: string;
}

export interface ProblemDetail {
  readonly type: string;
  readonly title: string;
  readonly status: number;
  readonly detail: string;
  readonly code: string;
  readonly recoveryAction: string;
  readonly fieldErrors: readonly FieldError[];
  readonly traceId: string | null;
}

const GENERIC_TITLE = 'Something went wrong';
const GENERIC_DETAIL = 'The request could not be completed.';
const GENERIC_RECOVERY = 'Try again. If the problem continues, start over from the upload step.';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function readString(source: Record<string, unknown>, key: string, fallback: string): string {
  const value = source[key];
  return typeof value === 'string' && value.length > 0 ? value : fallback;
}

function readFieldErrors(source: Record<string, unknown>): readonly FieldError[] {
  const value = source['fieldErrors'];
  if (!Array.isArray(value)) {
    return [];
  }
  return value.flatMap((entry): FieldError[] => {
    if (!isRecord(entry)) {
      return [];
    }
    const field = entry['field'];
    const message = entry['message'];
    if (typeof field !== 'string' || typeof message !== 'string') {
      return [];
    }
    return [{ field, message }];
  });
}

export function toProblemDetail(body: unknown, status: number): ProblemDetail {
  if (!isRecord(body)) {
    return {
      type: 'about:blank',
      title: GENERIC_TITLE,
      status,
      detail: GENERIC_DETAIL,
      code: 'INTERNAL_ERROR',
      recoveryAction: GENERIC_RECOVERY,
      fieldErrors: [],
      traceId: null,
    };
  }

  const traceId = body['traceId'];
  const parsedStatus = body['status'];

  return {
    type: readString(body, 'type', 'about:blank'),
    title: readString(body, 'title', GENERIC_TITLE),
    status: typeof parsedStatus === 'number' ? parsedStatus : status,
    detail: readString(body, 'detail', GENERIC_DETAIL),
    code: readString(body, 'code', 'INTERNAL_ERROR'),
    recoveryAction: readString(body, 'recoveryAction', GENERIC_RECOVERY),
    fieldErrors: readFieldErrors(body),
    traceId: typeof traceId === 'string' ? traceId : null,
  };
}

/** Thrown for every non-2xx API response so callers handle one error type. */
export class ApiError extends Error {
  readonly problem: ProblemDetail;

  constructor(problem: ProblemDetail) {
    super(problem.detail);
    this.name = 'ApiError';
    this.problem = problem;
  }
}
