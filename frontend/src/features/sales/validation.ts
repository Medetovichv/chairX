import type { DraftInput, Money, SaleStatus, FulfillmentType } from './types';

export const saleStatuses: SaleStatus[] = ['DRAFT', 'CONFIRMED', 'FULFILLED', 'CANCELLED'];
export const saleStatusNames: Record<SaleStatus,string> = {
  DRAFT: 'Черновик', CONFIRMED: 'Подтверждён', FULFILLED: 'Выполнен', CANCELLED: 'Отменён',
};
export const fulfillmentNames: Record<FulfillmentType,string> = {
  SELF_PICKUP:'Самовывоз', CITY_DELIVERY:'Доставка по городу', REGION_DELIVERY:'Доставка в регион',
};
export const uuidValid = (value: string) =>
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
export const salePriceValid = (price: string) => /^(0|[1-9][0-9]{0,18})$/.test(price.trim());
export const countValid = (value: string) => /^[1-9][0-9]*$/.test(value.trim()) &&
  Number.isSafeInteger(Number(value.trim()));
export function money(value: Money | null | undefined): string {
  if (value === null || value === undefined) return '—';
  const raw = String(value);
  if (!/^\d+(\.\d{1,2})?$/.test(raw)) return '—';
  const [int, decimals] = raw.split('.');
  const whole = (int ?? '').replace(/\B(?=(\d{3})+(?!\d))/g,'\u00a0');
  return whole + (decimals ? ',' + decimals : '') + ' сом';
}
export function saleDate(value: string | null | undefined) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' :
    new Intl.DateTimeFormat('ru-RU', {timeZone:'Asia/Bishkek',day:'2-digit',month:'2-digit',year:'numeric'}).format(date);
}
export function checkDraft(data: DraftInput): string | null {
  if (data.customerId !== null && !uuidValid(data.customerId)) return 'Некорректный UUID клиента.';
  if (data.items.length > 1000) return 'Слишком много товарных позиций.';
  const used = new Set<string>();
  for (const item of data.items) {
    if (!uuidValid(item.productVariantId) || !uuidValid(item.warehouseId)) return 'Укажите настоящую вариацию и склад для каждой позиции.';
    if (!Number.isSafeInteger(item.quantity) || item.quantity <= 0) return 'Количество должно быть положительным целым.';
    if (!salePriceValid(item.unitSalePrice)) return 'Цена продажи должна быть целым числом сомов от 0 до 19 цифр.';
    const key = item.warehouseId + ':' + item.productVariantId;
    if (used.has(key)) return 'Одинаковую вариацию с одного склада нужно объединить в одну строку.';
    used.add(key);
  }
  if ((data.comment?.length ?? 0) > 2000) return 'Комментарий: не более 2000 символов.';
  return null;
}
