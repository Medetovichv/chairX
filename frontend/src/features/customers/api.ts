import { apiClient } from '../../shared/api/client';
import type { Customer, CustomerInput, CustomerListing, CustomerOverviewPage, CustomerPage, SalePage } from './types';

export const CUSTOMER_PAGE_SIZE = 20;
const idPath = (id: string) => encodeURIComponent(id);

export const customerKeys = {
  lists: ['customers', 'lists'] as const,
  listing: (withSales: boolean, query: string, page: number) =>
    ['customers', 'lists', withSales ? 'overview' : 'basic', query, page, CUSTOMER_PAGE_SIZE] as const,
  detail: (id: string) => ['customers', 'detail', id] as const,
  duplicates: (query: string) => ['customers', 'duplicates', query] as const,
  history: (id: string, page: number) => ['customers', 'sales', id, page, CUSTOMER_PAGE_SIZE] as const,
};

export const customersApi = {
  async list(withSales: boolean, query: string, page: number): Promise<CustomerListing> {
    if (withSales) {
      const params = new URLSearchParams({ page: String(page), size: String(CUSTOMER_PAGE_SIZE) });
      if (query) params.set('query', query);
      const data = await apiClient.get<CustomerOverviewPage>('/api/customers/overview?' + params);
      return { kind: 'overview', data };
    }
    if (query) {
      // Backend search has a hard 20-result cap; never invent a total/pagination.
      const items = await apiClient.get<Customer[]>('/api/customers/search?query=' + encodeURIComponent(query));
      return { kind: 'limited-search', items };
    }
    const data = await apiClient.get<CustomerPage>(
      '/api/customers?page=' + page + '&size=' + CUSTOMER_PAGE_SIZE,
    );
    return { kind: 'basic', data };
  },
  search: (query: string) =>
    apiClient.get<Customer[]>('/api/customers/search?query=' + encodeURIComponent(query)),
  get: (id: string) => apiClient.get<Customer>('/api/customers/' + idPath(id)),
  create: (input: CustomerInput) => apiClient.post<Customer>('/api/customers', input),
  update: (id: string, input: CustomerInput) =>
    apiClient.put<Customer>('/api/customers/' + idPath(id), input),
  setActive: (id: string, active: boolean) =>
    apiClient.post<Customer>('/api/customers/' + idPath(id) + '/' +
      (active ? 'activate' : 'deactivate')),
  history: (id: string, page: number) =>
    apiClient.get<SalePage>('/api/sales/customer/' + idPath(id) +
      '?page=' + page + '&size=' + CUSTOMER_PAGE_SIZE),
};
