import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router';
import { AppRouter } from '../app/router/AppRouter';
import { authSession } from '../features/auth/session';
import { createQueryClient } from '../shared/api/query';
import { blankCustomer, customerInput, customerTitle, validateCustomer } from '../features/customers/validation';
import type { Customer, SaleSummary } from '../features/customers/types';

const access = vi.hoisted(() => ({
  permissions: ['CUSTOMERS_READ', 'CUSTOMERS_CREATE', 'CUSTOMERS_MANAGE', 'SALES_READ'] as string[],
  roles: ['CUSTOM_CRM_OPERATOR'] as string[],
}));
vi.mock('../features/auth/AuthProvider', () => ({
  useAuth: () => ({
    status: 'authenticated', isAuthenticated: true,
    user: { id: 'user', username: 'crm', displayName: 'Сотрудник',
      roles: access.roles, permissions: access.permissions },
    hasPermission: (p: string) => access.permissions.includes(p),
    hasAnyPermission: (pp: readonly string[]) => pp.some((p) => access.permissions.includes(p)),
    login: vi.fn(), logout: vi.fn(),
  }),
}));

const id = '11111111-1111-4111-8111-111111111111';
const createdId = '22222222-2222-4222-8222-222222222222';
const when = '2026-10-10T10:00:00Z';

function person(changes: Partial<Customer> = {}): Customer {
  return {
    id, fullName: 'Алия', phone: '+996700123456', secondaryPhone: '0555000101',
    whatsappPhone: '+996700123456', instagramUsername: 'aliya.official',
    address: 'ул. Манаса, 4', cityRegion: 'Бишкек', comment: 'Кресло Ergo',
    active: true, createdAt: when, updatedAt: when, ...changes,
  };
}
function sale(index = 1): SaleSummary {
  return {
    id: '33333333-3333-4333-8333-' + String(index).padStart(12, '0'),
    saleNumber: 'S-' + index, createdAt: when, customerId: id,
    customerName: 'Алия', products: 'Ergo Comfort, Чёрный',
    total: '8500.00', quantity: 1, status: 'FULFILLED',
    paymentStatus: 'PAID', deliveryStatus: 'DELIVERED', fulfillmentType: 'DELIVERY',
    plannedDeliveryDate: null,
  };
}
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body),
  { status, headers: { 'Content-Type': 'application/json' } });

function mockBackend(options: {
  customers?: Customer[]; sales?: SaleSummary[]; fail?: Record<string, number>;
  networkPost?: boolean; holdCreate?: Promise<void>;
} = {}) {
  const customers = [...(options.customers ?? [person()])];
  const sales = [...(options.sales ?? [sale()])];
  const calls = vi.fn(async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
    const url = new URL(String(input), 'http://chairx.local');
    const method = init?.method ?? 'GET';
    const path = url.pathname;
    const err = options.fail?.[method + ' ' + path];
    if (err) return json({ code: 'ACCESS_DENIED', message: 'java.lang.sensitive', details: {} }, err);
    if (method === 'GET' && path === '/api/customers/overview') {
      const q = url.searchParams.get('query')?.toLowerCase() ?? '';
      const page = Number(url.searchParams.get('page') ?? '0');
      const found = customers.filter((c) => [c.fullName, c.phone, c.secondaryPhone, c.whatsappPhone,
        c.instagramUsername].some((x) => x?.toLowerCase().includes(q)));
      const items = found.slice(page * 20, (page + 1) * 20).map((c) => ({
        id: c.id, fullName: c.fullName, phone: c.phone, whatsappPhone: c.whatsappPhone,
        cityRegion: c.cityRegion, orderCount: sales.filter((s) => s.customerId === c.id).length,
        lastSaleNumber: sales.find((s) => s.customerId === c.id)?.saleNumber ?? null,
        lastOrderAt: sales.find((s) => s.customerId === c.id)?.createdAt ?? null,
      }));
      return json({ items, page, size: 20, total: found.length });
    }
    if (method === 'GET' && path === '/api/customers/search') {
      const q = url.searchParams.get('query')?.toLowerCase() ?? '';
      return json(customers.filter((c) => [c.fullName, c.phone, c.secondaryPhone, c.whatsappPhone,
        c.instagramUsername].some((x) => x?.toLowerCase().includes(q))).slice(0, 20));
    }
    if (path === '/api/customers') {
      if (method === 'GET') {
        const page = Number(url.searchParams.get('page') ?? '0');
        return json({ items: customers.slice(page * 20, (page + 1) * 20),
          page, size: 20, totalElements: customers.length });
      }
      if (method === 'POST') {
        await options.holdCreate;
        if (options.networkPost) throw new TypeError('Connection lost');
        const input = JSON.parse(String(init?.body)) as Customer;
        const saved = person({ ...input, id: createdId, createdAt: when, updatedAt: when });
        customers.push(saved);
        return json(saved, 201);
      }
    }
    const status = /^\/api\/customers\/([^/]+)\/(activate|deactivate)$/.exec(path);
    if (status && method === 'POST') {
      const item = customers.find((c) => c.id === status[1]);
      if (!item) return json({ code: 'NOT_FOUND', message: '', details: {} }, 404);
      item.active = status[2] === 'activate';
      return json(item);
    }
    const detail = /^\/api\/customers\/([^/]+)$/.exec(path);
    if (detail) {
      const item = customers.find((c) => c.id === detail[1]);
      if (!item) return json({ code: 'NOT_FOUND', message: '', details: {} }, 404);
      if (method === 'PUT') Object.assign(item, JSON.parse(String(init?.body)), { updatedAt: when });
      return json(item);
    }
    const history = /^\/api\/sales\/customer\/([^/]+)$/.exec(path);
    if (history && method === 'GET') {
      const page = Number(url.searchParams.get('page') ?? '0');
      const found = sales.filter((s) => s.customerId === history[1]);
      return json({ items: found.slice(page * 20, (page + 1) * 20),
        page, size: 20, total: found.length });
    }
    throw new Error('Unexpected request: ' + method + ' ' + path);
  });
  vi.stubGlobal('fetch', calls);
  return { calls, customers, sales };
}

