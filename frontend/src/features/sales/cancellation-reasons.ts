/** Keep UI values aligned with backend SaleCancellationReason enum. */
export const cancellationReasons = [
  ['CUSTOMER_CHANGED_MIND', 'Клиент передумал'],
  ['PRODUCT_NOT_SUITABLE', 'Товар не подошёл'],
  ['PRICE_NOT_ACCEPTABLE', 'Не устроила цена'],
  ['DUPLICATE_ORDER', 'Дублирующийся заказ'],
  ['DELIVERY_UNAVAILABLE', 'Не удалось организовать доставку'],
  ['OTHER', 'Другое'],
] as const;

export type CancellationReason = (typeof cancellationReasons)[number][0];
