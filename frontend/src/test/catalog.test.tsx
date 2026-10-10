import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router';
import { AppRouter } from '../app/router/AppRouter';
import { authSession } from '../features/auth/session';
import { createQueryClient } from '../shared/api/query';
import type { Product, ProductVariant } from '../features/catalog/types';
import { formatCatalogPrice, validateMoney, validateProduct, validateVariant } from '../features/catalog/validation';

const access = vi.hoisted(() => ({
  permissions: ['CATALOG_READ', 'CATALOG_MANAGE'] as string[],
  roles: ['CUSTOM_CATALOG'] as string[],
}));
vi.mock('../features/auth/AuthProvider', () => ({
  useAuth: () => ({
    status: 'authenticated', isAuthenticated: true,
    user: { id: 'employee', username: 'staff', displayName: 'Сотрудник',
      roles: access.roles, permissions: access.permissions },
    hasPermission: (permission: string) => access.permissions.includes(permission),
    hasAnyPermission: (permissions: readonly string[]) =>
      permissions.some((p) => access.permissions.includes(p)),
    login: vi.fn(), logout: vi.fn(),
  }),
}));
const productId = '11111111-1111-4111-8111-111111111111';
const variantId = '22222222-2222-4222-8222-222222222222';
const createdId = '33333333-3333-4333-8333-333333333333';
const now = '2026-10-10T05:00:00Z';

function model(values: Partial<Product> = {}): Product {
  return { id: productId, name: 'Ergo Comfort', category: 'Офисные кресла',
    description: 'Эргономичное кресло', active: true, createdAt: now, updatedAt: now, ...values };
}
function variant(values: Partial<ProductVariant> = {}): ProductVariant {
  return { id: variantId, productId, name: 'Чёрный', color: 'Чёрный',
    sku: 'ERGO-BLK', recommendedSalePrice: 8500, active: true, createdAt: now, updatedAt: now, ...values };
}
function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}
function mockCatalog(options: {
  products?: Product[]; variants?: ProductVariant[];
  errors?: Record<string, number>; holdCreate?: Promise<void>; network?: boolean;
} = {}) {
  const products = [...(options.products ?? [model()])];
  const variants = [...(options.variants ?? [variant()])];
  const calls = vi.fn(async (url: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
    const route = new URL(String(url), 'http://chairx.local');
    const path = route.pathname;
    const method = init?.method ?? 'GET';
    if (options.network) throw new TypeError('Connection refused');
    const failure = options.errors?.[method + ' ' + path];
    if (failure) return json({ code: 'CATALOG_ERROR', message: 'java.lang.Exception sensitive trace', details: {} }, failure);
    if (path === '/api/products') {
      if (method === 'GET') {
        const page = Number(route.searchParams.get('page') ?? '0');
        return json({ items: products.slice(page * 20, (page + 1) * 20),
          page, size: 20, totalElements: products.length, totalPages: Math.ceil(products.length / 20) });
      }
      await options.holdCreate;
      const item = model({ ...(JSON.parse(String(init?.body)) as Product), id: createdId });
      products.push(item);
      return json(item, 201);
    }
    const itemMatch = /^\/api\/products\/([^/]+)$/.exec(path);
    if (itemMatch) {
      const item = products.find((p) => p.id === itemMatch[1]);
      if (!item) return json({ code: 'PRODUCT_NOT_FOUND', message: 'Not found', details: {} }, 404);
      if (method === 'PUT') Object.assign(item, JSON.parse(String(init?.body)), { updatedAt: now });
      return json(item);
    }
    const itemState = /^\/api\/products\/([^/]+)\/(activate|deactivate)$/.exec(path);
    if (itemState) {
      const item = products.find((p) => p.id === itemState[1]);
      if (!item) return json({}, 404);
      item.active = itemState[2] === 'activate';
      return json(item);
    }
    const variantsPath = /^\/api\/products\/([^/]+)\/variants$/.exec(path);
    if (variantsPath) {
      if (method === 'GET') {
        const page = Number(route.searchParams.get('page') ?? '0');
        return json({ items: variants.slice(page * 20, (page + 1) * 20), page, size: 20,
          totalElements: variants.length, totalPages: Math.ceil(variants.length / 20) });
      }
      const item = variant({ ...(JSON.parse(String(init?.body)) as ProductVariant),
        id: '44444444-4444-4444-8444-444444444444' });
      variants.push(item);
      return json(item, 201);
    }
    const variantMatch = /^\/api\/product-variants\/([^/]+)$/.exec(path);
    if (variantMatch) {
      const item = variants.find((v) => v.id === variantMatch[1]);
      if (!item) return json({}, 404);
      if (method === 'PUT') Object.assign(item, JSON.parse(String(init?.body)));
      return json(item);
    }
    const variantStatus = /^\/api\/product-variants\/([^/]+)\/(activate|deactivate)$/.exec(path);
    if (variantStatus) {
      const item = variants.find((v) => v.id === variantStatus[1]);
      if (!item) return json({}, 404);
      item.active = variantStatus[2] === 'activate';
      return json(item);
    }
    throw new Error('Unexpected request ' + method + ' ' + path);
  });
  vi.stubGlobal('fetch', calls);
  return { calls, products, variants };
}
function renderAt(path = '/catalog') {
  return render(<QueryClientProvider client={createQueryClient()}>
    <MemoryRouter initialEntries={[path]}><AppRouter /></MemoryRouter>
  </QueryClientProvider>);
}
function productForm() { return screen.getByRole('form', { name: 'Создание модели' }); }
function table() { return within(screen.getByRole('table')); }
function createButton() { return screen.getAllByRole('button', { name: '+ Создать товар' })[0]!; }