function renderAt(path = '/customers') {
  return render(<QueryClientProvider client={createQueryClient()}>
    <MemoryRouter initialEntries={[path]}><AppRouter /></MemoryRouter>
  </QueryClientProvider>);
}
function form(name = 'Создание клиента') {
  return within(screen.getByRole('form', { name }));
}
function setField(label: string, value: string, name = 'Создание клиента') {
  fireEvent.change(form(name).getByRole('textbox', { name: label }), { target: { value } });
}
function callsTo(calls: ReturnType<typeof vi.fn>, path: string) {
  return calls.mock.calls.filter(([url]) => String(url).includes(path));
}

beforeEach(() => {
  access.roles = ['CUSTOM_CRM_OPERATOR'];
  access.permissions = ['CUSTOMERS_READ', 'CUSTOMERS_CREATE', 'CUSTOMERS_MANAGE', 'SALES_READ'];
  authSession.activate('Basic test-credentials', { headerName: 'X-CSRF-TOKEN', token: 'test-csrf' });
});
afterEach(() => { authSession.clear(); vi.unstubAllGlobals(); });

describe('F05 customer list / permissions', () => {
  it('loads real overview, order counts and no per-customer sale queries', async () => {
    const { calls } = mockBackend();
    renderAt();
    expect(await screen.findByRole('cell', { name: 'Алия' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: 'S-1' })).toBeInTheDocument();
    expect(screen.getByText(/Всего клиентов: 1/)).toBeInTheDocument();
    expect(callsTo(calls, '/api/customers/overview')).toHaveLength(1);
    expect(callsTo(calls, '/api/sales/customer')).toHaveLength(0);
  });

  it('without SALES_READ uses customer list, omits all sale data and does not call overview', async () => {
    access.permissions = ['CUSTOMERS_READ'];
    const { calls } = mockBackend();
    renderAt();
    expect(await screen.findByRole('cell', { name: 'Алия' })).toBeInTheDocument();
    expect(screen.getByText(/Статистика продаж скрыта/)).toBeInTheDocument();
    expect(screen.queryByRole('columnheader', { name: 'Заказов' })).not.toBeInTheDocument();
    expect(callsTo(calls, '/api/customers/overview')).toHaveLength(0);
    expect(callsTo(calls, '/api/customers?page=')).toHaveLength(1);
  });

  it('without SALES_READ searches a capped 20-record endpoint, never fabricates totals', async () => {
    access.permissions = ['CUSTOMERS_READ'];
    const { calls } = mockBackend({ customers: Array.from({ length: 26 }, (_, i) =>
      person({ id: '55555555-5555-4555-8555-' + String(i).padStart(12, '0'), fullName: 'Алия ' + i })) });
    renderAt();
    await screen.findByText(/Всего клиентов: 26/);
    fireEvent.change(screen.getByRole('searchbox', { name: 'Поиск клиентов' }), { target: { value: 'Алия' } });
    expect(await screen.findByText(/Поиск показывает не более 20 совпадений/)).toBeInTheDocument();
    expect(screen.queryByText(/Всего клиентов: 26/)).not.toBeInTheDocument();
    expect(callsTo(calls, '/api/customers/search?query=')).toHaveLength(1);
    expect(callsTo(calls, '/api/customers/overview')).toHaveLength(0);
  });

  it('overview searches the backend including WhatsApp and Instagram with debounce', async () => {
    const { calls } = mockBackend({ customers: [person(), person({
      id: createdId, fullName: null, phone: null, whatsappPhone: '+996555987654', instagramUsername: 'chairx.client',
    })] });
    renderAt();
    await screen.findByText(/Всего клиентов: 2/);
    fireEvent.change(screen.getByRole('searchbox', { name: 'Поиск клиентов' }), {
      target: { value: 'chairx.client' },
    });
    expect(await screen.findByText(/Всего клиентов: 1/)).toBeInTheDocument();
    expect(screen.getAllByRole('cell', { name: '+996555987654' }).length).toBeGreaterThan(0);
    expect(callsTo(calls, 'query=chairx.client').length).toBeGreaterThan(0);
  });

  it('paginates on the server', async () => {
    const { calls } = mockBackend({ customers: Array.from({ length: 22 }, (_, i) =>
      person({ id: '55555555-5555-4555-8555-' + String(i).padStart(12, '0'), fullName: 'Клиент ' + i })) });
    renderAt();
    expect(await screen.findByText(/Страница 1 из 2/)).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole('button', { name: 'Следующая' }));
    expect(await screen.findByText(/Страница 2 из 2/)).toBeInTheDocument();
    expect(callsTo(calls, 'page=1')).toHaveLength(1);
  });

  it('does not fetch protected routes or show data without CUSTOMERS_READ', async () => {
    access.permissions = ['CUSTOMERS_CREATE', 'SALES_READ'];
    const { calls } = mockBackend();
    renderAt();
    expect(screen.getByRole('heading', { name: 'Доступ запрещён' })).toBeInTheDocument();
    expect(calls).not.toHaveBeenCalled();
  });

  it('keeps custom role names independent of permission gates', async () => {
    access.roles = ['CUSTOM_TEST_ROLE'];
    access.permissions = ['CUSTOMERS_READ'];
    mockBackend();
    renderAt();
    expect(await screen.findByRole('cell', { name: 'Алия' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '+ Добавить клиента' })).not.toBeInTheDocument();
  });

  it('server HTTP 403 shows forbidden, without rendering private records', async () => {
    mockBackend({ fail: { 'GET /api/customers/overview': 403 } });
    renderAt();
    expect(await screen.findByRole('heading', { name: 'Доступ запрещён' })).toBeInTheDocument();
    expect(screen.queryByRole('cell', { name: 'Алия' })).not.toBeInTheDocument();
  });
});

