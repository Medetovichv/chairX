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

/** The employee report's completedAt is the full-payment instant for regional
 * deliveries, and the operational completion instant for other fulfillments.
 * Always display it in the accounting timezone (Asia/Bishkek).
 */
export function operationalClosingTimestamp(value:string|null|undefined):string {
 if(!value)return 'Дата недоступна';
 const parsed=new Date(value);
 if(!Number.isFinite(parsed.getTime()))return 'Дата недоступна';
 return new Intl.DateTimeFormat('ru-RU',{
  timeZone:'Asia/Bishkek',day:'2-digit',month:'2-digit',year:'numeric',
  hour:'2-digit',minute:'2-digit',hour12:false,
 }).format(parsed);
}
