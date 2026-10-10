import { ApiClientError, isApiErrorBody } from './api-error';
import { authSession } from '../../features/auth/session';

export type RequestHeadersProvider = () => HeadersInit | Promise<HeadersInit>;
export interface ApiClientConfig {
  /** Origin (without /api), or empty for same-origin requests. */
  baseUrl?: string;
  credentials?: RequestCredentials;
  fetchImpl?: typeof fetch;
  /** Set by the future authentication package, never persisted here. */
  getAuthHeaders?: RequestHeadersProvider;
  /** Supply CSRF headers from F02 for state-changing operations. */
  getCsrfHeaders?: RequestHeadersProvider;
  /** Authentication failure from a normal authenticated business request. */
  onUnauthorized?: () => void;
}

export type ApiRequestOptions = Omit<RequestInit, 'method' | 'body'> & {
  body?: unknown;
};

function isAbort(error: unknown, signal?: AbortSignal | null): boolean {
  return (
    signal?.aborted === true ||
    (error instanceof DOMException && error.name === 'AbortError') ||
    (error instanceof Error && error.name === 'AbortError')
  );
}

async function decodeJson(response: Response): Promise<unknown> {
  const raw = await response.text();
  if (!raw) return undefined;
  try {
    return JSON.parse(raw) as unknown;
  } catch {
    return undefined;
  }
}

export class ApiClient {
  private readonly config: ApiClientConfig;

  constructor(config: ApiClientConfig = {}) {
    this.config = config;
  }

  async request<T>(method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE', path: string, options: ApiRequestOptions = {}): Promise<T> {
    if (!/^\/api(?:\/|\?|$)/.test(path)) {
      throw new Error('API paths must start with /api');
    }

    const { body, ...requestOptions } = options;
    const headers = new Headers(options.headers);
    headers.set('Accept', 'application/json');

    if (body !== undefined) headers.set('Content-Type', 'application/json');
    if (this.config.getAuthHeaders) {
      new Headers(await this.config.getAuthHeaders()).forEach((value, key) => headers.set(key, value));
    }
    if (method !== 'GET' && this.config.getCsrfHeaders) {
      new Headers(await this.config.getCsrfHeaders()).forEach((value, key) => headers.set(key, value));
    }

    let response: Response;
    try {
      response = await (this.config.fetchImpl ?? fetch)(
        (this.config.baseUrl ?? '').replace(/\/+$/, '') + path,
        {
          ...requestOptions,
          method,
          headers,
          credentials: options.credentials ?? this.config.credentials ?? 'same-origin',
          // A redirect must never carry auth/session context off-origin.
          redirect: 'error',
          body: body === undefined ? undefined : JSON.stringify(body),
        },
      );
    } catch (error: unknown) {
      throw new ApiClientError(isAbort(error, options.signal) ? 'abort' : 'network', isAbort(error, options.signal) ? 'REQUEST_ABORTED' : 'NETWORK_ERROR');
    }

    // Business errors from Spring use ApiError(code, message, details).
    // Never render unchecked response HTML or backend stack traces.
    if (!response.ok) {
      if (response.status === 401) this.config.onUnauthorized?.();
      const decoded = await decodeJson(response);
      const domain = isApiErrorBody(decoded) ? decoded : undefined;
      throw new ApiClientError(
        'http',
        domain?.code ?? 'HTTP_ERROR',
        response.status,
        domain?.details ?? {},
      );
    }

    if (response.status === 204 || method === 'DELETE' && response.headers.get('content-length') === '0') {
      return undefined as T;
    }
    const data = await decodeJson(response);
    if (data === undefined) throw new ApiClientError('parse', 'INVALID_RESPONSE', response.status);
    return data as T;
  }

  get<T>(path: string, options?: ApiRequestOptions): Promise<T> {
    return this.request<T>('GET', path, options);
  }

  post<T>(path: string, body?: unknown, options: ApiRequestOptions = {}): Promise<T> {
    return this.request<T>('POST', path, { ...options, body });
  }

  put<T>(path: string, body?: unknown, options: ApiRequestOptions = {}): Promise<T> {
    return this.request<T>('PUT', path, { ...options, body });
  }

  patch<T>(path: string, body?: unknown, options: ApiRequestOptions = {}): Promise<T> {
    return this.request<T>('PATCH', path, { ...options, body });
  }

  delete<T = void>(path: string, options?: ApiRequestOptions): Promise<T> {
    return this.request<T>('DELETE', path, options);
  }
}

export const apiClient = new ApiClient({
  // F02 always sends auth headers and session cookies to our own origin.
  baseUrl: '',
  credentials: 'same-origin',
  fetchImpl: (input, init) => authSession.fetchTracked(input, init),
  getAuthHeaders: () => authSession.requiredAuthHeaders(),
  getCsrfHeaders: () => authSession.csrfHeaders(),
  onUnauthorized: () => authSession.clear(),
});
