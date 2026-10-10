import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { QueryClientProvider } from '@tanstack/react-query';
import { AppRouter } from '../app/router/AppRouter';
import { authSession } from '../features/auth/session';
import { createQueryClient } from '../shared/api/query';
import { positiveQuantity, movementNames, warehouseStock } from '../features/inventory/lib';
import { inventoryApi } from '../features/inventory/api';
import type { InventoryBalance, StockLine, StockMovement, TransferInput, Warehouse } from '../features/inventory/types';

const access = vi.hoisted(() => ({
  permissions: ['INVENTORY_READ', 'INVENTORY_TRANSFER'] as string[],
  roles: ['CUSTOM_STOCK_OPERATOR'] as string[],
}));
vi.mock('../features/auth/AuthProvider', () => ({
  useAuth: () => ({
    status: 'authenticated', isAuthenticated: true,
    user: { id: 'user', username: 'inventory', displayName: 'Сотрудник',
      permissions: access.permissions, roles: access.roles },
    hasPermission: (p: string) => access.permissions.includes(p),
    hasAnyPermission: (pp: readonly string[]) => pp.some((p) => access.permissions.includes(p)),
    login: vi.fn(), logout: vi.fn(),
  }),
}));

const HOME = '11111111-1111-4111-8111-111111111111';
const OFFICE = '22222222-2222-4222-8222-222222222222';
const EXTRA = '33333333-3333-4333-8333-333333333333';
const VARIANT = '44444444-4444-4444-8444-444444444444';
const time = '2026-10-10T10:00:00Z';
const warehousesBase: Warehouse[] = [
  { id: HOME, name: 'Домашний', code: 'HOME', address: null, active: true, createdAt: time, updatedAt: time },
  { id: OFFICE, name: 'Офисный', code: 'OFFICE', address: null, active: true, createdAt: time, updatedAt: time },
  { id: EXTRA, name: 'Резервный', code: 'REVERSE', address: null, active: false, createdAt: time, updatedAt: time },
];
function chair(id = VARIANT): StockLine {
  return {
    productVariantId: id, model: 'Ergo Comfort', variation: 'Чёрный',
    onHand: 13, reserved: 2, blocked: 1, available: 10,
    warehouses: [
      { warehouseId: HOME, warehouseCode: 'HOME', onHand: 8, reserved: 2, blocked: 1, available: 5 },
      { warehouseId: OFFICE, warehouseCode: 'OFFICE', onHand: 5, reserved: 0, blocked: 0, available: 5 },
    ],
  };
}
function rowZero(): StockLine {
  return { productVariantId: '55555555-5555-4555-8555-555555555555', model: 'Chair X5',
    variation: 'Бежевый', onHand: 0, reserved: 0, blocked: 0, available: 0, warehouses: [] };
}
function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

