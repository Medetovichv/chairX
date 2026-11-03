import { Link } from 'react-router';
import { ArrowUpRight, CalendarDays, MapPin, Package, Phone } from 'lucide-react';
import { SaleStatusBadge } from './SaleStatusBadge';
import { SaleDeliveryQuickActions } from './SaleDeliveryQuickActions';
import { money, saleDate, fulfillmentNames } from '../validation';
import { displaySaleNumber } from '../../../shared/lib/format';
import type { SaleSummary } from '../types';

function paymentLabel(value: string | null): string {
  return value === 'PAID' ? 'Оплачено' : value === 'PARTIAL' ? 'Частично оплачено' :
    value === 'UNPAID' ? 'Не оплачено' : value || '—';
}
function deliveryLabel(value: string | null): string {
  return value === 'DELIVERED' ? 'Доставлено' : value === 'IN_TRANSIT' ? 'В пути' :
    value === 'READY' ? 'Готова к отправке' : value === 'FAILED' ? 'Не доставлено' :
    value || '—';
}
function ReturnNote({ sale }: { sale: SaleSummary }) {
  if (!(sale.returnedQuantity ?? 0)) return null;
  return <p className="mt-2 inline-flex rounded-lg bg-[#fff0db] px-2.5 py-1 text-xs font-semibold text-[#865c24]"
    aria-label="Возврат по продаже">
    {(sale.returnedQuantity ?? 0) >= sale.quantity ? 'Возвращено полностью' : 'Частичный возврат'}
    {' · ' + sale.returnedQuantity + ' шт.'}
  </p>;
}

