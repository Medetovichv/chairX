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
  customerPhone?: string | null; deliveryAddress?: string | null;
  returnedQuantity?: number;
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
export type SaleAuditAction =
  'DRAFT_CREATED' | 'DRAFT_UPDATED' | 'CONFIRMED' | 'FULFILLED' | 'CANCELLED';
export interface SaleHistoryEvent {
  action: SaleAuditAction; actor: string; occurredAt: string;
}
export interface SaleHistoryPage {
  items: SaleHistoryEvent[]; page: number; size: number; total: number;
}
export interface SaleComment {
  id: string; saleId: string; author: string; text: string; createdAt: string;
}
export interface SaleCommentsPage {
  items: SaleComment[]; page: number; size: number; total: number;
}
export interface CreateSaleCommentInput {
  idempotencyKey: string; text: string;
}
export interface SaleItemInput {
  productVariantId: string; warehouseId: string; quantity: number; unitSalePrice: string;
}
export interface InlineDeliveryDetails {
  recipientName: string | null;
  recipientPhone: string;
  address: string;
  cityRegion: string | null;
  deliveryCost: string;
  carrierName: string | null;
  trackingNumber: string | null;
  comment: string | null;
  plannedDeliveryDate: string | null;
}
export interface DraftInput {
  deliveryDetails?: InlineDeliveryDetails;
  customerId: string | null;
  fulfillmentType: FulfillmentType | null;
  items: SaleItemInput[];
  comment: string | null;
}
export interface CreateDraftInput extends DraftInput { idempotencyKey: string }
export interface SaleFilters {
  status: SaleStatus | ''; from: string; to: string; number: string; page: number; operational?: boolean;
}
