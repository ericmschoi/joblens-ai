import { ApiError, toProblemDetail } from './problem';

/**
 * Thin fetch wrapper for the JobLens API.
 *
 * Every call is cancellable so the UI can offer a working cancel action and so an unmounted screen
 * cannot resolve into stale state. Credentials are never sent: the API is stateless and has no
 * cookies or sessions.
 */

export const API_BASE = '/api/v1';

export interface RequestOptions {
  readonly signal?: AbortSignal;
}

async function readBody(response: Response): Promise<unknown> {
  const contentType = response.headers.get('content-type') ?? '';
  if (!contentType.includes('json')) {
    return null;
  }
  try {
    return (await response.json()) as unknown;
  } catch {
    return null;
  }
}

async function request<T>(path: string, init: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    credentials: 'omit',
    headers: { Accept: 'application/json', ...init.headers },
  });

  const body = await readBody(response);

  if (!response.ok) {
    throw new ApiError(toProblemDetail(body, response.status));
  }

  return body as T;
}

export function getJson<T>(path: string, options: RequestOptions = {}): Promise<T> {
  return request<T>(path, options.signal ? { method: 'GET', signal: options.signal } : { method: 'GET' });
}

export function postJson<T>(path: string, payload: unknown, options: RequestOptions = {}): Promise<T> {
  const init: RequestInit = {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  };
  return request<T>(path, options.signal ? { ...init, signal: options.signal } : init);
}

export function postFormData<T>(path: string, form: FormData, options: RequestOptions = {}): Promise<T> {
  // Content-Type is intentionally omitted so the browser sets the multipart boundary.
  const init: RequestInit = { method: 'POST', body: form };
  return request<T>(path, options.signal ? { ...init, signal: options.signal } : init);
}
