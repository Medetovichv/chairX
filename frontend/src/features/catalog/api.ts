import { apiClient } from '../../shared/api/client';
import type { Product, ProductInput, ProductPage, ProductVariant, VariantInput } from './types';

const idPath = (id: string) => encodeURIComponent(id);
export interface CatalogListFilters {
  search?: string;
  category?: string;
  active?: boolean | null;
}
const normalized = (filters: CatalogListFilters = {}): CatalogListFilters => ({
  search: filters.search?.trim() ?? '',
  category: filters.category?.trim() ?? '',
  active: filters.active ?? null,
});
function catalogListUrl(page: number, filters: CatalogListFilters = {}): string {
  const f = normalized(filters);
  const query = new URLSearchParams({ page: String(page), size: '20' });
  if (f.search) query.set('search', f.search);
  if (f.category) query.set('category', f.category);
  if (f.active !== null) query.set('active', String(f.active));
  return '/api/products?' + query.toString();
}

export const catalogKeys = {
  products: (page: number, filters: CatalogListFilters = {}) =>
    ['catalog', 'products', page, normalized(filters)] as const,
  productsPrefix: ['catalog', 'products'] as const,
  product: (id: string) => ['catalog', 'product', id] as const,
  variants: (id: string, page: number) => ['catalog', 'variants', id, page] as const,
  variantsPrefix: (id: string) => ['catalog', 'variants', id] as const,
};

export const catalogApi = {
  listProducts: (page: number, filters: CatalogListFilters = {}) =>
    apiClient.get<ProductPage<Product>>(catalogListUrl(page, filters)),
  getProduct: (id: string) =>
    apiClient.get<Product>('/api/products/' + idPath(id)),
  createProduct: (input: ProductInput) =>
    apiClient.post<Product>('/api/products', input),
  updateProduct: (id: string, input: ProductInput) =>
    apiClient.put<Product>('/api/products/' + idPath(id), input),
  setProductActive: (id: string, active: boolean) =>
    apiClient.post<Product>('/api/products/' + idPath(id) + '/' + (active ? 'activate' : 'deactivate')),
  listVariants: (id: string, page: number) =>
    apiClient.get<ProductPage<ProductVariant>>('/api/products/' + idPath(id) + '/variants?page=' + page + '&size=20'),
  createVariant: (id: string, input: VariantInput) =>
    apiClient.post<ProductVariant>('/api/products/' + idPath(id) + '/variants', input),
  getVariant: (id: string) =>
    apiClient.get<ProductVariant>('/api/product-variants/' + idPath(id)),
  updateVariant: (id: string, input: VariantInput) =>
    apiClient.put<ProductVariant>('/api/product-variants/' + idPath(id), input),
  setVariantActive: (id: string, active: boolean) =>
    apiClient.post<ProductVariant>('/api/product-variants/' + idPath(id) + '/' + (active ? 'activate' : 'deactivate')),
};