beforeEach(() => {
  access.permissions = ['CATALOG_READ', 'CATALOG_MANAGE'];
  access.roles = ['CUSTOM_CATALOG'];
  authSession.activate('Basic test-credential', { headerName: 'X-CSRF-TOKEN', token: 'csrf-value' });
});
afterEach(() => { authSession.clear(); vi.unstubAllGlobals(); });

describe('F03 catalog product list', () => {
  it('loads real page metadata, actual models and no N+1 variant queries', async () => {
    const { calls } = mockCatalog({ products: [model(), model({
      id: '55555555-5555-4555-8555-555555555555', name: 'Chair X5', active: false })] });
    renderAt();
    expect(screen.getByRole('status')).toHaveTextContent('Загружаем модели');
    expect(await screen.findByRole('cell', { name: 'Ergo Comfort' })).toBeInTheDocument();
    expect(table().getByRole('cell', { name: 'Chair X5' })).toBeInTheDocument();
    expect(screen.getByText(/backend поддерживает только пагинацию/)).toBeInTheDocument();
    expect(calls).toHaveBeenCalledTimes(1);
    expect(String(calls.mock.calls[0]?.[0])).toBe('/api/products?page=0&size=20');
  });
  it('shows actual empty catalog with no invented models', async () => {
    mockCatalog({ products: [] }); renderAt();
    expect(await screen.findByText('Каталог пока пуст')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });
  it('uses backend page/size and reports total count', async () => {
    const items = Array.from({ length: 21 }, (_, i) => model({
      id: '00000000-0000-4000-8000-' + String(i + 1).padStart(12, '0'),
      name: 'Модель ' + String(i + 1),
    }));
    const { calls } = mockCatalog({ products: items }); renderAt();
    await screen.findByRole('cell', { name: 'Модель 1' });
    expect(screen.getByText(/Всего:/)).toHaveTextContent('Всего: 21');
    await userEvent.setup().click(screen.getByRole('button', { name: 'Вперёд' }));
    expect(await screen.findByRole('cell', { name: 'Модель 21' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Вперёд' })).toBeDisabled();
    expect(calls.mock.calls.some((c) => String(c[0]) === '/api/products?page=1&size=20')).toBe(true);
    expect(calls.mock.calls.some((c) => String(c[0]).includes('/variants'))).toBe(false);
  });
  it('opens a model card and preserves F02.1 breadcrumbs', async () => {
    mockCatalog(); renderAt();
    await screen.findByRole('cell', { name: 'Ergo Comfort' });
    await userEvent.setup().click(table().getByRole('link', { name: 'Открыть' }));
    expect(await screen.findByRole('heading', { name: 'Ergo Comfort', level: 2 })).toBeInTheDocument();
    const bc = screen.getByRole('navigation', { name: 'Навигационная цепочка' });
    expect(within(bc).getByText('Каталог товаров')).toBeInTheDocument();
    expect(within(bc).getByText('Карточка модели')).toBeInTheDocument();
  });
  it('saves a model through POST + CSRF and opens created card', async () => {
    const { calls } = mockCatalog({ products: [] }); renderAt();
    await screen.findByText('Каталог пока пуст');
    const user = userEvent.setup();
    await user.click(createButton());
    await user.type(within(productForm()).getByRole('textbox', { name: /Название/ }), 'Новое кресло');
    await user.click(within(productForm()).getByRole('button', { name: 'Создать модель' }));
    expect(await screen.findByRole('heading', { name: 'Новое кресло', level: 2 })).toBeInTheDocument();
    expect(screen.getByText('Модель успешно создана.')).toBeInTheDocument();
    const post = calls.mock.calls.find((c) => c[1]?.method === 'POST' && c[0] === '/api/products');
    expect(JSON.parse(String(post?.[1]?.body))).toEqual({ name: 'Новое кресло', category: null, description: null });
    expect(new Headers(post?.[1]?.headers).get('X-CSRF-TOKEN')).toBe('csrf-value');
    expect(new Headers(post?.[1]?.headers).get('Authorization')).toBe('Basic test-credential');
  });
  it('disables double submission while the save request is in flight', async () => {
    let release: () => void = () => undefined;
    const holdCreate = new Promise<void>((resolve) => { release = resolve; });
    const { calls } = mockCatalog({ products: [], holdCreate }); renderAt();
    const user = userEvent.setup();
    await user.click(createButton());
    const form = productForm();
    await user.type(within(form).getByRole('textbox', { name: /Название/ }), 'Одна модель');
    await user.click(within(form).getByRole('button', { name: 'Создать модель' }));
    expect(within(form).getByRole('button', { name: 'Сохраняем…' })).toBeDisabled();
    fireEvent.submit(form);
    expect(calls.mock.calls.filter((c) => c[0] === '/api/products' && c[1]?.method === 'POST')).toHaveLength(1);
    release();
    expect(await screen.findByRole('heading', { name: 'Одна модель', level: 2 })).toBeInTheDocument();
  });
  it.each([400, 403, 409, 422])('keeps inputs on HTTP %i and shows sanitized error', async (status) => {
    mockCatalog({ products: [], errors: { 'POST /api/products': status } });
    renderAt(); const user = userEvent.setup();
    await user.click(createButton());
    await user.type(within(productForm()).getByRole('textbox', { name: /Название/ }), 'Без потерь');
    await user.click(within(productForm()).getByRole('button', { name: 'Создать модель' }));
    expect(await within(productForm()).findByRole('alert')).toBeInTheDocument();
    expect(within(productForm()).getByRole('textbox', { name: /Название/ })).toHaveValue('Без потерь');
    expect(within(productForm()).queryByText('java.lang')).not.toBeInTheDocument();
  });
  it('retries after a failed page request without fake data', async () => {
    const errors: Record<string, number> = { 'GET /api/products': 500 };
    mockCatalog({ errors }); renderAt();
    expect(await screen.findByRole('alert', {}, { timeout: 5000 })).toHaveTextContent('ошибка сервера');
    delete errors['GET /api/products'];
    await userEvent.setup().click(screen.getByRole('button', { name: 'Повторить' }));
    expect(await screen.findByRole('cell', { name: 'Ergo Comfort' })).toBeInTheDocument();
  });
  it('does not fetch catalog or show create when CATALOG_READ is absent', () => {
    access.permissions = ['CATALOG_MANAGE']; access.roles = ['MANAGER'];
    const { calls } = mockCatalog(); renderAt();
    expect(screen.getByRole('heading', { name: 'Доступ запрещён' })).toBeInTheDocument();
    expect(calls).not.toHaveBeenCalled();
  });
  it('handles a network error and supports retry', async () => {
    const options = { network: true };
    mockCatalog(options); renderAt();
    expect(await screen.findByRole('alert', {}, { timeout: 5000 })).toHaveTextContent('Не удалось подключиться');
    options.network = false;
    await userEvent.setup().click(screen.getByRole('button', { name: 'Повторить' }));
    expect(await screen.findByRole('cell', { name: 'Ergo Comfort' })).toBeInTheDocument();
  });
  it('honors F02 logout behavior on HTTP 401', async () => {
    mockCatalog({ errors: { 'GET /api/products': 401 } }); renderAt();
    await screen.findByRole('alert');
    expect(authSession.authHeaders()).toEqual({});
  });
  it('read-only employee sees no create action', async () => {
    access.permissions = ['CATALOG_READ']; access.roles = ['EMPLOYEE'];
    mockCatalog(); renderAt();
    await screen.findByRole('cell', { name: 'Ergo Comfort' });
    expect(screen.queryByRole('button', { name: '+ Создать товар' })).not.toBeInTheDocument();
  });
});

describe('F03 product detail and variants', () => {
  it('loads detail and paginated variants with actual SKU/color/price', async () => {
    const { calls } = mockCatalog();
    renderAt('/catalog/' + productId);
    expect(screen.getByRole('status')).toHaveTextContent('Загружаем карточку');
    await screen.findByRole('heading', { name: 'Ergo Comfort', level: 2 });
    await screen.findByRole('table');
    expect(await table().findByRole('cell', { name: 'ERGO-BLK' })).toBeInTheDocument();
    expect(table().getByText('8 500 сом')).toBeInTheDocument();
    expect(calls.mock.calls.some((c) => c[0] === '/api/products/' + productId + '/variants?page=0&size=20')).toBe(true);
  });
  it('handles invalid and missing model IDs without data fabrication', async () => {
    const { calls } = mockCatalog(); renderAt('/catalog/not-a-uuid');
    expect(screen.getByRole('heading', { name: 'Страница не найдена' })).toBeInTheDocument();
    expect(calls).not.toHaveBeenCalled();
  });
  it('renders server-side 404 for an unknown model', async () => {
    mockCatalog({ products: [] }); renderAt('/catalog/' + productId);
    expect(await screen.findByRole('heading', { name: 'Страница не найдена' })).toBeInTheDocument();
  });
  it('retrieves later variants using backend pagination', async () => {
    const values = Array.from({ length: 21 }, (_, i) => variant({
      id: '77777777-7777-4777-8777-' + String(i + 1).padStart(12, '0'),
      name: 'Оттенок ' + (i + 1),
    }));
    const { calls } = mockCatalog({ variants: values }); renderAt('/catalog/' + productId);
    await screen.findByRole('table');
    await userEvent.setup().click(screen.getByRole('button', { name: 'Вперёд' }));
    expect(await table().findByRole('cell', { name: 'Оттенок 21' })).toBeInTheDocument();
    expect(calls.mock.calls.some((c) => String(c[0]) ===
      '/api/products/' + productId + '/variants?page=1&size=20')).toBe(true);
  });
  it('shows an empty variants list and lets manager add a variant', async () => {
    const { calls } = mockCatalog({ variants: [] }); renderAt('/catalog/' + productId);
    await screen.findByText('У этой модели пока нет вариаций');
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: '+ Добавить вариацию' }));
    const form = screen.getByRole('form', { name: 'Создание вариации' });
    await user.type(within(form).getByRole('textbox', { name: /Название вариации/ }), 'Светло-серый');
    await user.type(within(form).getByRole('combobox', { name: /^Цвет/ }), 'Светло-серый');
    await user.type(within(form).getByRole('textbox', { name: /Рекомендуемая цена/ }), '8500,25');
    await user.click(within(form).getByRole('button', { name: 'Добавить вариацию' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Вариация добавлена');
    const post = calls.mock.calls.find((c) => c[1]?.method === 'POST' && String(c[0]).endsWith('/variants'));
    expect(JSON.parse(String(post?.[1]?.body))).toEqual({
      name: 'Светло-серый', sku: null, color: 'Светло-серый', recommendedSalePrice: '8500.25',
    });
  });
  it('updates the model and refreshes its name without reloading', async () => {
    const { calls } = mockCatalog(); renderAt('/catalog/' + productId);
    const user = userEvent.setup();
    await screen.findByRole('heading', { name: 'Ergo Comfort', level: 2 });
    await user.click(screen.getByRole('button', { name: 'Изменить модель' }));
    const form = screen.getByRole('form', { name: 'Редактирование модели' });
    await user.clear(within(form).getByRole('textbox', { name: /Название/ }));
    await user.type(within(form).getByRole('textbox', { name: /Название/ }), 'Ergo Plus');
    await user.click(within(form).getByRole('button', { name: 'Сохранить модель' }));
    expect(await screen.findByRole('heading', { name: 'Ergo Plus', level: 2 })).toBeInTheDocument();
    expect(calls.mock.calls.some((c) => c[1]?.method === 'PUT' && c[0] === '/api/products/' + productId)).toBe(true);
  });
  it('updates an existing variant and accepts optional SKU', async () => {
    const { calls } = mockCatalog(); renderAt('/catalog/' + productId);
    const user = userEvent.setup();
    await screen.findByRole('table');
    await table().findByRole('cell', { name: 'ERGO-BLK' });
    await user.click(table().getByRole('button', { name: 'Изменить Чёрный' }));
    const form = screen.getByRole('form', { name: 'Редактирование вариации' });
    await user.clear(within(form).getByRole('textbox', { name: /SKU/ }));
    await user.click(within(form).getByRole('button', { name: 'Сохранить вариацию' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Вариация обновлена');
    const put = calls.mock.calls.find((c) => c[1]?.method === 'PUT' && c[0] === '/api/product-variants/' + variantId);
    expect(JSON.parse(String(put?.[1]?.body)).sku).toBeNull();
  });
  it('requires confirmation to deactivate a product and can reactivate it', async () => {
    const { calls } = mockCatalog(); renderAt('/catalog/' + productId);
    const user = userEvent.setup();
    await screen.findByRole('heading', { name: 'Ergo Comfort', level: 2 });
    await user.click(screen.getByRole('button', { name: 'Деактивировать модель' }));
    expect(screen.getByRole('alertdialog')).toHaveTextContent('не удаляет');
    expect(calls.mock.calls.some((c) => String(c[0]).endsWith('/deactivate'))).toBe(false);
    await user.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Деактивировать' }));
    expect(await screen.findByRole('button', { name: 'Активировать модель' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Активировать модель' }));
    expect(await screen.findByRole('button', { name: 'Деактивировать модель' })).toBeInTheDocument();
  });
  it('deactivates and reactivates only the chosen variant', async () => {
    const { products } = mockCatalog(); renderAt('/catalog/' + productId);
    const user = userEvent.setup();
    await screen.findByRole('table');
    await table().findByRole('cell', { name: 'ERGO-BLK' });
    await user.click(table().getByRole('button', { name: 'Деактивировать Чёрный' }));
    await user.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Деактивировать' }));
    expect(await table().findByRole('button', { name: 'Активировать Чёрный' })).toBeInTheDocument();
    expect(products[0]?.active).toBe(true);
    await user.click(table().getByRole('button', { name: 'Активировать Чёрный' }));
    expect(await table().findByRole('button', { name: 'Деактивировать Чёрный' })).toBeInTheDocument();
  });
  it('retains unsaved edits and displays safe error after a rejected PUT', async () => {
    mockCatalog({ errors: { ['PUT /api/products/' + productId]: 409 } });
    renderAt('/catalog/' + productId);
    const user = userEvent.setup();
    await screen.findByRole('heading', { name: 'Ergo Comfort', level: 2 });
    await user.click(screen.getByRole('button', { name: 'Изменить модель' }));
    const form = screen.getByRole('form', { name: 'Редактирование модели' });
    await user.clear(within(form).getByRole('textbox', { name: /Название/ }));
    await user.type(within(form).getByRole('textbox', { name: /Название/ }), 'Новое имя');
    await user.click(within(form).getByRole('button', { name: 'Сохранить модель' }));
    expect(await within(form).findByRole('alert')).toHaveTextContent('противоречит');
    expect(within(form).getByRole('textbox', { name: /Название/ })).toHaveValue('Новое имя');
    expect(screen.getByRole('heading', { name: 'Ergo Comfort', level: 2 })).toBeInTheDocument();
  });
  it('keeps product active on a rejected deactivation, reports 403 instead of success', async () => {
    mockCatalog({ errors: { ['POST /api/products/' + productId + '/deactivate']: 403 } });
    renderAt('/catalog/' + productId);
    const user = userEvent.setup();
    await screen.findByRole('heading', { name: 'Ergo Comfort', level: 2 });
    await user.click(screen.getByRole('button', { name: 'Деактивировать модель' }));
    await user.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Деактивировать' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('недостаточно прав');
    expect(screen.getByRole('button', { name: 'Деактивировать модель' })).toBeInTheDocument();
  });
  it('read-only permissions hide all model and variant mutations', async () => {
    access.permissions = ['CATALOG_READ']; access.roles = ['EMPLOYEE'];
    mockCatalog(); renderAt('/catalog/' + productId);
    await screen.findByRole('table');
    await table().findByRole('cell', { name: 'ERGO-BLK' });
    expect(screen.queryByRole('button', { name: 'Изменить модель' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '+ Добавить вариацию' })).not.toBeInTheDocument();
    expect(table().queryByRole('button', { name: /Деактивировать/ })).not.toBeInTheDocument();
  });
});

describe('F03 value and F02.1 regression contracts', () => {
  it.each(['0', '8500', '8500.1', '8500,25', '99999999999999999.99'])('accepts exact decimal %s', (amount) => {
    expect(validateMoney(amount)).toBeNull();
  });
  it.each(['', '-1', '1.234', '1e5', 'NaN', '100000000000000000', '1,234'])('rejects invalid money %s', (amount) => {
    expect(validateMoney(amount)).not.toBeNull();
  });
  it('validates names and accepts custom color plus absent SKU', () => {
    expect(validateProduct({ name: '', category: '', description: '' })).not.toBeNull();
    expect(validateProduct({ name: 'x'.repeat(201), category: '', description: '' })).not.toBeNull();
    expect(validateVariant({ name: 'Серый', color: 'Светло-серый', sku: '', price: '0' })).toBeNull();
  });
  it('formats KGS for display without floating-point accounting', () => {
    expect(formatCatalogPrice('8500.25')).toBe('8\u00a0500,25\u00a0сом');
  });
  it('keeps separate F02.1 daily closing route working', () => {
    access.permissions = ['DAILY_CLOSING_READ'];
    mockCatalog(); renderAt('/daily-closing');
    expect(screen.getByRole('heading', { name: 'Закрытие дня', level: 2 })).toBeInTheDocument();
  });
});
