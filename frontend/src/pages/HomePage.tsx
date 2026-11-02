import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ArrowRight, Clock3, Package, Plus, ShoppingCart, TriangleAlert, Truck, ArrowUpRight, Search, type LucideIcon } from 'lucide-react';
import { Link } from 'react-router';
import { useAuth } from '../features/auth/AuthProvider';
import { dashboardApi, dashboardKeys } from '../features/dashboard/api';
import type { DashboardOverview } from '../features/dashboard/types';
import { displaySaleNumber } from '../shared/lib/format';
import { money, saleDate, saleStatusNames } from '../features/sales/validation';
import { navigationItems, isNavigationAllowed } from '../shared/lib/navigation';
import { Button } from '../shared/ui/button';
import { ErrorState } from '../shared/components/ErrorState';
import { LoadingState } from '../shared/components/LoadingState';

function previousDay(day: string): string {
  const date = new Date(day + 'T12:00:00Z'); date.setUTCDate(date.getUTCDate() - 1);
  return date.toISOString().slice(0,10);
}
function dayLabel(iso: string, includeYear = false): string {
  const date = new Date(iso + 'T12:00:00+06:00');
  return new Intl.DateTimeFormat('ru-RU', {
    timeZone: 'Asia/Bishkek', day: 'numeric',
    month: includeYear ? 'long' : 'short',
    ...(includeYear ? { year: 'numeric' } : {}),
  }).format(date);
}
function Stat({ title, value, note, icon: Icon, path }: {
  title: string; value: number; note: string; icon: LucideIcon; path: string;
}) {
  return <Link to={path} className="group flex min-h-40 flex-col justify-between rounded-[20px] border border-[#e3e9ee] bg-white p-5 transition-all duration-200 hover:-translate-y-0.5 hover:border-[#a2bac8] hover:shadow-[0_12px_28px_rgba(22,42,55,0.09)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#416c82] motion-reduce:transform-none motion-reduce:transition-none">
    <div className="flex items-start justify-between">
      <span className="flex size-10 items-center justify-center rounded-xl bg-[#e9f2f5] text-[#416c82]"><Icon className="size-5" aria-hidden="true" /></span>
      <ArrowUpRight className="size-4 text-[#8da0aa] transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5 motion-reduce:transition-none" aria-hidden="true" />
    </div>
    <div className="mt-4">
      <p className="text-xs font-semibold text-[#697a85]">{title}</p>
      <p className="mt-1 text-[clamp(1.75rem,2.5vw,2.35rem)] font-bold tracking-tight tabular-nums text-[#182d38]">{value.toLocaleString('ru-RU')}</p>
      <p className="mt-1 text-xs text-[#85939d]">{note}</p>
    </div>
  </Link>;
}

