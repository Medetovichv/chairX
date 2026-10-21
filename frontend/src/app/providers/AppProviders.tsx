import { useState, type ReactNode } from 'react';
import { QueryClientProvider } from '@tanstack/react-query';
import { createBrowserRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import { createQueryClient } from '../../shared/api/query';
import { AuthProvider } from '../../features/auth/AuthProvider';
import { AppRouter } from '../router/AppRouter';

// Create the data router once, outside React render, to support SPA navigation blocking.
const router = createBrowserRouter([{ path: '*', element: <AppRouter /> }]);

export function AppProviders({ children: _children }: { children?: ReactNode }) {
  const [queryClient] = useState(createQueryClient);
  return <QueryClientProvider client={queryClient}>
    <AuthProvider><RouterProvider router={router}/></AuthProvider>
  </QueryClientProvider>;
}
