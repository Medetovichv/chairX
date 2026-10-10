import { Link } from 'react-router';
import { SaleStatusBadge } from './SaleStatusBadge';
import { money, saleDate, fulfillmentNames } from '../validation';
import type { SaleSummary } from '../types';

export function SalesTable({rows}: {rows:SaleSummary[]}) {
  return <section aria-label="Таблица продаж" className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
    <div className="hidden overflow-x-auto xl:block">
      <table className="w-full text-left text-sm">
        <thead className="bg-slate-50">
          <tr>{['Заказ','Дата','Клиент','Товары','Шт.','Сумма','Статус','Оплата','Доставка','Действие'].map(h=>
            <th key={h} scope="col" className="whitespace-nowrap px-3 py-3 font-semibold text-slate-600">{h}</th>)}</tr>
        </thead>
        <tbody className="divide-y divide-slate-100">{rows.map(s=><tr key={s.id}>
          <td className="px-3 py-3 font-semibold">{s.saleNumber}</td>
          <td className="px-3 py-3">{saleDate(s.createdAt)}</td>
          <td className="px-3 py-3">{s.customerName||s.customerId||'Без клиента'}</td>
          <td className="max-w-64 break-words px-3 py-3">{s.products||'—'}</td>
          <td className="px-3 py-3">{s.quantity}</td>
          <td className="whitespace-nowrap px-3 py-3 font-semibold">{money(s.total)}</td>
          <td className="px-3 py-3"><SaleStatusBadge status={s.status}/></td>
          <td className="px-3 py-3">{s.paymentStatus||'—'}</td>
          <td className="px-3 py-3">{s.deliveryStatus||'—'}
            {s.plannedDeliveryDate&&<p className="text-xs text-slate-500">{s.plannedDeliveryDate}</p>}</td>
          <td className="px-3 py-3"><Link to={'/sales/'+encodeURIComponent(s.id)} state={{summary:s}}
            className="inline-flex min-h-10 items-center font-semibold text-indigo-700 hover:underline">Открыть</Link></td>
        </tr>)}</tbody>
      </table>
    </div>
    <div className="divide-y divide-slate-100 xl:hidden">
      {rows.map(s=><article key={s.id} className="space-y-2 p-4 text-sm">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <Link className="font-bold text-indigo-700" to={'/sales/'+encodeURIComponent(s.id)} state={{summary:s}}>
            {s.saleNumber}</Link><SaleStatusBadge status={s.status}/>
        </div>
        <p>{s.customerName||s.customerId||'Без клиента'} · {saleDate(s.createdAt)}</p>
        <p className="break-words text-slate-600">{s.products||'Товары не указаны'}</p>
        <p>Шт.: {s.quantity} · <strong>{money(s.total)}</strong></p>
        <p>Оплата: {s.paymentStatus||'—'} · Доставка: {s.deliveryStatus||'—'}</p>
        <p>{s.fulfillmentType?fulfillmentNames[s.fulfillmentType]:'Способ получения не указан'}
          {s.plannedDeliveryDate?' · План: '+s.plannedDeliveryDate:''}</p>
      </article>)}
    </div>
  </section>;
}
