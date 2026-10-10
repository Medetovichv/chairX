import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { ApiClient } from '../../shared/api/client';
import { ApiClientError } from '../../shared/api/api-error';
import { authSession, makeBasicHeader, validateUser, type CurrentUser, type CsrfCredentials } from './session';

export type AuthStatus = 'unauthenticated' | 'authenticating' | 'authenticated';

export interface AuthContextValue {
  user: CurrentUser | null;
  status: AuthStatus;
  isAuthenticated: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
  hasPermission: (permission: string) => boolean;
  hasAnyPermission: (permissions: readonly string[]) => boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [status, setStatus] = useState<AuthStatus>('unauthenticated');
  const generation = useRef(0);
  const pending = useRef(false);
  const loginController = useRef<AbortController | null>(null);

  const logout = useCallback(() => {
    generation.current += 1;
    loginController.current?.abort();
    pending.current = false;
    authSession.clear();
    void queryClient.cancelQueries();
    queryClient.clear();
    setUser(null);
    setStatus('unauthenticated');
  }, [queryClient]);

  // A 401 from an authenticated business request should invalidate the UI too.
  useEffect(() => authSession.subscribe(() => {
    generation.current += 1;
    loginController.current?.abort();
    pending.current = false;
    void queryClient.cancelQueries();
    queryClient.clear();
    setUser(null);
    setStatus('unauthenticated');
  }), [queryClient]);

  useEffect(() => () => {
    generation.current += 1;
    loginController.current?.abort();
    authSession.clear();
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    if (pending.current) return;
    pending.current = true;
    const attempt = ++generation.current;
    setStatus('authenticating');
    const controller = new AbortController();
    loginController.current = controller;
    try {
      const authorization = makeBasicHeader(username.trim(), password);
      // This client has no persisted session and no onUnauthorized callback.
      const candidateClient = new ApiClient({ credentials: 'same-origin' });
      const options = { headers: { Authorization: authorization }, signal: controller.signal };
      const candidate = validateUser(await candidateClient.get<CurrentUser>('/api/auth/me', options));
      const csrf = await candidateClient.get<CsrfCredentials>('/api/csrf', options);
      if (attempt !== generation.current || controller.signal.aborted) {
        throw new ApiClientError('abort', 'REQUEST_ABORTED');
      }
      authSession.activate(authorization, csrf);
      // Discard anything from the previous employee, including private cache.
      await queryClient.cancelQueries();
      queryClient.clear();
      setUser(candidate);
      setStatus('authenticated');
    } catch (error) {
      if (attempt === generation.current) {
        authSession.clear();
        void queryClient.cancelQueries();
        queryClient.clear();
        setUser(null);
        setStatus('unauthenticated');
      }
      throw error;
    } finally {
      if (attempt === generation.current) pending.current = false;
      if (loginController.current === controller) loginController.current = null;
    }
  }, [queryClient]);

  const hasPermission = useCallback(
    (permission: string) => user?.permissions.includes(permission) ?? false, [user]);
  const hasAnyPermission = useCallback(
    (permissions: readonly string[]) => permissions.some((permission) => hasPermission(permission)), [hasPermission]);

  return (
    <AuthContext.Provider value={{
      user, status, isAuthenticated: status === 'authenticated' && user !== null,
      login, logout, hasPermission, hasAnyPermission,
    }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('AuthProvider is required');
  return context;
}