interface MockOptions {
  warehouses?: Warehouse[]; stock?: StockLine[]; stockTotal?: number;
  fail?: Record<string, number>; mode?: 'ok' | 'network-after-save' | 'hold';
  hold?: Promise<void>;
  network?: boolean;
}
function fakeBackend(options: MockOptions = {}) {
  const records: TransferInput[] = [];
  const calls = vi.fn(async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
    const url = new URL(String(input), 'http://chairx.local');
    const method = init?.method ?? 'GET';
    const path = url.pathname;
    if (options.network) throw new TypeError('Offline');
    const rejected = options.fail?.[method + ' ' + path];
    if (rejected) return json({ code: 'INVENTORY_TEST_ERROR', message: 'Private Java stack trace', details: {} }, rejected);
    const wh = options.warehouses ?? warehousesBase;
    const stock = options.stock ?? [chair()];
    if (path === '/api/warehouses' && method === 'GET') {
      const page = Number(url.searchParams.get('page') ?? 0);
      const size = Number(url.searchParams.get('size') ?? 20);
      return json({ items: wh.slice(page * size, (page + 1) * size), page, size,
        totalElements: wh.length, totalPages: Math.ceil(wh.length / size) });
    }
    if (path === '/api/inventory/overview') {
      const warehouseId = url.searchParams.get('warehouseId');
      const model = url.searchParams.get('model')?.toLowerCase() ?? '';
      const variation = url.searchParams.get('variation')?.toLowerCase() ?? '';
      const onlyAvailable = url.searchParams.get('onlyAvailable') === 'true';
      const includeZero = url.searchParams.get('includeZero') === 'true';
      const page = Number(url.searchParams.get('page') ?? 0);
      const size = Number(url.searchParams.get('size') ?? 20);
      const rows = stock.map((item): StockLine => {
        if (!warehouseId) return item;
        const chosen = warehouseStock(item, warehouseId);
        return { ...item, onHand: chosen?.onHand ?? 0, reserved: chosen?.reserved ?? 0,
          blocked: chosen?.blocked ?? 0, available: chosen?.available ?? 0,
          warehouses: chosen ? [chosen] : [] };
      }).filter((item) => item.model.toLowerCase().includes(model) &&
        item.variation.toLowerCase().includes(variation) &&
        (includeZero || item.onHand !== 0 || item.reserved !== 0 || item.blocked !== 0) &&
        (!onlyAvailable || item.available > 0));
      return json({ items: rows.slice(page * size, (page + 1) * size), page, size,
        total: options.stockTotal ?? rows.length });
    }
    const balance = /^\/api\/inventory\/balances\/([^/]+)\/([^/]+)$/.exec(path);
    if (balance) {
      const entry = stock.find((v) => v.productVariantId === balance[2]);
      const chosen = entry?.warehouses.find((w) => w.warehouseId === balance[1]);
      if (!chosen) return json({ code: 'NOT_FOUND', message: '', details: {} }, 404);
      const data: InventoryBalance = { warehouseId: balance[1]!, productVariantId: balance[2]!,
        onHand: chosen.onHand, reserved: chosen.reserved,
        blocked: chosen.blocked, available: chosen.available };
      return json(data);
    }
    if (path === '/api/inventory/movements') {
      if (!url.searchParams.get('warehouseId') || !url.searchParams.get('variantId')) {
        return json({ code: 'INVALID_QUERY', message: '', details: {} }, 400);
      }
      const page = Number(url.searchParams.get('page') ?? 0);
      const entries: StockMovement[] = [{
        id: 'movement1', operationId: 'movement-op', warehouseId: HOME,
        productVariantId: VARIANT, type: 'PURCHASE_IN', quantity: 8,
        sourceType: 'PURCHASE', sourceId: null, actor: 'employee', occurredAt: time,
      }];
      return json({ items: entries.slice(page * 20, (page + 1) * 20),
        page, size: 20, totalElements: entries.length });
    }
    if (path === '/api/inventory/transfers' && method === 'POST') {
      const data = JSON.parse(String(init?.body)) as TransferInput;
      if (!records.some((entry) => entry.transferId === data.transferId)) records.push(data);
      await options.hold;
      if (options.mode === 'network-after-save') {
        options.mode = 'ok';
        throw new TypeError('Response lost after commit');
      }
      return json({ transferId: data.transferId, quantity: data.quantity,
        outMovementId: 'out', inMovementId: 'in', totalCost: 987654321 }, 201);
    }
    const transferDetails = /^\/api\/inventory\/transfers\/([^/]+)$/.exec(path);
    if (transferDetails) {
      const entry = records.find((item) => item.transferId === transferDetails[1]);
      return entry ? json({ id: entry.transferId, sourceWarehouseId: entry.sourceWarehouseId,
        destinationWarehouseId: entry.destinationWarehouseId, variantId: entry.variantId,
        quantity: entry.quantity, actor: 'employee', createdAt: time,
        outMovementId: 'out', inMovementId: 'in', totalCost: 987654321 })
        : json({ code: 'NOT_FOUND', message: '', details: {} }, 404);
    }
    if (path === '/api/inventory/transfers' && method === 'GET') {
      const whFilter = url.searchParams.get('warehouseId');
      const variantFilter = url.searchParams.get('variantId');
      const entries = [
        { id: 'transfer1', sourceWarehouseId: HOME, destinationWarehouseId: OFFICE,
          variantId: VARIANT, quantity: 3, actor: 'employee', createdAt: time,
          outMovementId: 'out', inMovementId: 'in', totalCost: 987654321 },
        ...records.map((item) => ({ id: item.transferId, sourceWarehouseId: item.sourceWarehouseId,
          destinationWarehouseId: item.destinationWarehouseId, variantId: item.variantId,
          quantity: item.quantity, actor: 'employee', createdAt: time,
          outMovementId: 'out', inMovementId: 'in', totalCost: 987654321 })),
      ].filter((entry) => (!whFilter || entry.sourceWarehouseId === whFilter || entry.destinationWarehouseId === whFilter)
        && (!variantFilter || entry.variantId === variantFilter));
      const page = Number(url.searchParams.get('page') ?? 0);
      const size = Number(url.searchParams.get('size') ?? 20);
      return json({ items: entries.slice(page * size, (page + 1) * size),
        page, size, totalElements: entries.length, totalPages: Math.ceil(entries.length / size) });
    }
    throw new Error('Unexpected ' + method + ' ' + path);
  });
  vi.stubGlobal('fetch', calls);
  return { calls, records };
}
function renderAt(path = '/inventory') {
  return render(<QueryClientProvider client={createQueryClient()}>
    <MemoryRouter initialEntries={[path]}><AppRouter /></MemoryRouter>
  </QueryClientProvider>);
}
function stockTable() {
  return within(screen.getAllByRole('table')[0]!);
}
function stockAction(name: string) {
  return stockTable().getAllByRole('button', { name })[0]!;
}
function transferForm() {
  return within(screen.getByRole('region', { name: 'Перемещение товара' }));
}
async function startTransfer() {
  await screen.findByRole('columnheader', { name: 'Модель' });
  await userEvent.setup().click(stockAction('Переместить'));
  return transferForm();
}
async function submitTransfer(amount = '3', source = HOME, destination = OFFICE) {
  const form = await startTransfer();
  const user = userEvent.setup();
  await user.selectOptions(form.getByRole('combobox', { name: 'Склад отправления' }), source);
  await user.selectOptions(form.getByRole('combobox', { name: 'Склад назначения' }), destination);
  await user.type(form.getByRole('textbox', { name: 'Количество, шт.' }), amount);
  await user.click(form.getByRole('button', { name: 'Проверить перемещение' }));
  return form;
}

