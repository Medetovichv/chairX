import { ArrowRight, ArrowUpRight, ChartNoAxesCombined, Package, ShoppingCart, Truck } from 'lucide-react';
import { Link } from 'react-router';
import type { DashboardOverview } from '../types';

interface Props {
  data: DashboardOverview;
  name: string;
  sales: boolean;
  deliveries: boolean;
  inventory: boolean;
}

/** Hero data is derived exclusively from role-filtered dashboard API; no fabricated KPIs. */
export function StudioHero({ data, name, sales, deliveries, inventory }: Props) {
  const count = sales ? data.orders30Days : null;
  const date = new Intl.DateTimeFormat('ru-RU', {
    day: 'numeric', month: 'long', year: 'numeric', timeZone: 'Asia/Bishkek',
  }).format(new Date(data.businessDate + 'T12:00:00+06:00'));
  const primary = sales ? { to:'/sales', name:'Продажи', icon:ShoppingCart }
    : deliveries ? { to:'/deliveries', name:'Доставки', icon:Truck }
    : inventory ? { to:'/inventory', name:'Склады', icon:Package } : null;
  return <section aria-label="Обзор бизнеса"
    className="relative isolate overflow-hidden rounded-[28px] bg-[#172f3c] p-6 text-white sm:p-8 xl:p-10">
    <div aria-hidden="true" className="pointer-events-none absolute -right-28 -top-40 size-96 rounded-full border-[65px] border-[#2f6370]/35" />
    <div aria-hidden="true" className="pointer-events-none absolute -bottom-48 right-1/4 size-80 rounded-full bg-[#377c7b]/15" />
    <div className="relative grid gap-7 lg:grid-cols-[minmax(0,1.3fr)_minmax(240px,.7fr)] lg:items-end">
      <div className="min-w-0">
        <div className="mb-5 inline-flex items-center gap-2 rounded-full border border-white/20 bg-white/10 px-3 py-1.5 text-xs font-semibold text-[#d5f1ec]">
          <span aria-hidden="true" className="size-2 rounded-full bg-[#76d9be]" /> Рабочий обзор · {date}
        </div>
        <h3 className="max-w-[20ch] text-[clamp(1.8rem,3.5vw,3.2rem)] font-semibold leading-[1.13] tracking-[-0.035em]">
          Контролируйте работу бизнеса в одном месте
        </h3>
        <p className="mt-4 max-w-[58ch] text-sm leading-relaxed text-[#c6d8dc] sm:text-base">
          {name}, здесь собраны заказы, доставки и остатки, доступные вашей роли. Все показатели открывают соответствующие записи.
        </p>
        <div className="mt-6 flex flex-wrap gap-2">
          {primary && <Link to={primary.to} className="inline-flex min-h-11 items-center gap-2 rounded-xl bg-[#a6e0ce] px-4 text-sm font-bold text-[#163a36] transition-colors hover:bg-[#c5f3e5] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white">
            <primary.icon className="size-4" aria-hidden="true" /> Открыть {primary.name.toLowerCase()} <ArrowUpRight className="size-4" aria-hidden="true" />
          </Link>}
          {sales && deliveries && <Link to="/deliveries" className="inline-flex min-h-11 items-center gap-2 rounded-xl border border-white/25 px-4 text-sm font-medium text-white hover:bg-white/10 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white">
            Доставки <Truck className="size-4" aria-hidden="true" />
          </Link>}
        </div>
      </div>
      <div className="min-w-0 rounded-[22px] border border-white/15 bg-white/10 p-5 sm:p-6">
        <div className="flex items-center justify-between gap-3">
          <span className="text-sm font-medium text-[#d4e8e9]">Заказы за 30 дней</span>
          <ChartNoAxesCombined className="size-6 text-[#9cddcb]" aria-hidden="true" />
        </div>
        <p className="mt-5 text-5xl font-semibold tracking-[-0.05em] tabular-nums sm:text-6xl">
          {count === null ? '—' : count.toLocaleString('ru-RU')}
        </p>
        <p className="mt-2 text-xs leading-relaxed text-[#c9dedf]">
          {count === null ? 'Для вашей роли этот показатель недоступен' : 'Все созданные заказы за последние 30 дней'}
        </p>
        {count !== null && <Link to="/sales?operational=false"
          className="mt-5 inline-flex min-h-10 items-center gap-2 text-sm font-semibold text-[#b3ecd9] underline-offset-4 hover:underline">
          Перейти к истории <ArrowRight className="size-4" aria-hidden="true" />
        </Link>}
      </div>
    </div>
  </section>;
}
