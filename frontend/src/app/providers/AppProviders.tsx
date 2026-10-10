import { useState, type ReactNode } from 'react';
import { QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router';
import { createQueryClient } from '../../shared/api/query';
import { AuthProvider } from '../../features/auth/AuthProvider';

export function AppProviders({ children }: { children: ReactNode }) {
  const [queryClient] = useState(createQueryClient);

  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider><BrowserRouter>{children}</BrowserRouter></AuthProvider>
    </QueryClientProvider>
  );
}
