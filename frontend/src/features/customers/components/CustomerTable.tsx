import { Link } from 'react-router';
import { ArrowUpRight, MapPin, MessageCircle, Phone, ShoppingBag } from 'lucide-react';
import type { Customer, CustomerOverviewLine } from '../types';
import { customerDate, customerTitle } from '../validation';
import { displaySaleNumber } from '../../../shared/lib/format';

type Line = Customer | CustomerOverviewLine;
function isOverview(line: Line): line is CustomerOverviewLine {
  return 'orderCount' in line;
}
const detail = (id: string) => '/customers/' + encodeURIComponent(id);

export function CustomerTable({ items, withSales }: { items: Line[]; withSales: boolean }) {
  return <section aria-label="Список клиентов" className="overflow-hidden rounded-2xl border border-[#dfe7eb] bg-white">
    <div className="hidden overflow-x-auto lg:block">
      <table className="w-full text-left text-sm">
        <thead className="bg-[#f5f9f7] text-[#516977]">
          <tr>{['Клиент', 'Контакты', 'Город / регион',
            ...(withSales ? ['Заказы и активность'] : [])].map(title =>
            <th scope="col" key={title} className="px-5 py-4 text-xs font-bold">{title}</th>)}</tr>
        </thead>
        <tbody className="divide-y divide-[#eaf0f0]">
          {items.map(line => <tr key={line.id} className="entity-clickable-row group transition-colors hover:bg-[#f5faf8]">
            <td className="max-w-60 px-5 py-4 align-top">
              <Link className="entity-stretched-link break-words font-bold text-[#236e66] hover:underline focus-visible:outline-2 focus-visible:outline-[#267f79]"
                to={detail(line.id)}>{customerTitle(line)}</Link>
            </td>
            <td className="max-w-60 px-5 py-4 align-top text-[#405b65]">
              <p className="break-all">{line.phone || 'Телефон не указан'}</p>
              {line.whatsappPhone && <p className="mt-1 break-all text-xs text-[#516977]">WhatsApp: {line.whatsappPhone}</p>}
            </td>
            <td className="max-w-56 break-words px-5 py-4 align-top text-[#405b65]">{line.cityRegion || '—'}</td>
            {withSales && <td className="px-5 py-4 align-top">
              {isOverview(line) ? <>
                <p className="font-semibold tabular-nums text-[#263e49]">{line.orderCount} заказов</p>
                <p className="mt-1 text-xs text-[#516977]">Последний: {line.lastSaleNumber ? displaySaleNumber(line.lastSaleNumber) : '—'}</p>
                <p className="mt-1 text-xs text-[#516977]">{customerDate(line.lastOrderAt)}</p>
              </> : '—'}
            </td>}
          </tr>)}
        </tbody>
      </table>
    </div>
    <div className="divide-y divide-[#eaf0f0] lg:hidden">
      {items.map(line => <article key={line.id}
        className="entity-clickable-row group space-y-4 p-4 transition-colors hover:bg-[#f5faf8] sm:p-5">
        <div>
          <Link className="entity-stretched-link inline-flex items-center gap-1.5 break-words text-base font-bold text-[#236e66] hover:underline"
            to={detail(line.id)}>{customerTitle(line)}<ArrowUpRight className="size-4 shrink-0" aria-hidden="true"/></Link>
          {line.cityRegion && <p className="mt-2 flex items-center gap-2 text-sm text-[#516977]">
            <MapPin className="size-4 shrink-0" aria-hidden="true"/>{line.cityRegion}
          </p>}
        </div>
        <div className="grid gap-2 text-sm text-[#405b65] sm:grid-cols-2">
          <p className="flex items-start gap-2"><Phone className="mt-0.5 size-4 shrink-0" aria-hidden="true"/>
            <span className="min-w-0 break-all">{line.phone || 'Телефон не указан'}</span></p>
          {line.whatsappPhone && <p className="flex items-start gap-2">
            <MessageCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true"/>
            <span className="min-w-0 break-all">WhatsApp: {line.whatsappPhone}</span></p>}
        </div>
        {withSales && isOverview(line) && <div className="flex flex-wrap items-center gap-2 rounded-xl bg-[#f2f7f6] px-4 py-3 text-sm text-[#285f58]">
          <ShoppingBag className="size-4 shrink-0" aria-hidden="true"/>
          <span className="font-semibold tabular-nums">{line.orderCount} заказов</span>
          <span className="text-xs text-[#516977]">Последний: {line.lastSaleNumber ? displaySaleNumber(line.lastSaleNumber) : '—'} · {customerDate(line.lastOrderAt)}</span>
        </div>}
      </article>)}
    </div>
  </section>;
}
