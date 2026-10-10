import { Link } from 'react-router';
import type { Customer, CustomerOverviewLine } from '../types';
import { customerDate, customerTitle } from '../validation';

type Line = Customer | CustomerOverviewLine;
function isOverview(line: Line): line is CustomerOverviewLine {
  return 'orderCount' in line;
}

export function CustomerTable({ items, withSales }: {
  items: Line[]; withSales: boolean;
}) {
  return <section aria-label="Список клиентов" className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-xs">
    <div className="hidden overflow-x-auto lg:block">
      <table className="w-full text-left text-sm">
        <thead className="bg-slate-50">
          <tr>{['Клиент', 'Телефон', 'WhatsApp', 'Город / регион',
            ...(withSales ? ['Заказов', 'Последний заказ', 'Дата заказа'] : []),
            'Действие'].map((title) =>
            <th scope="col" key={title} className="px-4 py-3 font-semibold text-slate-600">{title}</th>)}</tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {items.map((line) => <tr key={line.id}>
            <td className="px-4 py-4 font-semibold text-slate-900">{customerTitle(line)}</td>
            <td className="px-4 py-4">{line.phone || '—'}</td>
            <td className="px-4 py-4">{line.whatsappPhone || '—'}</td>
            <td className="px-4 py-4">{line.cityRegion || '—'}</td>
            {withSales && isOverview(line) && <>
              <td className="px-4 py-4">{line.orderCount}</td>
              <td className="px-4 py-4">{line.lastSaleNumber || '—'}</td>
              <td className="px-4 py-4">{customerDate(line.lastOrderAt)}</td>
            </>}
            <td className="px-4 py-4"><Link className="font-semibold text-indigo-700 hover:underline"
              to={'/customers/' + encodeURIComponent(line.id)}>Открыть</Link></td>
          </tr>)}
        </tbody>
      </table>
    </div>
    <div className="divide-y divide-slate-100 lg:hidden">
      {items.map((line) => <article key={line.id} className="space-y-3 p-4">
        <div className="flex flex-wrap items-start justify-between gap-2">
          <h3 className="break-words font-semibold text-slate-900">{customerTitle(line)}</h3>
          <Link className="inline-flex min-h-11 items-center font-semibold text-indigo-700" to={'/customers/' + encodeURIComponent(line.id)}>Открыть</Link>
        </div>
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
