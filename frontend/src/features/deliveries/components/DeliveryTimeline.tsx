import { dateLabel } from '../validation';
import type { DeliveryResponse } from '../types';
export function DeliveryTimeline({delivery:d}:{delivery:DeliveryResponse}){
  const stages=[
    {label:'Создана',at:d.createdAt,by:d.createdBy},
    {label:'Отправлена',at:d.dispatchedAt,by:d.dispatchedBy},
    {label:'Вручена',at:d.deliveredAt,by:d.deliveredBy},
    {label:'Не доставлена',at:d.failedAt,by:d.failedBy},
    {label:'Отменена',at:d.cancelledAt,by:d.cancelledBy},
    {label:'Возвращена на склад',at:d.returnedToWarehouseAt,by:d.returnedToWarehouseBy},
  ].filter(x=>x.at!==null);
  return <section aria-label="История этапов доставки" className="rounded-2xl border border-slate-200 bg-white p-4">
    <h3 className="mb-3 text-lg font-bold">История этапов</h3>
    <ol className="space-y-2">{stages.map(x=><li key={x.label}
      className="border-l-2 border-indigo-200 py-1 pl-3 text-sm">
      <p className="font-semibold">{x.label}</p>
      <p className="text-slate-600">{dateLabel(x.at)} · {x.by||'Сотрудник не указан'}</p>
    </li>)}</ol>
    <p className="mt-3 text-xs text-slate-500">
      История собрана из фактических timestamp и actor-полей. Отдельного audit API нет.
    </p>
  </section>;
}
