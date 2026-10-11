import { apiClient } from '../../shared/api/client';
import type { ClosingAccess, ClosingInput, ClosingPreview, ClosingReport, CompletedSales } from './types';

const root = '/api/finance/closings';
const dayPath = (date: string) => root + '/' + encodeURIComponent(date);
export const closingKeys = {
  all: ['daily-closing'] as const,
  dates: ['daily-closing', 'dates'] as const,
  access: (date: string) => ['daily-closing', date, 'access'] as const,
  report: (date: string) => ['daily-closing', date, 'report'] as const,
  preview: (date: string) => ['daily-closing', date, 'preview'] as const,
  sales: (date: string) => ['daily-closing', date, 'sales'] as const,
};
export const closingApi = {
  dates: (): Promise<string[]> => apiClient.get(root),
  access: (date: string): Promise<ClosingAccess> => apiClient.get(dayPath(date) + '/access'),
  report: (date: string): Promise<ClosingReport> => apiClient.get(dayPath(date)),
  preview: (date: string): Promise<ClosingPreview> => apiClient.get(dayPath(date) + '/preview'),
  sales: (date: string): Promise<CompletedSales> => apiClient.get(dayPath(date) + '/sales'),
  close: (date: string, payload: ClosingInput): Promise<ClosingReport> =>
    apiClient.post(dayPath(date), payload),
};