function WeekChart({ days }: { days: DashboardOverview['ordersByDay'] }) {
  const [focused, setFocused] = useState<string | null>(null);
  if (!days.length) return <section className="rounded-[22px] border border-[#e5ebef] bg-white p-6" aria-label="Заказы за 7 дней"><h3 className="text-lg font-semibold">Динамика заказов</h3><p className="mt-4 text-sm text-slate-500">Данных за выбранный период пока нет.</p></section>;
  const maximum = Math.max(1, ...days.map(x => x.orders));
  const top = Math.ceil(maximum / 4) * 4 || 4;
  const baseline = 180;
  const points = days.map((d, i) => ({
    ...d, x: 48 + i * 612 / Math.max(1, days.length - 1),
    y: baseline - 140 * d.orders / top,
  }));
  const line = points.map((p, i) => (i ? 'L' : 'M') + p.x.toFixed(1) + ' ' + p.y.toFixed(1)).join(' ');
  const fill = line + ' L' + points.at(-1)!.x + ' ' + baseline + ' L' + points[0]!.x + ' ' + baseline + ' Z';
  const active = points.find(p => p.day === focused);
  return <section aria-label="Заказы за 7 дней" className="min-w-0 rounded-[22px] border border-[#e5ebef] bg-white p-5 sm:p-6">
    <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
      <div><h3 className="text-lg font-semibold tracking-tight text-[#182d38]">Динамика заказов</h3>
        <p className="mt-1 text-xs text-[#81909a]">По дате создания · время Бишкек</p></div>
      <span className="rounded-full bg-[#edf3f6] px-3 py-2 text-xs font-semibold text-[#416c82]">7 дней</span>
    </div>
    <div className="mb-3 flex flex-wrap items-baseline gap-2">
      <strong className="text-4xl font-bold tracking-tight tabular-nums text-[#182d38]">{active ? active.orders : days.reduce((a,d)=>a+d.orders,0)}</strong>
      <span className="text-xs text-[#81909a]">{active ? 'заказов · ' + dayLabel(active.day) : 'заказов за неделю'}</span>
    </div>
    <svg viewBox="0 0 720 222" role="img" aria-label={days.map(d => dayLabel(d.day)+': '+d.orders+' заказов').join('; ')} className="h-auto w-full">
      <defs><linearGradient id="chairx-chart-fill" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#709dad" stopOpacity=".3"/><stop offset="100%" stopColor="#709dad" stopOpacity="0"/></linearGradient></defs>
      {[0, 0.25, 0.5, 0.75, 1].map(n => <g key={n}><line x1="48" x2="674" y1={baseline-n*140} y2={baseline-n*140} stroke="#e8edf0" strokeDasharray="4 6"/><text x="34" y={baseline-n*140+4} textAnchor="end" fill="#8797a0" fontSize="11">{Math.round(top*n)}</text></g>)}
      <path d={fill} fill="url(#chairx-chart-fill)" />
      <path d={line} stroke="#416c82" strokeWidth="3" strokeLinejoin="round" strokeLinecap="round" fill="none" />
      {points.map(p => <g key={p.day}>
        <circle cx={p.x} cy={p.y} r={focused===p.day?7:5} fill="#416c82" stroke="white" strokeWidth="2" />
        <circle cx={p.x} cy={p.y} r="17" fill="transparent" tabIndex={0} role="button"
          aria-label={dayLabel(p.day)+': '+p.orders+' заказов'} className="cursor-pointer outline-none focus-visible:stroke-[#416c82] focus-visible:stroke-2"
          onMouseEnter={()=>setFocused(p.day)} onMouseLeave={()=>setFocused(null)}
          onFocus={()=>setFocused(p.day)} onBlur={()=>setFocused(null)}
          onClick={()=>setFocused(focused===p.day?null:p.day)}
          onKeyDown={event=>{if(event.key==='Enter'||event.key===' '){event.preventDefault();setFocused(focused===p.day?null:p.day);}}}/>
        <text x={p.x} y="207" textAnchor="middle" fontSize="11" fill={focused===p.day?'#416c82':'#8c9aa4'}>{dayLabel(p.day)}</text>
      </g>)}
    </svg>
    <div className="mt-2 flex items-center gap-2 text-xs text-[#81909a]"><span className="size-2 rounded-full bg-[#416c82]"/> Заказы за день <span className="ml-auto">Данные сервера</span></div>
  </section>;
}

