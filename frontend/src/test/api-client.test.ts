import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiClient } from '../shared/api/client';
import { ApiClientError, userFacingApiError } from '../shared/api/api-error';

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('API Client', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('supports GET JSON, signal, base URL and credentials', async () => {
    const mockFetch = vi.fn(async (_url: RequestInfo | URL, _init?: RequestInit) => jsonResponse({ value: 42 }));
    const client = new ApiClient({ fetchImpl: mockFetch, baseUrl: 'http://localhost:8080/', credentials: 'include' });
    const abort = new AbortController();
    expect(await client.get<{ value: number }>('/api/products', { signal: abort.signal })).toEqual({ value: 42 });
    expect(mockFetch).toHaveBeenCalledOnce();
    expect(mockFetch.mock.calls[0]?.[0]).toBe('http://localhost:8080/api/products');
    expect(mockFetch.mock.calls[0]?.[1]?.signal).toBe(abort.signal);
    expect(mockFetch.mock.calls[0]?.[1]?.credentials).toBe('include');
  });

  it.each(['POST', 'PUT', 'PATCH', 'DELETE'] as const)('supports %s and auth/CSRF extension hooks', async (method) => {
    const mockFetch = vi.fn(async (_url: RequestInfo | URL, _init?: RequestInit) => jsonResponse({ ok: true }));
    const auth = vi.fn(() => ({ Authorization: 'Basic test-only-token' }));
    const csrf = vi.fn(() => ({ 'X-XSRF-TOKEN': 'test-only-csrf' }));
    const client = new ApiClient({ fetchImpl: mockFetch, getAuthHeaders: auth, getCsrfHeaders: csrf });
    expect(await client.request<{ ok: boolean }>(method, '/api/example', { body: { key: 'value' } })).toEqual({ ok: true });
    const init = mockFetch.mock.calls[0]?.[1];
    expect(init?.method).toBe(method);
    expect(init?.body).toBe('{"key":"value"}');
    expect(new Headers(init?.headers).get('X-XSRF-TOKEN')).toBe('test-only-csrf');
    expect(new Headers(init?.headers).get('Authorization')).toBe('Basic test-only-token');
    expect(auth).toHaveBeenCalledOnce();
    expect(csrf).toHaveBeenCalledOnce();
  });

  it.each([
    [400, 'VALIDATION_ERROR', 'введённые данные'],
    [401, 'AUTHENTICATION_REQUIRED', 'авторизация'],
    [403, 'ACCESS_DENIED', 'недостаточно прав'],
    [404, 'NOT_FOUND', 'не найден'],
    [409, 'INVENTORY_SHORTAGE', 'противоречит'],
    [500, 'INTERNAL_ERROR', 'ошибка сервера'],
  ])('maps HTTP %s safely', async (status, code, fragment) => {
    const client = new ApiClient({
      fetchImpl: vi.fn(async () => jsonResponse({
        code,
        message: 'java.lang.Exception: sensitive stack trace',
        details: { field: 'test' },
      }, status)),
    });
    let caught: unknown;
    try {
      await client.get('/api/products');
    } catch (error) {
      caught = error;
    }
    expect(caught).toBeInstanceOf(ApiClientError);
    const apiError = caught as ApiClientError;
    expect(apiError.status).toBe(status);
    expect(apiError.code).toBe(code);
    expect(apiError.details).toEqual({ field: 'test' });
    expect(userFacingApiError(apiError)).toContain(fragment);
    expect(userFacingApiError(apiError)).not.toContain('java.lang');
  });

  it('handles non-JSON errors without revealing HTML', async () => {
    const client = new ApiClient({ fetchImpl: vi.fn(async () => new Response('<html>bad</html>', { status: 500 })) });
    await expect(client.get('/api/sales')).rejects.toMatchObject({ kind: 'http', status: 500, code: 'HTTP_ERROR' });
  });

  it('reports network failure and abort distinctly', async () => {
    const network = new ApiClient({ fetchImpl: vi.fn(async () => { throw new TypeError('Failed to fetch'); }) });
    await expect(network.get('/api/sales')).rejects.toMatchObject({ kind: 'network', code: 'NETWORK_ERROR' });
    const abort = new AbortController();
    abort.abort();
    const aborted = new ApiClient({ fetchImpl: vi.fn(async () => { throw new DOMException('aborted', 'AbortError'); }) });
    await expect(aborted.get('/api/sales', { signal: abort.signal })).rejects.toMatchObject({ kind: 'abort', code: 'REQUEST_ABORTED' });
  });

  it('rejects malformed JSON success and non-/api paths', async () => {
    const client = new ApiClient({ fetchImpl: vi.fn(async () => new Response('not json')) });
    await expect(client.get('/api/products')).rejects.toMatchObject({ kind: 'parse', code: 'INVALID_RESPONSE' });
    await expect(client.get('/api-invalid')).rejects.toThrow('/api');
  });

  it('does not store secrets or passwords in browser persistent storage', async () => {
    const local = vi.spyOn(Storage.prototype, 'setItem');
    const client = new ApiClient({ fetchImpl: vi.fn(async () => jsonResponse({ ok: true })) });
    await client.post('/api/test', { value: 'example' });
    expect(local).not.toHaveBeenCalled();
    local.mockRestore();
  });
});