beforeEach(() => {
  access.permissions = ['INVENTORY_READ', 'INVENTORY_TRANSFER'];
  access.roles = ['CUSTOM_STOCK_OPERATOR'];
  authSession.activate('Basic test-inventory', { headerName: 'X-CSRF-TOKEN', token: 'test-xsrf' });
});
afterEach(() => { authSession.clear(); vi.unstubAllGlobals(); });

describe('F04 warehouses and real overview', () => {
  it('renders actual warehouses by UUID with inactive extra warehouse and real stock numbers', async () => {
    const { calls } = fakeBackend(); renderAt();
    expect(screen.getByRole('status')).toHaveTextContent('Загружаем список складов');
    await screen.findByRole('columnheader', { name: 'Модель' });
    expect(stockTable().getByRole('cell', { name: 'Ergo Comfort' })).toBeInTheDocument();
    expect(stockTable().getByRole('cell', { name: 'Чёрный' })).toBeInTheDocument();
    expect(stockTable().getByRole('columnheader', { name: 'Домашний' })).toBeInTheDocument();
    expect(stockTable().getByRole('columnheader', { name: 'Офисный' })).toBeInTheDocument();
    expect(stockTable().getByRole('columnheader', { name: 'Резервный' })).toBeInTheDocument();
    expect(stockTable().getByRole('cell', { name: '13' })).toBeInTheDocument();
    expect(stockTable().getByRole('cell', { name: '2' })).toBeInTheDocument();
    expect(stockTable().getByRole('cell', { name: '1' })).toBeInTheDocument();
    expect(stockTable().getByRole('cell', { name: '10' })).toBeInTheDocument();
    expect(calls.mock.calls.filter((c) => String(c[0]).startsWith('/api/inventory/balances/'))).toHaveLength(0);
  });
  it('loads a warehouse list longer than one page, without inventing IDs', async () => {
    const extra = Array.from({ length: 98 }, (_, i) => ({
      ...warehousesBase[0]!, id: '00000000-0000-4000-8000-' + String(i + 1).padStart(12, '0'),
      name: 'Дополнительный ' + String(i + 1),
    }));
    const { calls } = fakeBackend({ warehouses: [...warehousesBase, ...extra] }); renderAt();
    await screen.findByRole('columnheader', { name: 'Дополнительный 98' });
    expect(calls.mock.calls.some((c) => String(c[0]) === '/api/warehouses?page=1&size=100')).toBe(true);
  });
  it('switches to selected home warehouse and receives ONLY warehouse-specific totals', async () => {
    const { calls } = fakeBackend(); renderAt();
    await screen.findByRole('columnheader', { name: 'Модель' });
    await userEvent.setup().selectOptions(screen.getByRole('combobox', { name: 'Выбор склада' }), HOME);
    expect(await stockTable().findByRole('cell', { name: '5' })).toBeInTheDocument();
    expect(stockTable().getByRole('columnheader', { name: 'На складе' })).toBeInTheDocument();
    expect(stockTable().queryByRole('columnheader', { name: 'Офисный' })).not.toBeInTheDocument();
    expect(calls.mock.calls.some((c) => String(c[0]).includes('warehouseId=' + HOME))).toBe(true);
  });
  it('supports server-side search, available-only and include-zero filters', async () => {
    const { calls } = fakeBackend({ stock: [chair(), rowZero()] }); renderAt();
    const user = userEvent.setup();
    await screen.findByRole('columnheader', { name: 'Модель' });
    await user.click(screen.getByRole('button', { name: 'Включая нулевые' }));
    expect(await stockTable().findByRole('cell', { name: 'Chair X5' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Только доступные' }));
    await waitFor(() => expect(stockTable().queryByRole('cell', { name: 'Chair X5' })).not.toBeInTheDocument());
    expect(calls.mock.calls.some((c) => String(c[0]).includes('onlyAvailable=true'))).toBe(true);
    await user.type(screen.getByRole('searchbox', { name: 'Поиск модели' }), 'Ergo');
    await waitFor(() => expect(calls.mock.calls.some((c) => String(c[0]).includes('model=Ergo'))).toBe(true), { timeout: 2500 });
    await user.type(screen.getByRole('searchbox', { name: 'Поиск вариации' }), 'Чер');
    await waitFor(() => expect(calls.mock.calls.some((c) => String(c[0]).includes('variation=%D0%A7%D0%B5%D1%80'))).toBe(true), { timeout: 2500 });
  });
  it('uses API pagination and resets page when selecting a warehouse', async () => {
    const stock = Array.from({ length: 21 }, (_, i) => chair('00000000-0000-4000-8000-' + String(i + 1).padStart(12, '0')));
    const { calls } = fakeBackend({ stock }); renderAt();
    await screen.findByRole('columnheader', { name: 'Модель' });
    await userEvent.setup().click(screen.getAllByRole('button', { name: 'Следующая' })[0]!);
    await waitFor(() => expect(calls.mock.calls.some((c) => String(c[0]).includes('page=1&size=20'))).toBe(true));
    await userEvent.setup().selectOptions(screen.getByRole('combobox', { name: 'Выбор склада' }), OFFICE);
    await waitFor(() => expect(calls.mock.calls.some((c) => String(c[0]).includes('warehouseId=' + OFFICE) &&
      String(c[0]).includes('page=0'))).toBe(true));
  });
  it('shows empty warehouse and empty overview states', async () => {
    fakeBackend({ warehouses: [] }); renderAt();
    expect(await screen.findByText('Склады не найдены')).toBeInTheDocument();
  });
  it('retries after warehouse loading error', async () => {
    const failures: Record<string, number> = { 'GET /api/warehouses': 500 };
    fakeBackend({ fail: failures }); renderAt();
    expect(await screen.findByRole('alert', {}, { timeout: 5000 })).toHaveTextContent('Ошибка');
    delete failures['GET /api/warehouses'];
    await userEvent.setup().click(screen.getByRole('button', { name: 'Повторить' }));
    expect(await screen.findByRole('columnheader', { name: 'Модель' })).toBeInTheDocument();
  });
  it('does not call inventory APIs without INVENTORY_READ', () => {
    access.permissions = ['INVENTORY_TRANSFER']; access.roles = ['EMPLOYEE'];
    const { calls } = fakeBackend(); renderAt();
    expect(screen.getByRole('heading', { name: 'Доступ запрещён' })).toBeInTheDocument();
    expect(calls).not.toHaveBeenCalled();
  });
});

describe('F04 stock position and histories', () => {
  it('loads single balance and movements only after choosing warehouse for one variant', async () => {
    const { calls } = fakeBackend(); renderAt();
    await screen.findByRole('columnheader', { name: 'Модель' });
    await userEvent.setup().click(stockAction('Подробнее'));
    expect(screen.getByRole('region', { name: 'Карточка складской позиции' })).toHaveTextContent('Ergo Comfort / Чёрный');
    expect(calls.mock.calls.some((c) => String(c[0]).includes('/movements'))).toBe(false);
    await userEvent.setup().selectOptions(screen.getByRole('combobox', { name: 'Склад для истории' }), HOME);
    expect(await screen.findByText('Поступление')).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'История движений' })).toHaveTextContent('employee');
    expect(calls.mock.calls.some((c) => String(c[0]).includes('/api/inventory/movements?warehouseId=' + HOME) &&
      String(c[0]).includes('variantId=' + VARIANT))).toBe(true);
    expect(calls.mock.calls.some((c) => String(c[0]).includes('/api/inventory/balances/' + HOME + '/' + VARIANT))).toBe(true);
  });
  it('fetches transfer history without displaying cost and filters by chosen warehouse and variant', async () => {
    const { calls } = fakeBackend(); renderAt();
    await screen.findByRole('columnheader', { name: 'Модель' });
    const transferHistory = screen.getByRole('region', { name: 'История перемещений' });
    expect(transferHistory).toHaveTextContent('employee');
    expect(transferHistory).toHaveTextContent('Домашний');
    expect(transferHistory).toHaveTextContent('Офисный');
    expect(transferHistory).not.toHaveTextContent('987654321');
    await userEvent.setup().click(stockAction('Подробнее'));
    await userEvent.setup().click(screen.getByRole('checkbox', { name: 'Только выбранная вариация' }));
    await waitFor(() => expect(calls.mock.calls.some((c) =>
      String(c[0]).includes('/api/inventory/transfers?') &&
      String(c[0]).includes('variantId=' + VARIANT))).toBe(true));
  });
  it('read-only inventory staff never sees transfer buttons, even with CUSTOM role', async () => {
    access.permissions = ['INVENTORY_READ']; access.roles = ['CUSTOM_READER'];
    fakeBackend(); renderAt();
    await screen.findByRole('columnheader', { name: 'Модель' });
    expect(stockTable().queryByRole('button', { name: 'Переместить' })).not.toBeInTheDocument();
    await userEvent.setup().click(stockAction('Подробнее'));
    expect(screen.queryByRole('link', { name: 'Каталог товаров' })).not.toBeInTheDocument();
  });
  it('allows historical viewing of inactive warehouse but excludes it from transfers', async () => {
    fakeBackend(); renderAt();
    await screen.findByRole('columnheader', { name: 'Модель' });
    await userEvent.setup().selectOptions(screen.getByRole('combobox', { name: 'Выбор склада' }), EXTRA);
    expect(screen.getByRole('combobox', { name: 'Выбор склада' })).toHaveValue(EXTRA);
    const form = await startTransfer();
    expect(within(form.getByRole('combobox', { name: 'Склад назначения' })).queryByRole('option', { name: 'Резервный' })).not.toBeInTheDocument();
  });
});

