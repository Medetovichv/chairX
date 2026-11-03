/** Operational state in the daily report, not a claim that a customer received the item. */
export function operationalDeliveryLabel(
  status: string | null | undefined,
  fulfillment: string | null | undefined,
): string {
  if (fulfillment === 'SELF_PICKUP') return 'Выдано';
  switch (status) {
    case 'READY': return 'Готовится к отправке';
    case 'IN_TRANSIT': return 'В пути';
    case 'DELIVERED': return 'Доставлено';
    case 'FAILED': return 'Доставка не выполнена';
    case 'CANCELLED': return 'Доставка отменена';
    default: return 'Статус доставки не указан';
  }
}
