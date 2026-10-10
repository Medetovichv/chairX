// Strictly the backend inventory and warehouse read contracts.
// The transfer list backend additionally sends totalCost. Never display it here;
// SERVER-SIDE disclosure to INVENTORY_READ remains a security issue (see docs).
export interface Warehouse {
  id: string; name: string; code: string; address: string | null;
  active: boolean; createdAt: string; updatedAt: string;
}
export interface WarehousePage {
  items: Warehouse[]; page: number; size: number;
  totalElements: number; totalPages: number;
}
export interface WarehouseStock {
  warehouseId: string; warehouseCode: string;
  onHand: number; reserved: number; blocked: number; available: number;
}
export interface StockLine {
  productVariantId: string; model: string; variation: string;
  onHand: number; reserved: number; blocked: number; available: number;
  warehouses: WarehouseStock[];
}
export interface InventoryOverviewPage {
  items: StockLine[]; page: number; size: number; total: number;
}
export interface InventoryFilters {
  warehouseId: string | null;
  model: string; variation: string; includeZero: boolean; onlyAvailable: boolean;
  page: number; size: number;
}
export interface InventoryBalance {
  warehouseId: string; productVariantId: string;
  onHand: number; reserved: number; blocked: number; available: number;
}
export type StockMovementType =
  | 'PURCHASE_IN' | 'SALE_OUT' | 'RETURN_IN' | 'TRANSFER_OUT'
  | 'TRANSFER_IN' | 'WRITE_OFF' | 'ADJUSTMENT_IN' | 'ADJUSTMENT_OUT';
export interface StockMovement {
  id: string; operationId: string; warehouseId: string; productVariantId: string;
  type: StockMovementType; quantity: number; sourceType: string;
  sourceId: string | null; actor: string | null; occurredAt: string;
}
export interface MovementPage {
  items: StockMovement[]; page: number; size: number; totalElements: number;
}
export interface InventoryTransfer {
  id: string; sourceWarehouseId: string; destinationWarehouseId: string;
  variantId: string; quantity: number; actor: string | null;
  createdAt: string; outMovementId: string; inMovementId: string;
  // The actual backend also returns totalCost, but it is intentionally
  // omitted from UI types. Browser network access STILL exposes it.
}
export interface TransferPage {
  items: InventoryTransfer[]; page: number; size: number;
  totalElements: number; totalPages: number;
}
export interface TransferInput {
  transferId: string; sourceWarehouseId: string;
  destinationWarehouseId: string; variantId: string; quantity: number;
}
export interface TransferResult {
  transferId: string; outMovementId: string; inMovementId: string;
  quantity: number;
  // totalCost intentionally omitted; backend currently sends this field.
}
export interface TransferFilters {
  warehouseId: string | null; variantId: string | null; page: number; size: number;
}