describe('F04 idempotent and guarded transfer workflow', () => {
  it('requires different warehouses and positive integer quantity within available balance', async () => {
    fakeBackend(); renderAt();
    const form = await startTransfer();
    const user = userEvent.setup();
    await user.click(form.getByRole('button', { name: 'Проверить перемещение' }));
    expect(form.getByRole('alert')).toHaveTextContent('Выберите оба склада');
    await user.selectOptions(form.getByRole('combobox', { name: 'Склад отправления' }), HOME);
    await user.selectOptions(form.getByRole('combobox', { name: 'Склад назначения' }), HOME);
    await user.type(form.getByRole('textbox', { name: 'Количество, шт.' }), '2');
    await user.click(form.getByRole('button', { name: 'Проверить перемещение' }));
    expect(form.getByRole('alert')).toHaveTextContent('должны отличаться');
    await user.selectOptions(form.getByRole('combobox', { name: 'Склад назначения' }), OFFICE);
    await user.clear(form.getByRole('textbox', { name: 'Количество, шт.' }));
    await user.type(form.getByRole('textbox', { name: 'Количество, шт.' }), '6');
    await user.click(form.getByRole('button', { name: 'Проверить перемещение' }));
    expect(form.getByRole('alert')).toHaveTextContent('превышает доступный');
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });
  it('uses one transfer UUID, exact product/warehouses/amount and CSRF, then refreshes stock', async () => {
    const { calls, records } = fakeBackend(); renderAt();
    const form = await submitTransfer();
    expect(screen.getByRole('alertdialog')).toHaveTextContent('Ergo Comfort / Чёрный');
    expect(screen.getByRole('alertdialog')).toHaveTextContent('Домашний');
    expect(screen.getByRole('alertdialog')).toHaveTextContent('Офисный');
    expect(records).toHaveLength(0);
    await userEvent.setup().click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Подтвердить' }));
    expect(await screen.findByText(/Перемещение выполнено/)).toBeInTheDocument();
    const transfer = calls.mock.calls.filter((c) => c[1]?.method === 'POST' && c[0] === '/api/inventory/transfers');
    expect(transfer).toHaveLength(1);
    const sent = JSON.parse(String(transfer[0]?.[1]?.body)) as TransferInput;
    expect(sent).toEqual({ transferId: expect.any(String), sourceWarehouseId: HOME,
      destinationWarehouseId: OFFICE, variantId: VARIANT, quantity: 3 });
    expect(sent.transferId).toMatch(/^[0-9a-f-]{36}$/);
    expect(records).toHaveLength(1);
    expect(new Headers(transfer[0]?.[1]?.headers).get('X-CSRF-TOKEN')).toBe('test-xsrf');
    expect(new Headers(transfer[0]?.[1]?.headers).get('Authorization')).toBe('Basic test-inventory');
    expect(screen.queryByRole('region', { name: 'Перемещение товара' })).not.toBeInTheDocument();
    expect(form.queryByRole('alert')).not.toBeInTheDocument();
  });
  it('never sends two transfers on repeated clicks while request is pending', async () => {
    let release: () => void = () => undefined;
    const hold = new Promise<void>((resolve) => { release = resolve; });
    const { calls } = fakeBackend({ mode: 'hold', hold }); renderAt();
    await submitTransfer();
    const yes = within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Подтвердить' });
    fireEvent.click(yes); fireEvent.click(yes);
    expect(calls.mock.calls.filter((c) => c[1]?.method === 'POST')).toHaveLength(1);
    release();
    expect(await screen.findByText(/Перемещение выполнено/)).toBeInTheDocument();
  });
  it('checks an uncertain network response by the same transferId and recovers', async () => {
    const { calls, records } = fakeBackend({ mode: 'network-after-save' }); renderAt();
    await submitTransfer();
    await userEvent.setup().click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Подтвердить' }));
    expect(await screen.findByText(/Ответ сервера не получен/)).toBeInTheDocument();
    expect(records).toHaveLength(1);
    expect(transferForm().getByRole('button', { name: 'Закрыть форму' })).toBeDisabled();
    await userEvent.setup().click(transferForm().getByRole('button', { name: 'Проверить по ID' }));
    expect(await screen.findByText(/Перемещение выполнено/)).toBeInTheDocument();
    expect(calls.mock.calls.filter((c) => c[1]?.method === 'POST')).toHaveLength(1);
    expect(calls.mock.calls.some((c) => String(c[0]) ===
      '/api/inventory/transfers/' + records[0]?.transferId)).toBe(true);
  });
  it('resends exactly same payload and ID after ambiguous network error', async () => {
    const { calls, records } = fakeBackend({ mode: 'network-after-save' }); renderAt();
    await submitTransfer();
    await userEvent.setup().click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Подтвердить' }));
    await screen.findByText(/Ответ сервера не получен/);
    await userEvent.setup().click(transferForm().getByRole('button', { name: 'Повторить прежний запрос' }));
    expect(await screen.findByText(/Перемещение выполнено/)).toBeInTheDocument();
    const posts = calls.mock.calls.filter((c) => c[1]?.method === 'POST');
    expect(posts).toHaveLength(2);
    expect(posts[0]?.[1]?.body).toEqual(posts[1]?.[1]?.body);
    expect(records).toHaveLength(1);
  });
  it.each([403, 409])('rejects server %i without success or fake stock updates', async (status) => {
    fakeBackend({ fail: { 'POST /api/inventory/transfers': status } }); renderAt();
    await submitTransfer();
    await userEvent.setup().click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Подтвердить' }));
    expect(await transferForm().findByRole('alert')).toBeInTheDocument();
    expect(screen.queryByText(/Перемещение выполнено/)).not.toBeInTheDocument();
  });
});

