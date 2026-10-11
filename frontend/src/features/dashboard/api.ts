import { apiClient } from '../../shared/api/client';
import type { DashboardOverview } from './types';

export const dashboardKeys = {
  overview: (userId: string) => ['dashboard', 'overview', userId] as const,
};
export const dashboardApi = {
  overview: (): Promise<DashboardOverview> => apiClient.get('/api/dashboard/overview'),
};
