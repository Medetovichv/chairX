import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiClient } from '../shared/api/client';
import { authSession, makeBasicHeader, validateUser } from '../features/auth/session';
import { ApiClientError } from '../shared/api/api-error';

afterEach(() => {
  authSession.clear();
  vi.unstubAllGlobals();
});

describe('F02 same-origin HTTP Basic and CSRF API', () => {
  it('encodes username and password in real UTF-8, not Latin-1', () => {
    const header = makeBasicHeader('user', 'пароль');
    const raw = atob(header.slice('Basic '.length));
    expect(new TextDecoder().decode(Uint8Array.from(raw, (c) => c.charCodeAt(0)))).toBe('user:пароль');
  });

  it('requires a valid profile and refuses roleless profile data', () => {
    expect(() => validateUser({
      id: '1', username: 'staff', displayName: 'Staff', roles: [], permissions: [],
    })).toThrow();
    expect(() => validateUser({
      id: '1', username: 'staff', displayName: 'Staff', roles: ['EMPLOYEE'], permissions: [],
    })).not.toThrow();
  });

  it.each(['POST', 'PUT', 'PATCH', 'DELETE'] as const)(
    'attaches backend-provided CSRF header to %s', async (method) => {
      authSession.activate('Basic secret-in-memory', { headerName: 'X-CUSTOM-CSRF', token: 'csrf-secret' });
      const fetchMock = vi.fn(async () => new Response(JSON.stringify({ saved: true }), { status: 200 }));
      vi.stubGlobal('fetch', fetchMock);
      await apiClient.request(method, '/api/products', { body: { a: 1 } });
      expect(fetchMock).toHaveBeenCalledOnce();
      const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
      expect(url).toBe('/api/products');
      expect(init.credentials).toBe('same-origin');
      const headers = new Headers(init.headers);
      expect(headers.get('X-CUSTOM-CSRF')).toBe('csrf-secret');
      expect(headers.get('Authorization')).toBe('Basic secret-in-memory');
    },
  );

  it('blocks unsafe requests before network if CSRF or authentication is missing', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    await expect(apiClient.post('/api/finance/transfers', { amount: 5 }))
      .rejects.toMatchObject({ code: 'AUTHENTICATION_REQUIRED', status: 401 });
    expect(fetchMock).not.toHaveBeenCalled();
  });


  it('never issues a mutating request when an active account has no valid CSRF header', async () => {
    authSession.activate('Basic secret-in-memory', { headerName: 'X-CSRF-TOKEN', token: 'csrf' });
    const csrfUnavailable = vi.spyOn(authSession, 'csrfHeaders')
      .mockImplementation(() => { throw new ApiClientError('http', 'CSRF_REQUIRED', 403); });
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    await expect(apiClient.post('/api/sales', { amount: 5 }))
      .rejects.toMatchObject({ code: 'CSRF_REQUIRED', status: 403 });
    expect(fetchMock).not.toHaveBeenCalled();
    csrfUnavailable.mockRestore();
  });

  it('does not send Authorization headers after logout or clear private CSRF', async () => {
    authSession.activate('Basic secret', { headerName: 'X-CUSTOM-CSRF', token: 'csrf' });
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({ ok: true })));
    vi.stubGlobal('fetch', fetchMock);
    authSession.clear();
    expect(authSession.authHeaders()).toEqual({});
    await expect(apiClient.put('/api/sales/1', { a: 1 }))
      .rejects.toMatchObject({ code: 'AUTHENTICATION_REQUIRED' });
    await expect(apiClient.get('/api/sales'))
      .rejects.toMatchObject({ code: 'AUTHENTICATION_REQUIRED', status: 401 });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('a 401 clears authenticated session but a 403 does not', async () => {
    authSession.activate('Basic secret', { headerName: 'X-CUSTOM-CSRF', token: 'csrf' });
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ code: 'ACCESS_DENIED', details: {} }), { status: 403 })));
    await expect(apiClient.get('/api/private')).rejects.toMatchObject({ status: 403 });
    expect(authSession.authHeaders()).toEqual({ Authorization: 'Basic secret' });
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ code: 'AUTHENTICATION_REQUIRED', details: {} }), { status: 401 })));
    await expect(apiClient.get('/api/private')).rejects.toMatchObject({ status: 401 });
    expect(authSession.authHeaders()).toEqual({});
  });

  it('cancels in-flight authenticated requests on logout', async () => {
    authSession.activate('Basic secret', { headerName: 'X-CUSTOM-CSRF', token: 'csrf' });
    let capturedSignal: AbortSignal | undefined;
    vi.stubGlobal('fetch', vi.fn(async (_url: RequestInfo | URL, init?: RequestInit) => {
      capturedSignal = init?.signal ?? undefined;
      return new Promise<Response>(() => {});
    }));
    void apiClient.get('/api/finance/accounts');
    await vi.waitFor(() => expect(capturedSignal).toBeDefined());
    authSession.clear();
    expect(capturedSignal?.aborted).toBe(true);
  });

  it('never persists credentials in browser storage', () => {
    const local = vi.spyOn(Storage.prototype, 'setItem');
    authSession.activate('Basic secret', { headerName: 'X-CUSTOM-CSRF', token: 'csrf' });
    authSession.clear();
    expect(local).not.toHaveBeenCalled();
    local.mockRestore();
  });
});