describe('F04 contracts and regression', () => {
  it.each([['1', 5, null], ['5', 5, null], ['6', 5, 'превышает'], ['0', 5, 'положительное'],
    ['-1', 5, 'положительное'], ['1.5', 5, 'положительное'], ['9007199254740992', 9999999999999999, 'слишком велико']] as const)(
      'validates quantity %s', (value, stock, expected) => {
        if (expected === null) expect(positiveQuantity(value, stock)).toBeNull();
        else expect(positiveQuantity(value, stock)).toContain(expected);
      });
  it('has all named movement types', () => {
    for (const type of ['PURCHASE_IN', 'SALE_OUT', 'RETURN_IN', 'TRANSFER_IN',
      'TRANSFER_OUT', 'WRITE_OFF', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT']) {
      expect(movementNames[type]).toBeTruthy();
    }
  });
  it('keeps F02.1 navigation and F03 catalog accessible independently', async () => {
    access.permissions = ['INVENTORY_READ', 'CATALOG_READ'];
    fakeBackend(); renderAt('/inventory');
    await screen.findByRole('columnheader', { name: 'Модель' });
    expect(screen.getByRole('link', { name: 'Каталог товаров' })).toBeInTheDocument();
    expect(document.querySelector('.md\\:hidden')).not.toBeNull();
    expect(screen.getByRole('navigation', { name: 'Разделы ChairX' })).toBeInTheDocument();
  });
  it('does not persist authentication or transfer operation in browser storage', async () => {
    const local = vi.spyOn(Storage.prototype, 'setItem');
    const { calls } = fakeBackend(); renderAt();
    await screen.findByRole('columnheader', { name: 'Модель' });
    expect(calls).toHaveBeenCalled();
    expect(local).not.toHaveBeenCalled();
    local.mockRestore();
  });
  it('backend transfer history includes totalCost even though UI hides it (risk documented)', async () => {
    const { calls } = fakeBackend(); renderAt();
    await screen.findByRole('columnheader', { name: 'Модель' });
    const listReq = calls.mock.calls.find((c) => String(c[0]).startsWith('/api/inventory/transfers?'));
    expect(listReq).toBeTruthy();
    const raw = await inventoryApi.transfers({ warehouseId: null, variantId: null, page: 0, size: 20 });
    expect((raw.items[0] as unknown as { totalCost?: number }).totalCost).toBe(987654321);
    expect(screen.getByRole('region', { name: 'История перемещений' })).not.toHaveTextContent('987654321');
  });
});