describe('F05 customer creation and validation', () => {
  it.each([['Основной телефон', 'phone', '+996700445566'], ['WhatsApp', 'whatsappPhone', '+996777555666'],
    ['Instagram', 'instagramUsername', 'some.client']])(
    'creates a nameless client using only %s', async (label, field, value) => {
      const { calls } = mockBackend();
      renderAt();
      await screen.findByRole('cell', { name: 'Алия' });
      await userEvent.setup().click(screen.getByRole('button', { name: '+ Добавить клиента' }));
      setField(label, value);
      await userEvent.setup().click(form().getByRole('button', { name: 'Сохранить клиента' }));
      expect(await screen.findByRole('heading', { name: value })).toBeInTheDocument();
      const savedCall = calls.mock.calls.find(([url, req]) =>
        String(url) === '/api/customers' && (req as RequestInit).method === 'POST');
      const body = JSON.parse(String((savedCall?.[1] as RequestInit)?.body)) as Record<string, unknown>;
      expect(body.fullName).toBeNull();
      expect(body[field]).toBe(value);
    },
  );

  it('requires a contact and never invents a name', async () => {
    const { calls } = mockBackend();
    renderAt();
    await screen.findByRole('cell', { name: 'Алия' });
    await userEvent.setup().click(screen.getByRole('button', { name: '+ Добавить клиента' }));
    await userEvent.setup().click(form().getByRole('button', { name: 'Сохранить клиента' }));
    expect(form().getByRole('alert')).toHaveTextContent('Укажите хотя бы один контакт');
    expect(calls.mock.calls.filter(([url, req]) =>
      String(url) === '/api/customers' && (req as RequestInit).method === 'POST')).toHaveLength(0);
  });

  it('validates all field length limits and preserves + / leading zeros', () => {
    expect(validateCustomer({ ...blankCustomer, phone: '0'.repeat(51) })).toMatch(/50 символов/);
    expect(validateCustomer({ ...blankCustomer, instagramUsername: 'a'.repeat(101) })).toMatch(/100 символов/);
    expect(validateCustomer({ ...blankCustomer, comment: 'a'.repeat(2001), phone: '0123' })).toMatch(/2000 символов/);
    expect(customerInput({ ...blankCustomer, phone: '+00996123' }).phone).toBe('+00996123');
    expect(customerTitle(person({ fullName: null, phone: null, secondaryPhone: null,
      whatsappPhone: null, instagramUsername: null }))).toBe('Клиент без имени');
  });

  it('warns about duplicates but does not block creation', async () => {
    mockBackend();
    renderAt();
    await screen.findByRole('cell', { name: 'Алия' });
    await userEvent.setup().click(screen.getByRole('button', { name: '+ Добавить клиента' }));
    setField('Основной телефон', '+996700123456');
    expect(await screen.findByText('Возможно, такой клиент уже есть')).toBeInTheDocument();
    expect(form().getByRole('button', { name: 'Сохранить клиента' })).toBeEnabled();
    expect(form().getByRole('link', { name: 'Алия' })).toHaveAttribute('href', '/customers/' + id);
  });

  it('keeps fields after failed create; network uncertainty is explained', async () => {
    mockBackend({ networkPost: true });
    renderAt();
    await screen.findByRole('cell', { name: 'Алия' });
    await userEvent.setup().click(screen.getByRole('button', { name: '+ Добавить клиента' }));
    setField('Основной телефон', '+996777111222');
    await userEvent.setup().click(form().getByRole('button', { name: 'Сохранить клиента' }));
    expect(await form().findByRole('alert')).toHaveTextContent(/могло завершиться на сервере/);
    expect(form().getByRole('textbox', { name: 'Основной телефон' })).toHaveValue('+996777111222');
  });

  it('prevents duplicate POSTs while create is pending', async () => {
    let release!: () => void;
    const holdCreate = new Promise<void>((resolve) => { release = resolve; });
    const { calls } = mockBackend({ holdCreate });
    renderAt();
    await screen.findByRole('cell', { name: 'Алия' });
    await userEvent.setup().click(screen.getByRole('button', { name: '+ Добавить клиента' }));
    setField('Основной телефон', '+996222123456');
    fireEvent.click(form().getByRole('button', { name: 'Сохранить клиента' }));
    fireEvent.click(form().getByRole('button', { name: /Сохраняем/ }));
    expect(calls.mock.calls.filter(([url, req]) =>
      String(url) === '/api/customers' && (req as RequestInit).method === 'POST')).toHaveLength(1);
    release();
    expect(await screen.findByRole('heading', { name: '+996222123456' })).toBeInTheDocument();
  });

  it('shows HTTP 409 and keeps entered data', async () => {
    mockBackend({ fail: { 'POST /api/customers': 409 } });
    renderAt();
    await screen.findByRole('cell', { name: 'Алия' });
    await userEvent.setup().click(screen.getByRole('button', { name: '+ Добавить клиента' }));
    setField('Основной телефон', '0555123123');
    await userEvent.setup().click(form().getByRole('button', { name: 'Сохранить клиента' }));
    expect(await form().findByRole('alert')).toHaveTextContent(/противоречит текущему состоянию/);
    expect(form().getByRole('textbox', { name: 'Основной телефон' })).toHaveValue('0555123123');
  });
});

