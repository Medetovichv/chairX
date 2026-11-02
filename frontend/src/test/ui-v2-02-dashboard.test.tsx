import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router';
import { HomePage } from '../pages/HomePage';
import { createQueryClient } from '../shared/api/query';
import { dashboardApi } from '../features/dashboard/api';
import type { DashboardOverview } from '../features/dashboard/types';

const access = vi.hoisted(() => ({ permissions: [] as string[] }));
vi.mock('../features/auth/AuthProvider', () => ({
  useAuth: () => ({
    user: { id: 'staff-1', displayName: 'Айбек', username: 'employee', roles: ['CUSTOM_WAREHOUSE'],
      permissions: access.permissions },
    hasPermission: (p: string) => access.permissions.includes(p),
    hasAnyPermission: (ps: readonly string[]) => ps.some(p => access.permissions.includes(p)),
  }),
}));
vi.mock('../features/dashboard/api', () => ({
  dashboardKeys: { overview: (id: string) => ['dashboard', 'overview', id] },
  dashboardApi: { overview: vi.fn() },
}));

const sample: DashboardOverview = {
  businessDate: '2026-10-11', orders30Days: 18, activeOrders: 10,
  pendingDeliveries: 7, deliveredToday: 4, delayedDeliveries: 3, availableUnits: 142,
  lowStockVariants: 4,
  ordersByDay: [
    { day: '2026-10-05', orders: 2 }, { day: '2026-10-06', orders: 0 },
    { day: '2026-10-07', orders: 1 }, { day: '2026-10-08', orders: 4 },
    { day: '2026-10-09', orders: 5 }, { day: '2026-10-10', orders: 3 },
    { day: '2026-10-11', orders: 3 },
  ],
  recentSales: [{
    id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', saleNumber: 'CHX-2026-00042',
    createdAt: '2026-10-11T07:00:00Z',
    customerId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    customerName: 'Тестовый клиент', total: '8500',
    status: 'CONFIRMED', fulfillmentType: 'SELF_PICKUP', quantity: 1,
    products: 'ChairX Pro', paymentStatus: 'UNPAID',
    deliveryStatus: null, plannedDeliveryDate: null,
  }],
};

function renderHome() {
  return render(<QueryClientProvider client={createQueryClient()}>
    <MemoryRouter><HomePage /></MemoryRouter>
  </QueryClientProvider>);
}

beforeEach(() => {
  access.permissions = [];
  vi.mocked(dashboardApi.overview).mockResolvedValue(sample);
});
afterEach(() => vi.clearAllMocks());

describe('UI-V2-02 real-data dashboard and permission isolation', () => {
  it('never fetches operations when staff has no read permissions', () => {
    renderHome();
    expect(screen.getByText('Показатели недоступны')).toBeInTheDocument();
    expect(dashboardApi.overview).not.toHaveBeenCalled();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('warehouse-only staff sees inventory but no sales/customer or delivery information', async () => {
    access.permissions = ['INVENTORY_READ'];
    vi.mocked(dashboardApi.overview).mockResolvedValue({
      ...sample, orders30Days: null, activeOrders: null, pendingDeliveries: null,
      delayedDeliveries: null, deliveredToday: null, ordersByDay: [], recentSales: [],
    });
    renderHome();
    expect(await screen.findByText('Доступно на складах')).toBeInTheDocument();
    expect(screen.getByText('142')).toBeInTheDocument();
    expect(screen.getByText('Мало товара')).toBeInTheDocument();
    expect(screen.queryByText('Тестовый клиент')).not.toBeInTheDocument();
    expect(screen.queryByText('Заказы за 7 дней')).not.toBeInTheDocument();
    expect(screen.queryByText('Ожидают доставки')).not.toBeInTheDocument();
  });

  it('sales and delivery staff see server metrics, recent linked sales, and no stock', async () => {
    access.permissions = ['SALES_READ', 'DELIVERIES_READ', 'SALES_CREATE'];
    vi.mocked(dashboardApi.overview).mockResolvedValue({
      ...sample, availableUnits: null, lowStockVariants: null,
    });
    renderHome();
    expect(await screen.findByText('Заказов сегодня')).toBeInTheDocument();
    expect(within(screen.getByText('Заказов сегодня').closest('a')!).getByText('3')).toBeInTheDocument();
    expect(screen.getByText('Доставлено сегодня')).toBeInTheDocument();
    expect(within(screen.getByText('Доставлено сегодня').closest('a')!).getByText('4')).toBeInTheDocument();
    expect(screen.getByText('Ожидают доставки')).toBeInTheDocument();
    expect(screen.getByText('Просроченные доставки')).toBeInTheDocument();
    expect(screen.getByText('Заказы за 7 дней')).toBeInTheDocument();
    const table = screen.getByRole('table');
    const link = within(table).getByRole('link', { name: 'Открыть продажу CHX-2026-00042' });
    expect(link).toHaveTextContent('#42');
    expect(link).toHaveAttribute('href', '/sales/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
    expect(screen.getByRole('link', { name: 'Новая продажа' })).toHaveAttribute('href', '/sales/new');
    expect(screen.queryByText('Доступно на складах')).not.toBeInTheDocument();
  });
});
