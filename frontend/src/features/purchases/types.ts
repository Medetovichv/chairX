export type PurchaseStatus = 'DRAFT' | 'CONFIRMED' | 'PARTIALLY_RECEIVED' | 'RECEIVED' | 'CANCELLED';
export type Money = string | number;
export interface PurchaseItem {
  id: string; productVariantId: string; lineNumber: number;
  orderedQuantity: number; receivedQuantity: number;
  purchaseUnitCost: Money; allocatedCargoCost: Money | null; finalUnitCost: Money | null;
}
export interface Purchase {
  id: string; supplierId: string; status: PurchaseStatus; cargoCost: Money | null;
  cargoAllocationMethod: string | null; costsLockedAt: string | null; confirmedAt: string | null;
  comment: string | null; createdBy: string; createdAt: string; updatedAt: string;
  items: PurchaseItem[];
}
export interface PurchaseLine {
  id: string; supplierId: string; supplierName: string; status: PurchaseStatus;
  createdAt: string; itemCount: number; orderedQuantity: number;
  receivedQuantity: number; remainingQuantity: number; goodsCost: Money;
  cargoCost: Money | null; totalCost: Money | null; lastReceiptAt: string | null;
}
export interface Page<T> { items: T[]; page: number; size: number; total: number }
export interface PurchaseInputLine { productVariantId: string; orderedQuantity: number; purchaseUnitCost: string }
export interface PurchaseInput { supplierId: string; items: PurchaseInputLine[]; cargoCost: string | null; comment: string | null }
export interface PurchaseFilters {
  status: PurchaseStatus | ''; supplierId: string; from: string; to: string;
  number: string; page: number;
}
export interface Supplier { id: string; name: string; contactInformation: string | null;
  comment: string | null; active: boolean; createdAt: string; updatedAt: string }
export interface SupplierInput { name: string; contactInformation: string | null; comment: string | null }
export interface SupplierPage { items: Supplier[]; page: number; size: number; totalElements: number; totalPages: number }
export interface ReceivingLine {
  purchaseId: string; status: PurchaseStatus; createdAt: string; purchaseItemId: string;
  productVariantId: string; model: string; variant: string;
  orderedQuantity: number; receivedQuantity: number; remainingQuantity: number;
}
export interface ReceivingSummary { purchaseId: string; status: PurchaseStatus; items: ReceivingLine[] }
export interface ReceiptLine { purchaseItemId: string; quantity: number }
export interface ReceiptInput { idempotencyKey: string; warehouseId: string; items: ReceiptLine[]; comment: string | null }
export interface Payment {
  id: string; purchaseId: string; paymentKind: 'SUPPLIER' | 'CARGO';
  account: string; amount: Money; idempotencyKey: string;
  reference: string | null; comment: string | null; createdBy: string; createdAt: string;
}
export interface PaymentSummary {
  purchaseId: string; supplierTotal: Money; cargoTotal: Money;
  paidSupplier: Money; paidCargo: Money; remainingSupplier: Money;
  remainingCargo: Money; payments: Payment[];
}
