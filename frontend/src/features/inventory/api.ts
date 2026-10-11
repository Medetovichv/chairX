import { apiClient } from '../../shared/api/client';
import type {
  Warehouse, WarehousePage, WarehouseCreateInput, WarehouseUpdateInput, WarehouseStockSummary, InventoryOverviewPage, InventoryFilters, InventoryBalance,
  MovementPage, TransferInput, TransferPage, TransferResult, InventoryTransfer, TransferFilters,
} from './types';

function query(params: Record<string, string | number | boolean | null>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== null && value !== '') search.set(key, String(value));
  }
  return search.toString();
}
const urlId = (id: string) => encodeURIComponent(id);

export const inventoryKeys = {
  warehouses: ['inventory', 'warehouses'] as const,
  overviewPrefix: ['inventory', 'overview'] as const,
  warehouseSummary: ['inventory', 'warehouse-summary'] as const,
  overview: (f: InventoryFilters) =>
    ['inventory', 'overview', f.warehouseId, f.model, f.variation, f.search ?? '',
      f.includeZero, f.onlyAvailable, f.page, f.size] as const,
  balancePrefix: ['inventory', 'balance'] as const,
  balance: (warehouseId: string, variantId: string) =>
    ['inventory', 'balance', warehouseId, variantId] as const,
  movementsPrefix: ['inventory', 'movements'] as const,
  movements: (warehouseId: string, variantId: string, page: number) =>
    ['inventory', 'movements', warehouseId, variantId, page] as const,
  transfersPrefix: ['inventory', 'transfers'] as const,
  transfers: (f: TransferFilters) =>
    ['inventory', 'transfers', f.warehouseId, f.variantId, f.page, f.size] as const,
  transferDetail: (id: string) => ['inventory', 'transfer-detail', id] as const,
};

export const inventoryApi = {
  // The backend paginates warehouses; fetch all pages sequentially, with a
  // finite explicit guard. Never silently show an incomplete dropdown.
  warehouses: async (): Promise<Warehouse[]> => {
    const first = await apiClient.get<WarehousePage>('/api/warehouses?page=0&size=100');
    if (first.totalPages > 1000) throw new Error('Слишком много страниц складов.');
    const all = [...first.items];
    for (let page = 1; page < first.totalPages; page++) {
      const next = await apiClient.get<WarehousePage>('/api/warehouses?' + query({ page, size: 100 }));
      all.push(...next.items);
    }
    return all;
  },
  warehouseSummary: () => apiClient.get<WarehouseStockSummary[]>('/api/inventory/warehouse-summary'),
  createWarehouse: (input: WarehouseCreateInput) => apiClient.post<Warehouse>('/api/warehouses', input),
  updateWarehouse: (id: string, input: WarehouseUpdateInput) =>
    apiClient.put<Warehouse>('/api/warehouses/' + urlId(id), input),
  activateWarehouse: (id: string) => apiClient.post<Warehouse>('/api/warehouses/' + urlId(id) + '/activate', {}),
  deactivateWarehouse: (id: string) => apiClient.post<Warehouse>('/api/warehouses/' + urlId(id) + '/deactivate', {}),
  overview: (filters: InventoryFilters) =>
    apiClient.get<InventoryOverviewPage>('/api/inventory/overview?' + query({
      warehouseId: filters.warehouseId, model: filters.model, variation: filters.variation,
      search: filters.search ?? '',
      includeZero: filters.includeZero, onlyAvailable: filters.onlyAvailable,
      page: filters.page, size: filters.size,
    })),
  balance: (warehouseId: string, variantId: string) =>
    apiClient.get<InventoryBalance>('/api/inventory/balances/' + urlId(warehouseId) + '/' + urlId(variantId)),
  movements: (warehouseId: string, variantId: string, page: number) =>
    apiClient.get<MovementPage>('/api/inventory/movements?' + query({
      warehouseId, variantId, page, size: 20,
    })),
  transfers: (filters: TransferFilters) =>
    apiClient.get<TransferPage>('/api/inventory/transfers?' + query({
      warehouseId: filters.warehouseId, variantId: filters.variantId,
      page: filters.page, size: filters.size,
    })),
  // Transfer DTOs omit internal FIFO cost on the SERVER, not just in the UI.
  transfer: (payload: TransferInput) =>
    apiClient.post<TransferResult>('/api/inventory/transfers', payload),
  transferById: (id: string) =>
    apiClient.get<InventoryTransfer>('/api/inventory/transfers/' + urlId(id)),
};
