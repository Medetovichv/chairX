import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router';
import { AuthProvider } from '../features/auth/AuthProvider';
import { authSession } from '../features/auth/session';
import { AppRouter } from '../app/router/AppRouter';
import { createQueryClient } from '../shared/api/query';

const profile = {
  id: 'f02-user-id',
  username: 'employee',
  displayName: 'Тестовый сотрудник',
  roles: ['EMPLOYEE'],
  permissions: ['CATALOG_READ', 'SALES_READ', 'INVENTORY_READ'],
};
const csrf = { headerName: 'X-CSRF-TOKEN', token: 'token-from-server' };

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function mockBackend(
  user = profile,
  option: { meStatus?: number; csrfStatus?: number; network?: boolean } = {},
) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, _init?: RequestInit) => {
    if (option.network) throw new TypeError('Connection refused');
    const path = String(input);
    if (path === '/api/auth/me') {
      if (option.meStatus) return json({ code: 'AUTHENTICATION_REQUIRED', details: {} }, option.meStatus);
      return json(user);
    }
    if (path === '/api/csrf') {
      return option.csrfStatus
        ? json({ code: 'ACCESS_DENIED', details: {} }, option.csrfStatus)
        : json(csrf);
    }
    return json({ ok: true });
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function renderApp(initialPath = '/login') {
  const queryClient = createQueryClient();
  const view = render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <MemoryRouter initialEntries={[initialPath]}>
          <AppRouter />
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  );
  return { ...view, queryClient };
}

async function enterCredentials(username = 'employee', password = 'SecretPassword123!') {
  const user = userEvent.setup();
  await user.type(screen.getByRole('textbox', { name: 'Логин' }), username);
  await user.type(screen.getByLabelText('Пароль'), password);
  await user.click(screen.getByRole('button', { name: 'Войти' }));
  return user;
}

afterEach(() => {
  authSession.clear();
  vi.unstubAllGlobals();
});

