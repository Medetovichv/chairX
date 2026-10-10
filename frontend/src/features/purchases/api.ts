import { apiClient } from '../../shared/api/client';
import type { Page, Purchase, PurchaseFilters, PurchaseInput, PurchaseLine, ReceivingLine,
  ReceivingSummary, ReceiptInput, Supplier, SupplierInput, SupplierPage, PaymentSummary } from './types';

export const PAGE_SIZE = 20;
const id = (value: string) => encodeURIComponent(value);
function params(input: Record<string, string | number | null | undefined>) {
  const p = new URLSearchParams();
  for (const [key, value] of Object.entries(input)) {
    if (value !== null && value !== undefined && value !== '') p.set(key, String(value));
  }
  return p.toString();
}
export const purchaseKeys = {
  overviews: ['purchases', 'overview'] as const,
  overview: (f: PurchaseFilters) => ['purchases', 'overview', f.status, f.supplierId, f.from, f.to, f.number, f.page, PAGE_SIZE] as const,
  detail: (purchaseId: string) => ['purchases', 'detail', purchaseId] as const,
  receiving: ['purchases', 'receiving'] as const,
  receivingPage: (page: number) => ['purchases', 'receiving', page, PAGE_SIZE] as const,
  receivingSummary: (purchaseId: string) => ['purchases', 'receiving-summary', purchaseId] as const,
  payments: (purchaseId: string) => ['purchases', 'payments', purchaseId] as const,
  suppliers: ['suppliers'] as const,
  supplierPage: (page: number) => ['suppliers', 'page', page, PAGE_SIZE] as const,
  supplierAll: ['suppliers', 'all'] as const,
};

export const purchaseApi = {
  overview: (f: PurchaseFilters) =>
    apiClient.get<Page<PurchaseLine>>('/api/purchases/overview?' + params({
      status: f.status, supplierId: f.supplierId, from: f.from, to: f.to,
      number: f.number, page: f.page, size: PAGE_SIZE,
    })),
  get: (purchaseId: string) => apiClient.get<Purchase>('/api/purchases/' + id(purchaseId)),
  create: (input: PurchaseInput) => apiClient.post<Purchase>('/api/purchases', input),
  update: (purchaseId: string, input: PurchaseInput) =>
    apiClient.put<Purchase>('/api/purchases/' + id(purchaseId), input),
  cargo: (purchaseId: string, cargoCost: string) =>
    apiClient.put<Purchase>('/api/purchases/' + id(purchaseId) + '/cargo', { cargoCost }),
  status: (purchaseId: string, action: 'confirm' | 'cancel') =>
    apiClient.post<Purchase>('/api/purchases/' + id(purchaseId) + '/' + action),
  receiving: (page: number) => apiClient.get<Page<ReceivingLine>>(
    '/api/purchases/receiving?' + params({ page, size: PAGE_SIZE })),
  receivingSummary: (purchaseId: string) =>
    apiClient.get<ReceivingSummary>('/api/purchases/' + id(purchaseId) + '/receiving-summary'),
  // Ignore the cost-free warehouse response: inventory state is refreshed from queries.
  // Backend exposes only WarehouseReceiptResponse to INVENTORY_RECEIVE (Issue #24 fix).
  postReceipt: async (purchaseId: string, request: ReceiptInput): Promise<void> => {
    await apiClient.post<unknown>('/api/purchases/' + id(purchaseId) + '/receipts', request);
  },
  payments: (purchaseId: string) =>
    apiClient.get<PaymentSummary>('/api/purchases/' + id(purchaseId) + '/payments'),
};
export const supplierApi = {
  page: (page: number) => apiClient.get<SupplierPage>('/api/suppliers?' +
    params({ page, size: PAGE_SIZE })),
  async all(): Promise<Supplier[]> {
    // Paged server API has no global autocomplete endpoint. Never silently
    // truncate the supplier picker to just the first 20 records.
    const first = await apiClient.get<SupplierPage>('/api/suppliers?page=0&size=100');
    if (!Number.isInteger(first.totalPages) || first.totalPages > 1000) {
      throw new Error('Список поставщиков слишком большой или некорректен.');
    }
    const items = [...first.items];
    for (let page = 1; page < first.totalPages; page++) {
      const next = await apiClient.get<SupplierPage>('/api/suppliers?page=' + page + '&size=100');
      items.push(...next.items);
    }
    return items;
  },
  get: (supplierId: string) => apiClient.get<Supplier>('/api/suppliers/' + id(supplierId)),
  create: (input: SupplierInput) => apiClient.post<Supplier>('/api/suppliers', input),
  update: (supplierId: string, input: SupplierInput) =>
    apiClient.put<Supplier>('/api/suppliers/' + id(supplierId), input),
  active: (supplierId: string, active: boolean) =>
    apiClient.post<Supplier>('/api/suppliers/' + id(supplierId) + '/' +
      (active ? 'activate' : 'deactivate')),
};
