import { apiClient } from '../../shared/api/client';
import type { Money } from './types';

export interface SaleStatistics {
  orders30Days: number;
  fulfilled30Days: number;
  inWork30Days: number;
  averageCheck30Days: Money;
}
export const saleStatisticsKeys = {
  last30Days: ['sales', 'statistics', 'last30Days'] as const,
};
export const saleStatisticsApi = {
  last30Days: (): Promise<SaleStatistics> =>
    apiClient.get<SaleStatistics>('/api/sales/statistics'),
};
