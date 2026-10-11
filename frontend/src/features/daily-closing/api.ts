import { apiClient } from '../../shared/api/client';
import type { ClosingAccess, ClosingInput, ClosingPreview, ClosingReport, CompletedSales, ClosingUpdate, ClosingAudit, UnlockGrant } from './types';

const root = '/api/finance/closings';
const dayPath = (date: string) => root + '/' + encodeURIComponent(date);
export const closingKeys = {
  all: ['daily-closing'] as const,
  dates: ['daily-closing', 'dates'] as const,
  access: (date: string) => ['daily-closing', date, 'access'] as const,
  report: (date: string) => ['daily-closing', date, 'report'] as const,
  preview: (date: string) => ['daily-closing', date, 'preview'] as const,
  sales: (date: string) => ['daily-closing', date, 'sales'] as const,
  history: (date: string) => ['daily-closing', date, 'history'] as const,
};
export const closingApi = {
  dates: (): Promise<string[]> => apiClient.get(root),
  access: (date: string): Promise<ClosingAccess> => apiClient.get(dayPath(date) + '/access'),
  report: (date: string): Promise<ClosingReport> => apiClient.get(dayPath(date)),
  preview: (date: string): Promise<ClosingPreview> => apiClient.get(dayPath(date) + '/preview'),
  sales: (date: string): Promise<CompletedSales> => apiClient.get(dayPath(date) + '/sales'),
  close: (date: string, payload: ClosingInput): Promise<ClosingReport> =>
    apiClient.post(dayPath(date), payload),
  update: (date: string, payload: ClosingUpdate): Promise<ClosingReport> =>
    apiClient.put(dayPath(date), payload),
  unlock: (date: string, reason: string): Promise<UnlockGrant> =>
    apiClient.post(dayPath(date) + '/unlock', { reason }),
  lock: (date: string): Promise<{revoked: boolean}> =>
    apiClient.post(dayPath(date) + '/lock', {}),
  history: (date: string): Promise<ClosingAudit[]> =>
    apiClient.get(dayPath(date) + '/history'),
  correctedExpense: (date: string, payload: import('../finance/types').ExpenseInput):
    Promise<import('../finance/types').ExpenseResponse> =>
    apiClient.post(dayPath(date) + '/expenses', payload),
};
