/** Shared presentation labels; backend movement codes remain unchanged. */
const labels: Record<string,string> = {
  SALE_PAYMENT:'Оплата продажи',
  PURCHASE_PAYMENT:'Оплата закупки',
  EXPENSE:'Расход',
  CUSTOMER_REFUND:'Возврат клиенту',
  PAYMENT_REVERSAL:'Отмена платежа',
  TRANSFER:'Перевод между счетами',
  OPENING_BALANCE:'Начальный остаток',
};
export function financeMovementLabel(type: string): string {
  return labels[type] ?? type.replaceAll('_',' ');
}
