import { Link } from 'react-router';
import { ArrowUpRight, CalendarDays, MapPin, Phone, Truck } from 'lucide-react';
import { DeliveryStatusBadge } from './DeliveryStatusBadge';
import { dateLabel, label } from '../validation';
import { displaySaleNumber } from '../../../shared/lib/format';
import type { DeliverySummary } from '../types';

const detail = (id: string) => '/deliveries/' + encodeURIComponent(id);
const typeLabel = (type: DeliverySummary['fulfillmentType']) =>
  type === 'CITY_DELIVERY' ? 'Городская' : type === 'REGION_DELIVERY' ? 'Региональная' : 'Тип не указан';

export function DeliveriesTable({ rows }: { rows: DeliverySummary[] }) {
  return <section aria-label="Список доставок" className="overflow-hidden rounded-2xl border border-[#dfe7eb] bg-white">
    <div className="hidden overflow-x-auto xl:block">
      <table className="w-full text-left text-sm">
        <thead className="bg-[#f5f9f7] text-[#516977]">
          <tr>{['Заказ и дата', 'Получатель', 'Направление', 'План доставки', 'Статус'].map(header =>
            <th key={header} scope="col" className="whitespace-nowrap px-5 py-4 text-xs font-bold">{header}</th>)}</tr>
        </thead>
        <tbody className="divide-y divide-[#eaf0f0]">{rows.map(d =>
          <tr key={d.id} className="entity-clickable-row group transition-colors hover:bg-[#f5faf8]">
            <td className="px-5 py-4 align-top">
              <Link to={detail(d.id)} state={{ summary: d }} title={d.saleNumber || undefined}
                className="entity-stretched-link font-bold text-[#236e66] hover:underline focus-visible:outline-2 focus-visible:outline-[#267f79]"
                aria-label={'Открыть доставку ' + (d.saleNumber || '')}>
                {d.saleNumber ? displaySaleNumber(d.saleNumber) : 'Доставка'}
              </Link>
              <p className="mt-1 text-xs text-[#516977]">Создана: {dateLabel(d.createdAt)}</p>
            </td>
            <td className="max-w-60 px-5 py-4 align-top">
              <p className="break-words font-semibold text-[#263e49]">{label(d.recipientName)}</p>
              <p className="mt-1 whitespace-nowrap text-xs text-[#516977]">{label(d.recipientPhone)}</p>
            </td>
            <td className="max-w-88 px-5 py-4 align-top">
              <p className="font-semibold text-[#263e49]">{label(d.cityRegion)}</p>
              <p className="mt-1 break-words text-xs text-[#516977]">{label(d.address)}</p>
              <p className="mt-2 inline-flex items-center gap-1 text-xs text-[#516977]">
                <Truck className="size-3.5" aria-hidden="true"/>{typeLabel(d.fulfillmentType)}
              </p>
            </td>
            <td className="whitespace-nowrap px-5 py-4 align-top font-medium tabular-nums text-[#263e49]">
              {dateLabel(d.plannedDeliveryDate)}
              {d.deliveredAt && <p className="mt-1 text-xs font-normal text-[#516977]">Факт: {dateLabel(d.deliveredAt)}</p>}
            </td>
            <td className="px-5 py-4 align-top"><DeliveryStatusBadge status={d.status}/></td>
          </tr>)}</tbody>
      </table>
    </div>
    <div className="divide-y divide-[#eaf0f0] xl:hidden">{rows.map(d =>
      <article key={d.id} className="entity-clickable-row group space-y-4 p-4 transition-colors hover:bg-[#f5faf8] sm:p-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <Link to={detail(d.id)} state={{ summary: d }} title={d.saleNumber || undefined}
              className="entity-stretched-link inline-flex items-center gap-1.5 font-bold text-[#236e66] hover:underline"
              aria-label={'Открыть доставку ' + (d.saleNumber || '')}>
              {d.saleNumber ? displaySaleNumber(d.saleNumber) : 'Доставка'}
              <ArrowUpRight className="size-4 shrink-0" aria-hidden="true"/>
            </Link>
            <p className="mt-1 text-xs text-[#516977]">Создана: {dateLabel(d.createdAt)}</p>
          </div>
          <DeliveryStatusBadge status={d.status}/>
        </div>
        <div>
          <p className="break-words font-semibold text-[#263e49]">{label(d.recipientName)}</p>
          {d.recipientPhone && <p className="mt-2 flex items-center gap-2 text-sm text-[#516977]">
            <Phone className="size-4 shrink-0" aria-hidden="true"/>{d.recipientPhone}
          </p>}
        </div>
        <div className="space-y-2 text-sm text-[#405b65]">
          <p className="flex items-start gap-2">
            <MapPin className="mt-0.5 size-4 shrink-0" aria-hidden="true"/>
            <span className="min-w-0 break-words"><strong className="font-semibold">{label(d.cityRegion)}</strong>
              {' · ' + label(d.address)}</span>
          </p>
          <p className="flex items-center gap-2">
            <Truck className="size-4 shrink-0" aria-hidden="true"/>{typeLabel(d.fulfillmentType)}
          </p>
        </div>
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-[#f2f7f6] px-4 py-3 text-sm">
          <span className="flex items-center gap-2 font-medium text-[#285f58]">
            <CalendarDays className="size-4 shrink-0" aria-hidden="true"/>План: {dateLabel(d.plannedDeliveryDate)}
          </span>
          {d.deliveredAt && <span className="text-xs text-[#516977]">Доставлена: {dateLabel(d.deliveredAt)}</span>}
        </div>
      </article>)}</div>
  </section>;
}