describe('F05 customer detail, editing, history', () => {
  it('shows all actual fields, copy actions, dates and paginated sale details', async () => {
    const { calls } = mockBackend();
    renderAt('/customers/' + id);
    expect(await screen.findByRole('heading', { name: 'Алия' })).toBeInTheDocument();
    expect(screen.getByText('ул. Манаса, 4')).toBeInTheDocument();
    expect(screen.getByText('aliya.official')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Копировать Основной телефон' })).toBeInTheDocument();
    expect(await screen.findByRole('cell', { name: 'S-1' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: 'Ergo Comfort, Чёрный' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: '8 500 сом' })).toBeInTheDocument();
    expect(callsTo(calls, '/api/sales/customer/' + id)).toHaveLength(1);
  });

  it('requires both CUSTOMER and SALES read for history and never shows fake zero orders', async () => {
    access.permissions = ['CUSTOMERS_READ'];
    const { calls } = mockBackend();
    renderAt('/customers/' + id);
    expect(await screen.findByText('История заказов недоступна для вашей роли.')).toBeInTheDocument();
    expect(callsTo(calls, '/api/sales/customer')).toHaveLength(0);
    expect(screen.queryByText('У клиента пока нет оформленных заказов.')).not.toBeInTheDocument();
  });

  it('paginates sale history on the server without sale-by-sale hydration', async () => {
    const { calls } = mockBackend({ sales: Array.from({ length: 23 }, (_, i) => sale(i + 1)) });
    renderAt('/customers/' + id);
    expect(await screen.findByText(/Страница 1 из 2/)).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole('button', { name: 'Следующая' }));
    expect(await screen.findByText(/Страница 2 из 2/)).toBeInTheDocument();
    expect(callsTo(calls, '/api/sales/customer/' + id)).toHaveLength(2);
    expect(callsTo(calls, '/api/sales/')).toHaveLength(2);
  });

  it('updates with PUT of all fields, not just changed ones', async () => {
    const { calls } = mockBackend();
    renderAt('/customers/' + id);
    await screen.findByRole('heading', { name: 'Алия' });
    await userEvent.setup().click(screen.getByRole('button', { name: 'Редактировать' }));
    setField('Имя', 'Алия Т.', 'Редактирование клиента');
    await userEvent.setup().click(form('Редактирование клиента').getByRole('button', { name: 'Сохранить клиента' }));
    expect(await screen.findByRole('heading', { name: 'Алия Т.' })).toBeInTheDocument();
    const request = calls.mock.calls.find(([url, init]) =>
      String(url) === '/api/customers/' + id && (init as RequestInit).method === 'PUT');
    const body = JSON.parse(String((request?.[1] as RequestInit)?.body)) as Record<string, unknown>;
    expect(body.fullName).toBe('Алия Т.');
    expect(body.phone).toBe('+996700123456');
    expect(body.instagramUsername).toBe('aliya.official');
    expect(body.address).toBe('ул. Манаса, 4');
  });

  it('can deactivate only after confirmation, and reactivate', async () => {
    const { calls } = mockBackend();
    renderAt('/customers/' + id);
    await screen.findByRole('heading', { name: 'Алия' });
    await userEvent.setup().click(screen.getByRole('button', { name: 'Деактивировать' }));
    expect(screen.getByRole('alertdialog')).toHaveTextContent(/Клиент останется в базе/);
    expect(callsTo(calls, '/deactivate')).toHaveLength(0);
    await userEvent.setup().click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Деактивировать' }));
    expect(await screen.findByText('Клиент деактивирован.')).toBeInTheDocument();
    expect(callsTo(calls, '/deactivate')).toHaveLength(1);
    await userEvent.setup().click(screen.getByRole('button', { name: 'Активировать' }));
    expect(await screen.findByText('Клиент активирован.')).toBeInTheDocument();
  });

  it('hides manage controls without CUSTOMERS_MANAGE', async () => {
    access.permissions = ['CUSTOMERS_READ', 'SALES_READ'];
    mockBackend();
    renderAt('/customers/' + id);
    await screen.findByRole('heading', { name: 'Алия' });
    expect(screen.queryByRole('button', { name: 'Редактировать' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Деактивировать' })).not.toBeInTheDocument();
  });

  it('rejects forbidden detail route before any network request', () => {
    access.permissions = ['SALES_READ'];
    const { calls } = mockBackend();
    renderAt('/customers/' + id);
    expect(screen.getByRole('heading', { name: 'Доступ запрещён' })).toBeInTheDocument();
    expect(calls).not.toHaveBeenCalled();
  });

  it('shows 404 for missing customer and invalid UUID', async () => {
    mockBackend();
    const view = renderAt('/customers/' + createdId);
    expect(await screen.findByRole('heading', { name: 'Страница не найдена' })).toBeInTheDocument();
    view.unmount();
    renderAt('/customers/not-a-uuid');
    expect(screen.getByRole('heading', { name: 'Страница не найдена' })).toBeInTheDocument();
  });

  it('does not create dead links to unimplemented sale detail pages', async () => {
    mockBackend();
    renderAt('/customers/' + id);
    await screen.findByRole('cell', { name: 'S-1' });
    expect(screen.queryByRole('link', { name: 'S-1' })).not.toBeInTheDocument();
  });
});
