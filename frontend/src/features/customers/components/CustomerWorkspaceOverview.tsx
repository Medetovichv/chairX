import { ArrowUpRight, ContactRound, PhoneCall, ShoppingBag } from 'lucide-react';
import type { Customer, CustomerOverviewLine } from '../types';

type Line = Customer | CustomerOverviewLine;
type Props = {
  items: Line[];
  total: number | null;
  page: number;
  limitedSearch: boolean;
  withSales: boolean;
};
const isOverview = (line: Line): line is CustomerOverviewLine => 'orderCount' in line;

/** A contextual directory summary. Never present page aggregates as global sales metrics. */
export function CustomerWorkspaceOverview({ items, total, page, limitedSearch, withSales }: Props) {
  const withPhone = items.filter(line => Boolean(line.phone?.trim() || line.whatsappPhone?.trim())).length;
  const withOrders = withSales ? items.filter(line => isOverview(line) && line.orderCount > 0).length : null;
  const metrics = [
    { label: 'В выборке', value: items.length, hint: limitedSearch ? 'Поиск ограничен 20 совпадениями' : 'Клиентов на этой странице', icon: ContactRound },
    { label: 'С контактами', value: withPhone, hint: 'Указан телефон или WhatsApp', icon: PhoneCall },
    ...(withOrders === null ? [] : [{ label: 'С заказами', value: withOrders, hint: 'Среди показанных клиентов', icon: ShoppingBag }]),
  ];
  return <section aria-label="Обзор клиентской базы" className="space-y-3">
    <div className="flex flex-wrap items-center justify-between gap-2">
      <div>
        <h2 className="text-lg font-bold tracking-tight text-[#182d38]">Обзор клиентской базы</h2>
        <p className="mt-1 text-sm text-[#516977]">Показатели рассчитаны по видимым записям, а не по всей базе.</p>
      </div>
      {total !== null && <span className="inline-flex items-center gap-2 rounded-lg bg-[#e6efed] px-3 py-2 text-xs font-semibold tabular-nums text-[#285f58]">
        <ArrowUpRight className="size-4" aria-hidden="true"/>Страница {page + 1} · всего {total.toLocaleString('ru-RU')}
      </span>}
    </div>
    <div className={withOrders === null ? 'grid gap-3 sm:grid-cols-2' : 'grid gap-3 sm:grid-cols-3'}>
      {metrics.map(({ label, value, hint, icon: Icon }) =>
        <div key={label} className="flex min-h-32 flex-col justify-between rounded-2xl border border-[#dfe7eb] bg-white p-5">
          <div className="flex items-center justify-between gap-2">
            <p className="text-sm font-semibold text-[#405b65]">{label}</p>
            <Icon className="size-5 text-[#286e66]" aria-hidden="true"/>
          </div>
          <div className="mt-4">
            <strong className="text-3xl font-bold tabular-nums text-[#182d38]">{value.toLocaleString('ru-RU')}</strong>
            <p className="mt-1 text-xs text-[#516977]">{hint}</p>
          </div>
        </div>)}
    </div>
  </section>;
}