export function SalesTable({ rows }: { rows: SaleSummary[] }) {
  return <section aria-label="Таблица продаж"
    className="overflow-hidden rounded-[24px] border border-[#e0e8eb] bg-white">
    <div className="hidden overflow-x-auto xl:block">
      <table className="w-full text-left text-sm">
        <thead className="bg-[#f5f9f7] text-[#516977]">
          <tr>{['Заказ','Клиент / WhatsApp','Товары','Сумма','Статус продажи','Оплата','Доставка'].map(header =>
            <th key={header} scope="col" className="whitespace-nowrap px-4 py-4 text-xs font-bold">{header}</th>)}</tr>
        </thead>
        <tbody className="divide-y divide-[#eaf0f0]">{rows.map(sale =>
          <tr key={sale.id} className="entity-clickable-row group transition-colors hover:bg-[#f5faf8]">
            <td className="px-4 py-4">
              <Link className="entity-stretched-link font-bold text-[#236e66] hover:underline"
                to={'/sales/' + encodeURIComponent(sale.id)} state={{ summary:sale }}
                title={sale.saleNumber} aria-label={'Открыть продажу ' + sale.saleNumber}>
                {displaySaleNumber(sale.saleNumber)}
              </Link>
              <p className="mt-1 whitespace-nowrap text-xs text-[#516977]">{saleDate(sale.createdAt)}</p>
            </td>
            <td className="min-w-35 max-w-52 px-4 py-4">
              <p className="break-words font-semibold text-[#263e49]">{sale.customerName || 'Имя не указано'}</p>
              {sale.customerPhone && <p className="mt-1 whitespace-nowrap text-xs text-[#516977]">{sale.customerPhone}</p>}
              {sale.deliveryAddress && <p className="mt-1 max-w-48 truncate text-xs text-[#516977]" title={sale.deliveryAddress}>{sale.deliveryAddress}</p>}
            </td>
            <td className="min-w-35 max-w-57 px-4 py-4">
              <p className="line-clamp-2 break-words text-[#263e49]" title={sale.products || undefined}>{sale.products || '—'}</p>
              <p className="mt-1 text-xs text-[#516977]">{sale.quantity} шт.</p>
            </td>
            <td className="whitespace-nowrap px-4 py-4 font-bold tabular-nums text-[#182d38]">{money(sale.total)}</td>
            <td className="px-4 py-4">
              <SaleStatusBadge status={sale.status} />
              <ReturnNote sale={sale} />
              {sale.status === 'FULFILLED' && sale.paymentStatus !== 'PAID' &&
                <p className="mt-2 text-xs font-bold text-[#926120]">Деньги не получены</p>}
            </td>
            <td className="px-4 py-4 text-sm text-[#405b65]">{paymentLabel(sale.paymentStatus)}</td>
            <td className="px-4 py-4">
              <p className="text-sm text-[#405b65]">{deliveryLabel(sale.deliveryStatus)}</p>
              {sale.plannedDeliveryDate && <p className="mt-1 text-xs text-[#516977]">{sale.plannedDeliveryDate}</p>}
              {sale.deliveryStatus && <SaleDeliveryQuickActions saleId={sale.id} />}
            </td>
          </tr>)}</tbody>
      </table>
    </div>
    <div className="divide-y divide-[#eaf0f0] xl:hidden">{rows.map(sale =>
      <article key={sale.id} className="entity-clickable-row group space-y-4 p-4 transition-colors hover:bg-[#f5faf8] sm:p-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <Link className="entity-stretched-link inline-flex items-center gap-1.5 break-words text-base font-bold text-[#236e66] hover:underline"
              to={'/sales/' + encodeURIComponent(sale.id)} state={{ summary: sale }}
              title={sale.saleNumber} aria-label={'Открыть продажу ' + sale.saleNumber}>
              {displaySaleNumber(sale.saleNumber)}
              <ArrowUpRight className="size-4 shrink-0" aria-hidden="true" />
            </Link>
            <p className="mt-1 flex items-center gap-1.5 text-xs text-[#516977]">
              <CalendarDays className="size-3.5" aria-hidden="true" />{saleDate(sale.createdAt)}
            </p>
          </div>
          <SaleStatusBadge status={sale.status} />
        </div>
        <div className="space-y-2">
          <div className="font-semibold text-[#263e49]">{sale.customerName || 'Без имени'}</div>
          {sale.customerPhone && <p className="flex items-center gap-2 text-xs text-[#516977]"><Phone className="size-4 shrink-0" aria-hidden="true"/>{sale.customerPhone}</p>}
          {sale.deliveryAddress && <p className="flex items-start gap-2 text-xs text-[#516977]"><MapPin className="mt-0.5 size-4 shrink-0" aria-hidden="true"/><span className="min-w-0 break-words">{sale.deliveryAddress}</span></p>}
          <p className="flex items-start gap-2 text-sm text-[#405b65]"><Package className="mt-0.5 size-4 shrink-0" aria-hidden="true"/><span className="min-w-0 break-words">{sale.products || 'Товары не указаны'} · {sale.quantity} шт.</span></p>
        </div>
        <ReturnNote sale={sale} />
        <div className="flex flex-wrap items-center justify-between gap-2 rounded-xl bg-[#f2f7f6] px-4 py-3">
          <div>
            <p className="text-[11px] text-[#516977]">Сумма продажи</p>
            <strong className="text-lg font-bold tabular-nums text-[#182d38]">{money(sale.total)}</strong>
          </div>
          <span className="text-xs font-semibold text-[#35685c]">{paymentLabel(sale.paymentStatus)}</span>
        </div>
        <p className="text-xs leading-relaxed text-[#516977]">
          Доставка: {deliveryLabel(sale.deliveryStatus)}
          {' · ' + (sale.fulfillmentType ? fulfillmentNames[sale.fulfillmentType] : 'Способ получения не указан')}
          {sale.plannedDeliveryDate ? ' · План: ' + sale.plannedDeliveryDate : ''}
        </p>
        {sale.deliveryStatus && <SaleDeliveryQuickActions saleId={sale.id} />}
      </article>)}</div>
  </section>;
}
