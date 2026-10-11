import { Link } from 'react-router';
import { DeliveryStatusBadge } from './DeliveryStatusBadge';
import { dateLabel, label } from '../validation';
import { displaySaleNumber } from '../../../shared/lib/format';
import type { DeliverySummary } from '../types';

export function DeliveriesTable({rows}:{rows:DeliverySummary[]}) {
  const detail=(id:string)=>'/deliveries/'+encodeURIComponent(id);
  return <section aria-label="Список доставок" className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
    <div className="hidden overflow-x-auto xl:block">
      <table className="w-full text-left text-sm">
        <thead className="bg-slate-50"><tr>{[
          'Заказ','Получатель','Телефон','Город / регион','Адрес',
          'План','Статус','Тип','Создана',
        ].map(h=><th scope="col" key={h} className="px-3 py-3 font-semibold">{h}</th>)}</tr></thead>
        <tbody className="divide-y divide-slate-100">{rows.map(d=>
          <tr key={d.id} className="entity-clickable-row group transition-colors">
            <td className="px-3 py-3 font-bold text-blue-700">
              <Link to={detail(d.id)} state={{summary:d}} title={d.saleNumber||undefined}
                className="entity-stretched-link hover:underline" aria-label={'Открыть доставку '+(d.saleNumber||'')}>
                {d.saleNumber?displaySaleNumber(d.saleNumber):'Доставка'}</Link>
            </td>
            <td className="px-3 py-3">{label(d.recipientName)}</td>
            <td className="whitespace-nowrap px-3 py-3">{label(d.recipientPhone)}</td>
            <td className="px-3 py-3">{label(d.cityRegion)}</td>
            <td className="max-w-64 break-words px-3 py-3">{label(d.address)}</td>
            <td className="px-3 py-3">{dateLabel(d.plannedDeliveryDate)}</td>
            <td className="px-3 py-3"><DeliveryStatusBadge status={d.status}/></td>
            <td className="px-3 py-3">{d.fulfillmentType==='CITY_DELIVERY'?'Городская':
              d.fulfillmentType==='REGION_DELIVERY'?'Региональная':'—'}</td>
            <td className="px-3 py-3">{dateLabel(d.createdAt)}</td>
          </tr>)}</tbody>
      </table>
    </div>
    <div className="divide-y divide-slate-100 xl:hidden">{rows.map(d=>
      <article key={d.id} className="entity-clickable-row group space-y-2 p-4 text-sm transition-colors">
        <div className="flex items-center justify-between gap-2">
          <Link to={detail(d.id)} state={{summary:d}} title={d.saleNumber||undefined}
            className="entity-stretched-link font-bold text-blue-700"
            aria-label={'Открыть доставку '+(d.saleNumber||'')}>
            {d.saleNumber?displaySaleNumber(d.saleNumber):'Доставка'}</Link>
          <DeliveryStatusBadge status={d.status}/>
        </div>
        <p className="font-semibold">{label(d.recipientName)} · {label(d.recipientPhone)}</p>
        <p className="break-words">{label(d.cityRegion)} · {label(d.address)}</p>
        <p>План: {dateLabel(d.plannedDeliveryDate)} · Создана: {dateLabel(d.createdAt)}</p>
        <p>{d.fulfillmentType==='REGION_DELIVERY'?'Региональная доставка':
          d.fulfillmentType==='CITY_DELIVERY'?'Городская доставка':'Тип не указан'}</p>
      </article>)}</div>
  </section>;
}
