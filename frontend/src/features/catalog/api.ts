import { apiClient } from '../../shared/api/client';
import type { Product, ProductInput, ProductPage, ProductVariant, VariantInput } from './types';

const idPath = (id: string) => encodeURIComponent(id);

export const catalogKeys = {
  products: (page: number) => ['catalog', 'products', page] as const,
  productsPrefix: ['catalog', 'products'] as const,
  product: (id: string) => ['catalog', 'product', id] as const,
  variants: (id: string, page: number) => ['catalog', 'variants', id, page] as const,
  variantsPrefix: (id: string) => ['catalog', 'variants', id] as const,
};

export const catalogApi = {
  listProducts: (page: number) =>
    apiClient.get<ProductPage<Product>>('/api/products?page=' + page + '&size=20'),
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
