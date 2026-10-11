import { Link } from 'react-router';
import type { Customer, CustomerOverviewLine } from '../types';
import { customerDate, customerTitle } from '../validation';

type Line = Customer | CustomerOverviewLine;
function isOverview(line: Line): line is CustomerOverviewLine {
  return 'orderCount' in line;
}

export function CustomerTable({ items, withSales }: {items: Line[]; withSales: boolean}) {
  return <section aria-label="Список клиентов" className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
    <div className="hidden overflow-x-auto lg:block">
      <table className="w-full text-left text-sm">
        <thead className="bg-slate-50">
          <tr>{['Клиент', 'Телефон', 'WhatsApp', 'Город / регион',
            ...(withSales ? ['Заказов', 'Последний заказ', 'Дата заказа'] : [])].map((title) =>
            <th scope="col" key={title} className="px-4 py-3 font-semibold text-slate-600">{title}</th>)}</tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {items.map((line) => <tr key={line.id} className="entity-clickable-row group transition-colors">
            <td className="px-4 py-4 font-semibold text-blue-700">
              <Link className="entity-stretched-link hover:underline" to={'/customers/' + encodeURIComponent(line.id)}>{customerTitle(line)}</Link>
            </td>
            <td className="px-4 py-4">{line.phone || '—'}</td>
            <td className="px-4 py-4">{line.whatsappPhone || '—'}</td>
            <td className="px-4 py-4">{line.cityRegion || '—'}</td>
            {withSales && isOverview(line) && <>
              <td className="px-4 py-4">{line.orderCount}</td>
              <td className="px-4 py-4">{line.lastSaleNumber || '—'}</td>
              <td className="px-4 py-4">{customerDate(line.lastOrderAt)}</td>
            </>}
          </tr>)}
        </tbody>
      </table>
    </div>
    <div className="divide-y divide-slate-100 lg:hidden">
      {items.map((line) => <article key={line.id} className="entity-clickable-row group space-y-3 p-4 transition-colors">
        <Link className="entity-stretched-link block break-words font-semibold text-blue-700"
          to={'/customers/' + encodeURIComponent(line.id)}>{customerTitle(line)}</Link>
        <p className="break-all text-sm text-slate-600">Телефон: {line.phone || '—'}</p>
        <p className="break-all text-sm text-slate-600">WhatsApp: {line.whatsappPhone || '—'}</p>
        <p className="text-sm text-slate-600">Регион: {line.cityRegion || '—'}</p>
        {withSales && isOverview(line) && <p className="text-sm text-slate-600">
          Заказов: {line.orderCount} · Последний: {line.lastSaleNumber || '—'} · {customerDate(line.lastOrderAt)}
        </p>}
      </article>)}
    </div>
  </section>;
}
