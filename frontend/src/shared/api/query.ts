import { QueryClient } from '@tanstack/react-query';
import { ApiClientError } from './api-error';

export const queryKeys = {
  catalog: ['catalog'] as const,
  inventory: ['inventory'] as const,
  purchases: ['purchases'] as const,
  sales: ['sales'] as const,
  customers: ['customers'] as const,
  suppliers: ['suppliers'] as const,
  deliveries: ['deliveries'] as const,
  finance: ['finance'] as const,
  dailyClosing: ['daily-closing'] as const,
};

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: false,
        retry: (failureCount, error) => {
          if (error instanceof ApiClientError) {
            if (error.kind === 'abort') return false;
            if (error.kind === 'http' && error.status !== undefined && error.status < 500) {
              return false;
            }
          }
          return failureCount < 1;
        },
      },
      mutations: { retry: false },
    },
  });
}

