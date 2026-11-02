import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from 'react-router';
import { QueryClientProvider } from '@tanstack/react-query';
import { createQueryClient } from '../shared/api/query';
import { HomePage } from '../pages/HomePage';
import { SalesPage } from '../features/sales/pages/SalesPage';
import { DeliveriesPage } from '../features/deliveries/pages/DeliveriesPage';
import { InventoryPage } from '../features/inventory/pages/InventoryPage';
import { salesApi } from '../features/sales/api';
import { deliveryApi } from '../features/deliveries/api';
import { inventoryApi } from '../features/inventory/api';
import { dashboardApi } from '../features/dashboard/api';
import { useUrlListState } from '../shared/hooks/useUrlListState';

vi.mock('../features/auth/AuthProvider', () => ({
  useAuth: () => ({
    user: { id: 'drilldown-user' },
    hasPermission: () => true,
    hasAnyPermission: () => true,
  }),
}));
vi.mock('../features/sales/api', async (original) => ({
  ...(await original<typeof import('../features/sales/api')>()),
  salesApi: { list: vi.fn() },
}));
vi.mock('../features/deliveries/api', async (original) => ({
  ...(await original<typeof import('../features/deliveries/api')>()),
  deliveryApi: { list: vi.fn() },
}));
vi.mock('../features/inventory/api', async (original) => ({
  ...(await original<typeof import('../features/inventory/api')>()),
  inventoryApi: { overview: vi.fn(), warehouses: vi.fn(), warehouseSummary: vi.fn() },
}));
vi.mock('../features/dashboard/api', async (original) => ({
  ...(await original<typeof import('../features/dashboard/api')>()),
  dashboardApi: { overview: vi.fn() },
}));
const WH = '11111111-1111-4111-8111-111111111111';
const empty = { items: [], page: 0, size: 20, total: 0 };
function HistoryControls() {
  const navigate = useNavigate(),
    location = useLocation();
  return (
    <>
      <button onClick={() => void navigate(-1)}>Back</button>
      <button onClick={() => void navigate(1)}>Forward</button>
      <output aria-label="URL">{location.pathname + location.search}</output>
    </>
  );
}
function setup(path: string, child?: React.ReactNode) {
  return render(
    <QueryClientProvider client={createQueryClient()}>
      <MemoryRouter initialEntries={[path]}>
        <HistoryControls />
        {child ?? (
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/sales" element={<SalesPage />} />
            <Route path="/deliveries" element={<DeliveriesPage />} />
            <Route path="/inventory" element={<InventoryPage />} />
          </Routes>
        )}
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  vi.mocked(salesApi.list).mockResolvedValue(empty);
  vi.mocked(deliveryApi.list).mockResolvedValue(empty);
  vi.mocked(inventoryApi.overview).mockResolvedValue(empty);
  vi.mocked(inventoryApi.warehouseSummary).mockResolvedValue([]);
  vi.mocked(inventoryApi.warehouses).mockResolvedValue([
    {
      id: WH,
      name: 'Warehouse',
      code: 'WH',
      active: true,
      address: null,
      createdAt: '',
      updatedAt: '',
    },
  ]);
  vi.mocked(dashboardApi.overview).mockResolvedValue({
    businessDate: '2026-10-11',
    orders30Days: 5,
    activeOrders: 3,
    pendingDeliveries: 2,
    deliveredToday: 1,
    delayedDeliveries: 1,
    availableUnits: 5,
    lowStockVariants: 2,
    ordersByDay: [{ day: '2026-10-11', orders: 5 }],
    recentSales: [],
  });
});

