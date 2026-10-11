// Strictly the backend inventory and warehouse read contracts.
// Server-side transfer DTOs exclude internal FIFO costs (security fix #20).
// Do not add cost fields to warehouse-facing response contracts.
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
  model: string; variation: string; search?: string; includeZero: boolean; onlyAvailable: boolean;
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
  model?: string | null; variation?: string | null;
  // The backend intentionally excludes FIFO cost from warehouse-facing JSON.
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
  // This response deliberately contains no purchase/FIFO cost information.
}
export interface TransferFilters {
  warehouseId: string | null; variantId: string | null; page: number; size: number;
}

/** Complete warehouse-level PostgreSQL totals, not a paginated row sum. */
export interface WarehouseStockSummary {
  warehouseId: string;
  onHand: number; reserved: number; blocked: number; available: number;
  stockedVariants: number; lowStockVariants: number;
}

export interface WarehouseCreateInput { name: string; code: string; address: string | null; }
export interface WarehouseUpdateInput { name: string; address: string | null; }