describe('F02 sign-in, protected routes and session isolation', () => {
  it('redirects unauthenticated deep links to login without making backend calls', async () => {
    const fetchMock = mockBackend();
    renderApp('/finance');
    expect(screen.getByRole('heading', { name: 'Вход для сотрудников' })).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('displays required-field validation, show/hide password and loading indicator', async () => {
    const fetchMock = mockBackend();
    renderApp();
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Войти' }));
    expect(screen.getByRole('alert')).toHaveTextContent('Введите логин и пароль.');
    expect(fetchMock).not.toHaveBeenCalled();

    await user.type(screen.getByRole('textbox', { name: 'Логин' }), 'employee');
    await user.type(screen.getByLabelText('Пароль'), 'SecretPassword123!');
    expect(screen.getByLabelText('Пароль')).toHaveAttribute('type', 'password');
    await user.click(screen.getByRole('button', { name: 'Показать пароль' }));
    expect(screen.getByLabelText('Пароль')).toHaveAttribute('type', 'text');
    await user.click(screen.getByRole('button', { name: 'Скрыть пароль' }));
    expect(screen.getByLabelText('Пароль')).toHaveAttribute('type', 'password');
  });

  it('authenticates employee, retrieves CSRF and shows only allowed menu entries', async () => {
    const fetchMock = mockBackend();
    renderApp();
    await enterCredentials();
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Добро пожаловать в ChairX' })).toBeInTheDocument());

    expect(screen.getByText('Тестовый сотрудник')).toBeInTheDocument();
    expect(screen.getByText(/employee · EMPLOYEE/)).toBeInTheDocument();
    const nav = screen.getByRole('navigation', { name: 'Разделы ChairX' });
    expect(within(nav).getByRole('link', { name: 'Продажи' })).toBeInTheDocument();
    expect(within(nav).queryByRole('link', { name: 'Финансы' })).not.toBeInTheDocument();
    expect(within(nav).queryByRole('link', { name: 'Администрирование' })).not.toBeInTheDocument();

    expect(fetchMock.mock.calls.slice(0, 2).map(([path]) => path)).toEqual(['/api/auth/me', '/api/csrf']);
    for (const [, init] of fetchMock.mock.calls.slice(0, 2)) {
      expect(new Headers(init?.headers).get('Authorization')).toMatch(/^Basic /);
      expect(init?.credentials).toBe('same-origin');
    }
  });

  it('shows wrong-password and permission errors without exposing backend text', async () => {
    mockBackend(profile, { meStatus: 401 });
    renderApp();
    await enterCredentials();
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Неверный логин или пароль.'));

    vi.unstubAllGlobals();
    mockBackend(profile, { meStatus: 403 });
    await userEvent.setup().click(screen.getByRole('button', { name: 'Войти' }));
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('нет доступа к ChairX'));
  });

  it('does not lose credentials on a network error and displays a safe message', async () => {
    mockBackend(profile, { network: true });
    renderApp();
    await enterCredentials();
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Не удалось подключиться'));
  });

  it('prevents double-submit while authentication is pending', async () => {
    let resolveResponse: ((response: Response) => void) | undefined;
    const fetchMock = vi.fn(async () => new Promise<Response>((resolve) => { resolveResponse = resolve; }));
    vi.stubGlobal('fetch', fetchMock);
    renderApp();
    const user = userEvent.setup();
    await user.type(screen.getByRole('textbox', { name: 'Логин' }), 'employee');
    await user.type(screen.getByLabelText('Пароль'), 'SecretPassword123!');
    await user.click(screen.getByRole('button', { name: 'Войти' }));
    expect(screen.getByRole('button', { name: 'Проверяем данные…' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Проверяем данные…' }));
    expect(fetchMock).toHaveBeenCalledTimes(1);
    resolveResponse?.(json(profile));
  });

  it('direct navigation to a forbidden page stays forbidden after valid login', async () => {
    mockBackend();
    renderApp('/finance');
    await enterCredentials();
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Доступ запрещён' })).toBeInTheDocument());
    expect(screen.queryByRole('heading', { name: 'Финансы' })).not.toBeInTheDocument();
  });

  it('uses permissions, not hardcoded roles, for custom access', async () => {
    mockBackend({ ...profile, permissions: ['FINANCE_READ', 'DAILY_CLOSING_READ'], roles: ['CUSTOM'] });
    renderApp('/finance');
    await enterCredentials();
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Финансы', level: 2 })).toBeInTheDocument());
    const nav = screen.getByRole('navigation', { name: 'Разделы ChairX' });
    expect(within(nav).getByRole('link', { name: 'Финансы' })).toBeInTheDocument();
    expect(within(nav).queryByRole('link', { name: 'Продажи' })).not.toBeInTheDocument();
  });

  it('logout removes authorization/CSRF, clears query cache and returns to login', async () => {
    mockBackend();
    const { queryClient } = renderApp();
    await enterCredentials();
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Добро пожаловать в ChairX' })).toBeInTheDocument());
    queryClient.setQueryData(['customers'], { confidential: true });
    expect(queryClient.getQueryData(['customers'])).toBeDefined();

    await userEvent.setup().click(screen.getByRole('button', { name: 'Выйти из ChairX' }));
    expect(screen.getByRole('heading', { name: 'Вход для сотрудников' })).toBeInTheDocument();
    expect(queryClient.getQueryData(['customers'])).toBeUndefined();
    expect(authSession.authHeaders()).toEqual({});
    expect(() => authSession.csrfHeaders()).toThrow();
  });

  it('invalid CSRF endpoint response blocks authentication', async () => {
    const fetchMock = mockBackend(profile, { csrfStatus: 403 });
    renderApp();
    await enterCredentials();
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('недостаточно прав'));
    expect(authSession.authHeaders()).toEqual({});
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('a second user can sign in without carrying previous authorization or data', async () => {
    const fetchMock = mockBackend();
    const { queryClient } = renderApp();
    await enterCredentials();
    await waitFor(() => expect(screen.getByText('Тестовый сотрудник')).toBeInTheDocument());
    queryClient.setQueryData(['sales'], { first: 'secret' });
    await userEvent.setup().click(screen.getByRole('button', { name: 'Выйти из ChairX' }));
    fetchMock.mockImplementation(async (input: RequestInfo | URL) =>
      String(input) === '/api/auth/me'
        ? json({ ...profile, username: 'manager', displayName: 'Менеджер', roles: ['MANAGER'], permissions: ['PURCHASE_READ'] })
        : json(csrf));
    await enterCredentials('manager', 'DifferentPassword123!');
    await waitFor(() => expect(screen.getByText('Менеджер')).toBeInTheDocument());
    expect(queryClient.getQueryData(['sales'])).toBeUndefined();
    expect(screen.queryByText('Тестовый сотрудник')).not.toBeInTheDocument();
  });
});