describe('dashboard drilldowns and browser history', () => {
  it.each([
    [
      'Заказов сегодня',
      '/sales?operational=false&from=2026-10-11&to=2026-10-11',
      salesApi.list,
      { from: '2026-10-11', to: '2026-10-11', operational: false },
    ],
    [
      'В работе',
      '/sales?operational=false&activeOnly=true',
      salesApi.list,
      { activeOnly: true, operational: false },
    ],
    [
      'Ожидают доставки',
      '/deliveries?operational=false&pendingOnly=true',
      deliveryApi.list,
      { pendingOnly: true, operational: false },
    ],
    [
      'Доставлено сегодня',
      '/deliveries?operational=false&deliveredOn=2026-10-11',
      deliveryApi.list,
      { deliveredOn: '2026-10-11', operational: false },
    ],
    [
      'Просроченные доставки',
      '/deliveries?operational=false&pendingOnly=true&plannedTo=2026-10-10',
      deliveryApi.list,
      { pendingOnly: true, plannedTo: '2026-10-10', operational: false },
    ],
    [
      'Доступно на складах',
      '/inventory?availability=recorded',
      inventoryApi.overview,
      { includeZero: false, onlyAvailable: false },
    ],
    [
      'Мало товара',
      '/inventory?lowStock=true',
      inventoryApi.overview,
      { lowStock: true, warehouseId: null },
    ],
  ] as const)(
    '%s links to the matching real server query and survives Back/Forward',
    async (name, url, api, expected) => {
      // Persisted tab filters must not contaminate links from the dashboard.
      sessionStorage.setItem('chairx:f16:list:v1:drilldown-user:sales-search', '"stale"');
      setup('/');
      const link = (await screen.findByText(name)).closest('a')!;
      expect(link).toHaveAttribute('href', url);
      fireEvent.click(link);
      await waitFor(() =>
        expect(api).toHaveBeenCalledWith(expect.objectContaining({ ...expected, page: 0 })),
      );
      expect(screen.getByLabelText('URL')).toHaveTextContent(url);
      fireEvent.click(screen.getByText('Back'));
      expect(await screen.findByText('Рабочий обзор')).toBeInTheDocument();
      fireEvent.click(screen.getByText('Forward'));
      await waitFor(() => expect(screen.getByLabelText('URL')).toHaveTextContent(url));
    },
  );
  it('restores sales status, search, dates, mode and unrelated quick-view URL parameters', async () => {
    setup(
      '/sales?operational=false&from=2026-10-01&to=2026-10-11&number=CHX-7&page=2&source=dashboard',
    );
    expect(screen.getByLabelText('Поиск по номеру заказа')).toHaveValue('CHX-7');
    fireEvent.change(screen.getByLabelText('Фильтр статуса продажи'), {
      target: { value: 'CONFIRMED' },
    });
    await waitFor(() =>
      expect(salesApi.list).toHaveBeenCalledWith(
        expect.objectContaining({
          status: 'CONFIRMED',
          number: 'CHX-7',
          from: '2026-10-01',
          page: 0,
          operational: false,
        }),
      ),
    );
    expect(screen.getByLabelText('URL').textContent).toContain('source=dashboard');
    fireEvent.click(screen.getByText('Back'));
    expect(screen.getByLabelText('Фильтр статуса продажи')).toHaveValue('');
    await waitFor(() =>
      expect(salesApi.list).toHaveBeenCalledWith(
        expect.objectContaining({ page: 2, number: 'CHX-7' }),
      ),
    );
    fireEvent.click(screen.getByText('Forward'));
    expect(screen.getByLabelText('Фильтр статуса продажи')).toHaveValue('CONFIRMED');
  });
  it('restores delivery planned and creation dates, region, scope and page on direct load/history', async () => {
    setup(
      '/deliveries?operational=false&pendingOnly=true&plannedTo=2026-10-10&from=2026-09-01&cityRegion=Ош&page=2',
    );
    fireEvent.change(screen.getByLabelText('Плановая дата по'), {
      target: { value: '2026-10-09' },
    });
    await waitFor(() =>
      expect(deliveryApi.list).toHaveBeenCalledWith(
        expect.objectContaining({
          page: 0,
          plannedTo: '2026-10-09',
          cityRegion: 'Ош',
          pendingOnly: true,
          from: '2026-09-01',
        }),
      ),
    );
    fireEvent.click(screen.getByText('Back'));
    expect(screen.getByLabelText('Плановая дата по')).toHaveValue('2026-10-10');
    await waitFor(() =>
      expect(deliveryApi.list).toHaveBeenCalledWith(
        expect.objectContaining({ page: 2, plannedTo: '2026-10-10' }),
      ),
    );
    fireEvent.click(screen.getByText('Forward'));
    expect(screen.getByLabelText('Плановая дата по')).toHaveValue('2026-10-09');
    fireEvent.click(screen.getByRole('button', { name: 'Сегодня' }));
    expect(screen.getByRole('button', { name: 'Сегодня' })).toHaveAttribute('aria-pressed', 'true');
    fireEvent.click(screen.getByRole('button', { name: 'Предстоящие' }));
    expect(screen.getByRole('button', { name: 'Предстоящие' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    fireEvent.click(screen.getByText('Back'));
    expect(screen.getByRole('button', { name: 'Сегодня' })).toHaveAttribute('aria-pressed', 'true');
  });
  it('restores inventory warehouse including clearing it, all search fields, availability and page', async () => {
    setup(
      '/inventory?warehouseId=' +
        WH +
        '&search=SKU&model=Ergo&variation=Black&availability=available&page=2',
    );
    await screen.findByRole('option', { name: 'Warehouse' });
    fireEvent.change(screen.getByLabelText('Выбор склада'), { target: { value: '' } });
    await waitFor(() =>
      expect(inventoryApi.overview).toHaveBeenCalledWith(
        expect.objectContaining({
          warehouseId: null,
          model: 'Ergo',
          variation: 'Black',
          search: 'SKU',
          onlyAvailable: true,
          page: 0,
        }),
      ),
    );
    fireEvent.click(screen.getByText('Back'));
    expect(screen.getByLabelText('Выбор склада')).toHaveValue(WH);
    await waitFor(() =>
      expect(inventoryApi.overview).toHaveBeenCalledWith(
        expect.objectContaining({ warehouseId: WH, page: 2 }),
      ),
    );
    fireEvent.click(screen.getByText('Forward'));
    expect(screen.getByLabelText('Выбор склада')).toHaveValue('');
  });
  it('normalizes malformed URL values and blocks reversed date ranges', async () => {
    setup('/sales?status=INVALID&page=-2&from=2026-02-30&operational=wat');
    await waitFor(() =>
      expect(salesApi.list).toHaveBeenCalledWith(
        expect.objectContaining({ status: '', page: 0, from: '', operational: true }),
      ),
    );
    fireEvent.change(screen.getByLabelText('Продажи с даты'), { target: { value: '2026-10-11' } });
    fireEvent.change(screen.getByLabelText('Продажи по дату'), { target: { value: '2026-10-01' } });
    expect(screen.getByRole('alert')).toHaveTextContent('Начальная дата');
    expect(
      vi
        .mocked(salesApi.list)
        .mock.calls.some(([f]) => f.from === '2026-10-11' && f.to === '2026-10-01'),
    ).toBe(false);
  });
  it('updates pagination in URL without losing quick-view or unknown parameters', () => {
    function List() {
      const [f, set] = useUrlListState({ page: 0, status: '' });
      return (
        <button onClick={() => set((x) => ({ ...x, page: x.page + 1 }))}>Page {f.page}</button>
      );
    }
    setup('/sales?quick=sale-id&source=dashboard', <List />);
    fireEvent.click(screen.getByText('Page 0'));
    expect(screen.getByLabelText('URL').textContent).toContain(
      'quick=sale-id&source=dashboard&page=1',
    );
    fireEvent.click(screen.getByText('Back'));
    expect(screen.getByText('Page 0')).toBeInTheDocument();
    fireEvent.click(screen.getByText('Forward'));
    expect(screen.getByText('Page 1')).toBeInTheDocument();
  });
});
