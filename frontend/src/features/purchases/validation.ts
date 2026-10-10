import type { PurchaseInput, PurchaseInputLine, PurchaseStatus, SupplierInput } from './types';
export const statuses: readonly PurchaseStatus[] =
  ['DRAFT', 'CONFIRMED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CANCELLED'];
export const statusNames: Record<PurchaseStatus, string> = {
  DRAFT: 'Черновик', CONFIRMED: 'Подтверждена',
  PARTIALLY_RECEIVED: 'Частично получена', RECEIVED: 'Получена', CANCELLED: 'Отменена',
};
export function formatDate(value: string | null) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : new Intl.DateTimeFormat('ru-RU',
    { timeZone: 'Asia/Bishkek', day: '2-digit', month: '2-digit', year: 'numeric' }).format(date);
}
export function formatMoney(value: string | number | null): string {
  if (value === null || value === undefined) return 'Не указано';
  const str = String(value);
  if (!/^\d+(?:\.\d{1,2})?$/.test(str)) return '—';
  const [int, frac] = str.split('.');
  const group = (int ?? '').replace(/\B(?=(\d{3})+(?!\d))/g, '\u00a0');
  return group + (frac ? ',' + frac : '') + ' сом';
}
export function checkMoney(value: string): string | null {
  if (!/^(?:0|[0-9]+)(?:[.,][0-9]{1,2})?$/.test(value.trim())) return 'Сумма должна быть неотрицательной, с точностью до двух знаков.';
  if ((value.trim().split(/[,.]/)[0]?.length ?? 0) > 17) return 'Допустимо максимум 17 цифр до запятой.';
  return null;
}
export function integerQuantity(value: string) {
  if (!/^[1-9]\d*$/.test(value.trim())) return null;
  const n = Number(value.trim());
  return Number.isSafeInteger(n) ? n : null;
}
export function validatePurchase(input: PurchaseInput): string | null {
  if (!input.supplierId) return 'Выберите поставщика.';
  if (!input.items.length) return 'Добавьте хотя бы одну позицию.';
  if (input.items.length > 1000) return 'Максимум 1000 позиций.';
  const used = new Set<string>();
  for (const item of input.items) {
    if (!item.productVariantId) return 'Выберите вариацию для каждой позиции.';
    if (used.has(item.productVariantId)) return 'Одна вариация не может повторяться в закупке.';
    used.add(item.productVariantId);
    if (!Number.isSafeInteger(item.orderedQuantity) || item.orderedQuantity < 1) return 'Количество должно быть положительным целым.';
    const err = checkMoney(item.purchaseUnitCost);
    if (err) return 'Ошибка закупочной цены: ' + err;
  }
  if (input.cargoCost !== null) {
    const err = checkMoney(input.cargoCost);
    if (err) return 'Ошибка карго: ' + err;
  }
  if ((input.comment?.length ?? 0) > 4000) return 'Комментарий: максимум 4000 символов.';
  return null;
}
export function purchaseInput(supplierId: string, rows: PurchaseInputLine[], cargo: string, comment: string): PurchaseInput {
  return { supplierId, items: rows.map((r) => ({
    productVariantId: r.productVariantId, orderedQuantity: r.orderedQuantity,
    purchaseUnitCost: r.purchaseUnitCost.replace(',', '.'),
  })), cargoCost: cargo.trim() ? cargo.trim().replace(',', '.') : null, comment: comment.trim() || null };
}
export function validateSupplier(input: SupplierInput): string | null {
  if (!input.name.trim()) return 'Укажите название поставщика.';
  if (input.name.trim().length > 200) return 'Название: максимум 200 символов.';
  if ((input.contactInformation?.trim().length ?? 0) > 1000) return 'Контакт: максимум 1000 символов.';
  if ((input.comment?.trim().length ?? 0) > 4000) return 'Комментарий: максимум 4000 символов.';
  return null;
}
