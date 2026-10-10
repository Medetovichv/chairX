import type { StockLine, Warehouse, WarehouseStock } from './types';

export function warehouseLabel(id: string, warehouses: readonly Warehouse[]): string {
  const known = warehouses.find((w) => w.id === id);
  return known ? known.name : id;
}
export function warehouseStock(line: StockLine, id: string): WarehouseStock | null {
  return line.warehouses.find((s) => s.warehouseId === id) ?? null;
}
export function positiveQuantity(value: string, available: number): string | null {
  if (!/^[1-9]\d*$/.test(value)) return 'Укажите целое положительное количество.';
  const quantity = Number(value);
  if (!Number.isSafeInteger(quantity)) return 'Количество слишком велико.';
  if (quantity > available) return 'Количество превышает доступный остаток на складе отправления.';
  return null;
}
export const movementNames: Readonly<Record<string, string>> = {
  PURCHASE_IN: 'Поступление', SALE_OUT: 'Продажа',
  RETURN_IN: 'Возврат', TRANSFER_OUT: 'Перемещение со склада',
  TRANSFER_IN: 'Перемещение на склад', WRITE_OFF: 'Списание',
  ADJUSTMENT_IN: 'Корректировка (+)', ADJUSTMENT_OUT: 'Корректировка (−)',
};
export function displayTimestamp(timestamp: string): string {
  const time = new Date(timestamp);
  return Number.isNaN(time.getTime()) ? '—' : new Intl.DateTimeFormat('ru-RU', {
    dateStyle: 'short', timeStyle: 'short', timeZone: 'Asia/Bishkek',
  }).format(time);
}
export function shortId(id: string): string {
  return id.length > 8 ? id.slice(0, 8) + '…' : id;
}
export function pages(total: number, size: number): number {
  return Math.ceil(total / size);
}
