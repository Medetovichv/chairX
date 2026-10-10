// Mirrors backend kg.chairx.product.api records; no stock, purchasing or payment fields.
export interface Product {
  id: string;
  name: string;
  description: string | null;
  category: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ProductVariant {
  id: string;
  productId: string;
  name: string;
  sku: string | null;
  color: string | null;
  // Jackson serializes Java BigDecimal as a JSON number; requests use an
  // exact decimal string, accepted by Jackson, to avoid float arithmetic.
  recommendedSalePrice: string | number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ProductPage<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ProductInput {
  name: string;
  description: string | null;
  category: string | null;
}

export interface VariantInput {
  name: string;
  color: string | null;
  sku: string | null;
  recommendedSalePrice: string;
}
