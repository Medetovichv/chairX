import { apiClient } from '../../shared/api/client';
import type { DashboardOverview } from './types';

export const dashboardKeys = {
  overview: (userId: string) => ['dashboard', 'overview', userId] as const,
};

function validMetric(x: unknown): boolean {
  return x === null || x === undefined || typeof x === 'number' && Number.isFinite(x) && x >= 0;
}
function validate(value: unknown): DashboardOverview {
  if (value === null || typeof value !== 'object') throw new Error('Сервер вернул некорректную сводку.');
  const x = value as Record<string, unknown>;
  const isDay = (v: unknown) => typeof v === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(v)
    && !Number.isNaN(new Date(v + 'T12:00:00+06:00').getTime());
  if (!isDay(x.businessDate) || !Array.isArray(x.ordersByDay) || !Array.isArray(x.recentSales) ||
      !['orders30Days', 'activeOrders', 'pendingDeliveries', 'delayedDeliveries',
        'availableUnits', 'lowStockVariants'].every(key => validMetric(x[key])) ||
      !x.ordersByDay.every((d: unknown) => d && typeof d === 'object' &&
        isDay((d as Record<string, unknown>).day) &&
        Number.isSafeInteger((d as Record<string, unknown>).orders) &&
        Number((d as Record<string, unknown>).orders) >= 0) ||
      !x.recentSales.every((s: unknown) => s && typeof s === 'object' &&
        typeof (s as Record<string, unknown>).id === 'string' &&
        typeof (s as Record<string, unknown>).saleNumber === 'string')) {
    throw new Error('Сервер вернул некорректную сводку.');
  }
  // Jackson may omit null metrics: missing authorization is still NOT zero.
  return { ...x,
    orders30Days: x.orders30Days ?? null,
    activeOrders: x.activeOrders ?? null,
    pendingDeliveries: x.pendingDeliveries ?? null,
    delayedDeliveries: x.delayedDeliveries ?? null,
    availableUnits: x.availableUnits ?? null,
    lowStockVariants: x.lowStockVariants ?? null,
  } as DashboardOverview;
}
export const dashboardApi = {
  overview: async (): Promise<DashboardOverview> => validate(await apiClient.get<unknown>('/api/dashboard/overview')),
};