function Attention({ data, deliveryRight, inventoryRight }: {
  data: DashboardOverview; deliveryRight: boolean; inventoryRight: boolean;
}) {
  if (!deliveryRight && !inventoryRight) return null;
  const alerts = [
    ...(deliveryRight && data.delayedDeliveries !== null && data.delayedDeliveries > 0
      ? [{ name: 'Просроченные доставки', count: data.delayedDeliveries,
        desc: 'Плановая дата прошла; доставка не завершена', path: '/deliveries?operational=false&pendingOnly=true&plannedTo=' + previousDay(data.businessDate), icon: Truck }] : []),
    ...(inventoryRight && data.lowStockVariants !== null && data.lowStockVariants > 0
      ? [{ name: 'Мало товара', count: data.lowStockVariants,
        desc: 'Вариации с доступным остатком 1–5 шт.', path: '/inventory?lowStock=true', icon: Package }] : []),
  ];
  return <section aria-label="Требует внимания" className="rounded-[22px] border border-[#e5ebef] bg-white p-5 sm:p-6">
    <h3 className="mb-3 flex items-center gap-2 text-lg font-semibold text-[#182d38]">
      <TriangleAlert className="size-5 text-[#c17952]" aria-hidden="true" />Требует внимания
    </h3>
    {alerts.length === 0
      ? <p className="rounded-xl bg-[#edf4ee] p-4 text-sm text-[#35614a]">Нет проблем по доступным вам показателям.</p>
      : <div className="divide-y divide-slate-100">{alerts.map(a =>
        <Link key={a.path} to={a.path} className="flex items-center gap-3 py-4 rounded-xl px-2 transition-colors hover:bg-[#f3f7f9] focus-visible:outline-2 focus-visible:outline-[#416c82]">
          <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-[#fbefe8] text-[#ad653d]">
            <a.icon className="size-5" aria-hidden="true" />
          </span>
          <span className="min-w-0 flex-1"><strong className="block text-sm">{a.name}</strong>
            <span className="text-xs text-slate-500">{a.desc}</span></span>
          <b className="rounded-full bg-[#fbefe8] px-2 py-1 text-sm text-[#ad653d]">{a.count}</b>
          <ArrowRight className="size-4 shrink-0" aria-hidden="true" />
        </Link>)}</div>}
  </section>;
}
function RecentSales({ items }: { items: DashboardOverview['recentSales'] }) {
  const [status, setStatus] = useState('Все');
  const [search, setSearch] = useState('');
  const availableStatuses = ['Все', 'DRAFT', 'CONFIRMED', 'FULFILLED'] as const;
  const filtered = items.filter(s => (status === 'Все' || s.status === status) &&
    [s.saleNumber, s.customerName || '', s.products || ''].some(x => x.toLocaleLowerCase('ru-RU').includes(search.toLocaleLowerCase('ru-RU'))));
  const badgeClass = (value: string) => value === 'FULFILLED' ? 'bg-[#e7f4ec] text-[#357254]' :
    value === 'CONFIRMED' ? 'bg-[#fbefdb] text-[#946f30]' : value === 'CANCELLED' ?
    'bg-[#fcebea] text-[#ad4f4c]' : 'bg-[#edf0f3] text-[#657480]';
  return <section aria-label="Последние заказы" className="overflow-hidden rounded-[22px] border border-[#e5ebef] bg-white">
    <div className="flex flex-wrap items-start justify-between gap-3 p-5 sm:p-6">
      <div><h3 className="text-lg font-semibold tracking-tight text-[#182d38]">Последние заказы</h3>
        <p className="mt-1 text-xs text-[#81909a]">Недавняя активность продаж</p></div>
      <Link to="/sales" className="inline-flex items-center gap-1 rounded-full px-3 py-2 text-xs font-semibold text-[#416c82] hover:bg-[#edf3f6]">Все продажи <ArrowUpRight className="size-4" aria-hidden="true"/></Link>
    </div>
    <div className="flex flex-wrap items-center gap-2 px-5 pb-4 sm:px-6">
      <div className="flex flex-wrap gap-1" role="group" aria-label="Фильтр статуса заказа">
        {availableStatuses.map(v=><button key={v} type="button" aria-pressed={status===v} onClick={()=>setStatus(v)}
          className={'rounded-full px-3 py-2 text-xs font-medium transition-colors focus-visible:outline-2 focus-visible:outline-[#416c82] '+(status===v?'bg-[#182d38] text-white':'bg-[#f1f5f7] text-[#647783] hover:bg-[#e5edf1]')}>
          {v==='Все'?'Все':v==='DRAFT'?'Черновики':v==='CONFIRMED'?'Подтверждены':'Выполнены'}
        </button>)}
      </div>
      <label className="relative ml-auto"><Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-[#8495a0]" aria-hidden="true"/>
        <span className="sr-only">Поиск последних заказов</span>
        <input value={search} onChange={e=>setSearch(e.target.value)} placeholder="Номер, клиент, товар"
          className="w-full max-w-56 rounded-full border border-[#e4ebef] bg-[#f6f8f9] py-2 pl-9 pr-3 text-xs text-[#182d38] placeholder:text-[#8b99a2] focus-visible:outline-2 focus-visible:outline-[#416c82]"/>
      </label>
    </div>
    {filtered.length===0 ? <p className="px-6 pb-6 text-sm text-[#81909a]">{items.length?'По выбранным фильтрам заказов нет.':'Заказов пока нет.'}</p>
    : <div className="divide-y divide-[#ebeff1]">
      {filtered.map(s=><Link key={s.id} to={'/sales/'+encodeURIComponent(s.id)}
        aria-label={'Открыть продажу '+s.saleNumber}
        className="group grid grid-cols-[minmax(0,1fr)_auto] items-center gap-3 px-5 py-4 transition-colors hover:bg-[#f4f8fa] focus-visible:outline-2 focus-visible:outline-[#416c82] sm:grid-cols-[100px_minmax(120px,1.1fr)_minmax(120px,1.3fr)_110px_110px_20px] sm:px-6">
        <span className="font-semibold text-[#416c82]">№ {displaySaleNumber(s.saleNumber)}</span>
        <span className="min-w-0 text-sm text-[#273b47] sm:truncate">{s.customerName||'Без имени'}</span>
        <span className="col-span-2 min-w-0 truncate text-xs text-[#81909a] sm:col-span-1">{s.products||'—'}</span>
        <span className="font-semibold text-[#273b47]">{money(s.total)}</span>
        <span className={'justify-self-end rounded-full px-2.5 py-1 text-xs font-medium sm:justify-self-start '+badgeClass(s.status)}>{saleStatusNames[s.status]}</span>
        <ArrowUpRight className="hidden size-4 text-[#96a5ae] group-hover:text-[#416c82] sm:block" aria-hidden="true"/>
        <span className="col-span-2 text-xs text-[#9aa5ae] sm:hidden">{saleDate(s.createdAt)}</span>
      </Link>)}
    </div>}
  </section>;
}
export function HomePage() {
  const { user, hasPermission, hasAnyPermission } = useAuth();
  const sales = hasPermission('SALES_READ');
  const deliveries = hasPermission('DELIVERIES_READ');
  const inventory = hasPermission('INVENTORY_READ');
  const allowed = sales || deliveries || inventory;
  const overview = useQuery({
    queryKey: dashboardKeys.overview(user?.id ?? ''),
    queryFn: dashboardApi.overview, enabled: allowed, staleTime: 30_000,
  });
  const data = overview.data;
  const quickLinks = [
    { label: 'Склады', path: '/inventory' },
    { label: 'Доставки', path: '/deliveries' },
    { label: 'Закрыть день', path: '/daily-closing' },
  ].filter(x => {
    const nav = navigationItems.find(i => i.path === x.path);
    return !!nav && isNavigationAllowed(nav, hasAnyPermission);
  });

  return <div className="space-y-5 rounded-[26px] bg-[#e9eef0] p-3 text-[#182d38] sm:space-y-6 sm:p-6 xl:p-8">
    <header className="flex flex-wrap items-center justify-between gap-4">
      <div>
        <p className="mb-2 text-[11px] font-semibold uppercase tracking-[0.16em] text-[#82939d]">ChairX / Workspace</p>
        <h2 className="text-2xl font-bold tracking-tight text-[#182d38] sm:text-4xl">Рабочий обзор</h2>
        <p className="mt-1 text-sm text-slate-500">
          {data ? dayLabel(data.businessDate, true) : 'Рабочее пространство ChairX'}
        </p>
      </div>
      {hasPermission('SALES_CREATE') && <Button asChild className="rounded-full bg-[#182d38] px-5 text-white hover:bg-[#305063]">
        <Link to="/sales/new"><Plus className="size-4" aria-hidden="true" />Новая продажа</Link>
      </Button>}
    </header>
    {allowed && overview.isPending && <LoadingState label="Загружаем актуальные показатели…" />}
    {allowed && overview.isError && <ErrorState error={overview.error} onRetry={() => void overview.refetch()} />}
    {allowed && data && <>
      <section aria-label="Показатели" className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-5">
        {sales && <Stat title="Заказов сегодня"
          value={data.ordersByDay.find(day=>day.day===data.businessDate)?.orders??0}
          note="Создано сегодня" icon={ShoppingCart} path={'/sales?operational=false&from='+data.businessDate+'&to='+data.businessDate} />}
        {deliveries && data.pendingDeliveries !== null && <Stat title="Ожидают доставки"
          value={data.pendingDeliveries} note="Готовы к отправке или в пути" icon={Truck} path="/deliveries?operational=false&pendingOnly=true" />}
        {deliveries && data.deliveredToday !== null && <Stat title="Доставлено сегодня"
          value={data.deliveredToday} note="Завершены за текущий день" icon={Truck} path={'/deliveries?operational=false&deliveredOn='+data.businessDate} />}
        {inventory && data.availableUnits !== null && <Stat title="Доступно на складах"
          value={data.availableUnits} note="С учётом резерва и блокировок" icon={Package} path="/inventory?availability=recorded" />}
        {sales && data.activeOrders !== null && <Stat title="В работе"
          value={data.activeOrders} note="Черновики и подтверждённые" icon={Clock3} path="/sales?operational=false&activeOnly=true" />}
      </section>
      <div className="grid items-start gap-4 xl:grid-cols-[minmax(0,1.65fr)_minmax(300px,1fr)]">
        {sales && <WeekChart days={data.ordersByDay} />}
        <Attention data={data} deliveryRight={deliveries} inventoryRight={inventory} />
      </div>
      {sales && <RecentSales items={data.recentSales} />}
    </>}
    {!allowed && <section className="rounded-[22px] border border-[#e5ebef] bg-white p-6">
      <h3 className="font-bold text-slate-900">Показатели недоступны</h3>
      <p className="mt-2 text-sm text-slate-500">Права на просмотр заказов, доставок или складов ещё не назначены.</p>
    </section>}
    {!!quickLinks.length && <nav aria-label="Быстрые действия" className="flex flex-wrap gap-2">
      {quickLinks.map(x => <Button asChild variant="outline" key={x.path}>
        <Link to={x.path}>{x.label}<ArrowRight className="size-4" aria-hidden="true" /></Link>
      </Button>)}
    </nav>}
  </div>;
}
