import { Link } from 'react-router';
import { SaleStatusBadge } from './SaleStatusBadge';
import { SaleDeliveryQuickActions } from './SaleDeliveryQuickActions';
import { money, saleDate, fulfillmentNames, integerSom } from '../validation';
import { displaySaleNumber } from '../../../shared/lib/format';
import type { SaleSummary } from '../types';

export function netSaleTotal(s:SaleSummary): string | null {
  const goods=integerSom(s.total);
  const delivery=integerSom(s.deliveryCost??0);
  const refunded=integerSom(s.refundedAmount??0);
  if(goods===null||delivery===null||refunded===null)return null;
  const net=BigInt(goods)+BigInt(delivery)-BigInt(refunded);
  // Inconsistent imported/refund records must not be displayed as a negative sale.
  return net<0n?null:net.toString();
}
export function SalesTable({rows}: {rows:SaleSummary[]}) {
  return <section aria-label="Таблица продаж" className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
    <div className="hidden overflow-x-auto xl:block">
      <table className="w-full text-left text-sm">
        <thead className="bg-slate-50">
          <tr>{['Заказ','Дата','Клиент / WhatsApp','Адрес доставки','Товары','Шт.','Сумма после возвратов','Доставка','Статус продажи','Статус оплаты','Статус доставки'].map(h=>
            <th key={h} scope="col" className="whitespace-nowrap px-3 py-3 font-semibold text-slate-600">{h}</th>)}</tr>
        </thead>
        <tbody className="divide-y divide-slate-100">{rows.map(s=>
          <tr key={s.id} className="entity-clickable-row group transition-colors">
            <td className="px-3 py-3 font-bold text-blue-700">
              <Link className="entity-stretched-link hover:underline" to={'/sales/'+encodeURIComponent(s.id)}
                state={{summary:s}} title={s.saleNumber}
                aria-label={'Открыть продажу '+s.saleNumber}>{displaySaleNumber(s.saleNumber)}</Link>
            </td>
            <td className="px-3 py-3">{saleDate(s.createdAt)}</td>
            <td className="px-3 py-3"><div>{s.customerName||'Имя не указано'}</div>
              {s.customerPhone&&<div className="whitespace-nowrap text-xs text-slate-600">{s.customerPhone}</div>}</td>
            <td className="max-w-52 break-words px-3 py-3 text-slate-700">{s.deliveryAddress||'—'}</td>
            <td className="max-w-64 break-words px-3 py-3">{s.products||'—'}</td>
            <td className="px-3 py-3">{s.quantity}</td>
            <td className="whitespace-nowrap px-3 py-3 font-semibold">{netSaleTotal(s)===null?money(s.total):money(netSaleTotal(s)!)}
              {integerSom(s.refundedAmount??0)!==null&&BigInt(integerSom(s.refundedAmount??0)!)>0n&&<div className="text-xs font-normal text-slate-600">Возвращено: −{money(s.refundedAmount!)}</div>}</td>
            <td className="whitespace-nowrap px-3 py-3">{money(s.deliveryCost??0)}</td>
            <td className="px-3 py-3"><SaleStatusBadge status={s.status}/>
              {(s.returnedQuantity??0)>0&&<p className="mt-1 inline-flex rounded-md bg-amber-100 px-2 py-0.5 text-xs font-semibold text-amber-900" aria-label="Возврат по продаже">
                {(s.returnedQuantity??0)>=s.quantity?'Возвращено полностью':'Частичный возврат'} · {s.returnedQuantity} шт.
              </p>}
              {s.status==='FULFILLED'&&s.paymentStatus!=='PAID'&&
                <p className="mt-1 text-xs font-semibold text-amber-800">Деньги не получены</p>}</td>
            <td className="px-3 py-3">{s.paymentStatus==='PAID'?'Оплачено':s.paymentStatus==='PARTIAL'?'Частично оплачено':s.paymentStatus==='UNPAID'?'Не оплачено':s.paymentStatus||'—'}</td>
            <td className="px-3 py-3">{s.deliveryStatus==='DELIVERED'?'Доставлено':s.deliveryStatus==='IN_TRANSIT'?'В пути':s.deliveryStatus==='READY'?'Готова к отправке':s.deliveryStatus==='FAILED'?'Не доставлено':s.deliveryStatus||'—'}
              {s.plannedDeliveryDate&&<p className="text-xs text-slate-500">{s.plannedDeliveryDate}</p>}
              {s.deliveryStatus&&<SaleDeliveryQuickActions saleId={s.id}/>}</td>
          </tr>)}</tbody>
      </table>
    </div>
    <div className="divide-y divide-slate-100 xl:hidden">
      {rows.map(s=><article key={s.id} className="entity-clickable-row group space-y-2 p-4 text-sm transition-colors">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <Link className="entity-stretched-link font-bold text-blue-700 hover:underline"
            to={'/sales/'+encodeURIComponent(s.id)} state={{summary:s}}
            title={s.saleNumber} aria-label={'Открыть продажу '+s.saleNumber}>
            {displaySaleNumber(s.saleNumber)}</Link>
          <SaleStatusBadge status={s.status}/>
        </div>
        {(s.returnedQuantity??0)>0&&<p className="inline-flex rounded-md bg-amber-100 px-2 py-1 text-xs font-semibold text-amber-900" aria-label="Возврат по продаже">
          {(s.returnedQuantity??0)>=s.quantity?'Возвращено полностью':'Частичный возврат'} · {s.returnedQuantity} шт.
        </p>}
        <p>{s.customerName||'Без имени'} · {s.customerPhone||'Номер не указан'} · {saleDate(s.createdAt)}</p>
        {s.deliveryAddress&&<p className="break-words font-medium text-slate-800">Адрес: {s.deliveryAddress}</p>}
        <p className="break-words text-slate-600">{s.products||'Товары не указаны'}</p>
        <p>Шт.: {s.quantity} · Итого после возвратов: <strong>{netSaleTotal(s)===null?'Сумма недоступна':money(netSaleTotal(s)!)}</strong></p>
        {integerSom(s.refundedAmount??0)!==null&&BigInt(integerSom(s.refundedAmount??0)!)>0n&&
          <p className="text-xs text-slate-600">Возвращено: −{money(s.refundedAmount!)}</p>}
        <p className="text-xs text-slate-600">Доставка: {money(s.deliveryCost??0)}</p>
        <p>Статус оплаты: {s.paymentStatus==='PAID'?'Оплачено':s.paymentStatus==='PARTIAL'?'Частично оплачено':s.paymentStatus==='UNPAID'?'Не оплачено':s.paymentStatus||'—'} · Статус доставки: {s.deliveryStatus==='DELIVERED'?'Доставлено':s.deliveryStatus==='IN_TRANSIT'?'В пути':s.deliveryStatus==='READY'?'Готова к отправке':s.deliveryStatus||'—'}</p>
        {s.deliveryStatus&&<SaleDeliveryQuickActions saleId={s.id}/>}
        <p>{s.fulfillmentType?fulfillmentNames[s.fulfillmentType]:'Способ получения не указан'}
          {s.plannedDeliveryDate?' · План: '+s.plannedDeliveryDate:''}</p>
      </article>)}
    </div>
  </section>;
}
