// Backend kg.chairx.sale.api contracts. No cost, margin, or finance-ledger fields.
export type SaleStatus = 'DRAFT' | 'CONFIRMED' | 'FULFILLED' | 'CANCELLED';
export type FulfillmentType = 'SELF_PICKUP' | 'CITY_DELIVERY' | 'REGION_DELIVERY';
export type Money = string | number;

export interface SaleSummary {
  id: string; saleNumber: string; createdAt: string;
  customerId: string | null; customerName: string | null; total: Money;
  status: SaleStatus; fulfillmentType: FulfillmentType | null;
  quantity: number; products: string | null; paymentStatus: string | null;
  deliveryStatus: string | null; plannedDeliveryDate: string | null;
}
export interface SalePageResponse { items: SaleSummary[]; page: number; size: number; total: number }
export interface SaleItemResponse {
  id: string; productVariantId: string; warehouseId: string;
  quantity: number; unitSalePrice: Money; total: Money;
}
export interface SaleResponse {
  id: string; saleNumber: string; customerId: string | null;
  fulfillmentType: FulfillmentType | null; status: SaleStatus; items: SaleItemResponse[];
  total: Money; createdBy: string; createdAt: string; fulfilledBy: string | null;
  fulfilledAt: string | null; cancelledBy: string | null; cancelledAt: string | null;
  comment: string | null;
}
export interface SaleItemInput {
  productVariantId: string; warehouseId: string; quantity: number; unitSalePrice: string;
}
export interface DraftInput {
  customerId: string | null;
  fulfillmentType: FulfillmentType | null;
  items: SaleItemInput[];
  comment: string | null;
}
export interface CreateDraftInput extends DraftInput { idempotencyKey: string }
export interface SaleFilters {
  status: SaleStatus | ''; from: string; to: string; number: string; page: number;
}
