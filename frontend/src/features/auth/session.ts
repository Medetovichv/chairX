import { ApiClientError } from '../../shared/api/api-error';

export interface CurrentUser {
  id: string;
  username: string;
  displayName: string;
  roles: string[];
  permissions: string[];
}

export interface CsrfCredentials {
  headerName: string;
  token: string;
}

export function makeBasicHeader(username: string, password: string): string {
  // btoa alone fails on Unicode. Encode both fields as UTF-8 bytes.
  const bytes = new TextEncoder().encode(username + ':' + password);
  const binary = Array.from(bytes, (value) => String.fromCharCode(value)).join('');
  return 'Basic ' + btoa(binary);
}

export function validateUser(value: CurrentUser): CurrentUser {
  if (!value || typeof value.id !== 'string' || typeof value.username !== 'string'
      || typeof value.displayName !== 'string'
      || !Array.isArray(value.roles) || value.roles.length === 0
      || !value.roles.every((role) => typeof role === 'string')
      || !Array.isArray(value.permissions)
      || !value.permissions.every((permission) => typeof permission === 'string')) {
    throw new ApiClientError('parse', 'INVALID_PROFILE');
  }
  return value;
}

class InMemoryAuthSession {
  private authorization: string | null = null;
  private csrf: CsrfCredentials | null = null;
  private controllers = new Set<AbortController>();
  private listeners = new Set<() => void>();

  activate(authorization: string, csrf: CsrfCredentials) {
    if (!csrf.headerName || !csrf.token || !/^X-[a-z0-9-]+$/i.test(csrf.headerName)) {
      throw new ApiClientError('parse', 'INVALID_CSRF');
    }
    this.clear();
    this.authorization = authorization;
    this.csrf = csrf;
  }

  authHeaders(): HeadersInit {
    return this.authorization ? { Authorization: this.authorization } : {};
  }

  csrfHeaders(): HeadersInit {
    if (!this.authorization || !this.csrf) {
      // F02 business clients must fail closed before an unsafe HTTP request.
      throw new ApiClientError('http', 'CSRF_REQUIRED', 403);
    }
    return { [this.csrf.headerName]: this.csrf.token };
  }

  subscribe(listener: () => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  clear() {
    const wasAuthenticated = this.authorization !== null;
    this.authorization = null;
    this.csrf = null;
    for (const controller of this.controllers) controller.abort();
    this.controllers.clear();
    if (wasAuthenticated) this.listeners.forEach((listener) => listener());
  }

  async fetchTracked(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
    const controller = new AbortController();
    this.controllers.add(controller);
    const signal = init?.signal ? AbortSignal.any([init.signal, controller.signal]) : controller.signal;
    try {
      return await fetch(input, { ...init, signal });
    } finally {
      this.controllers.delete(controller);
    }
  }
}

export const authSession = new InMemoryAuthSession();
