import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ArrowRight, Clock3, Package, Plus, ShoppingCart, TriangleAlert, Truck, ArrowUpRight, Search, CheckCircle2, Sparkles, type LucideIcon } from 'lucide-react';
import { StudioHero } from '../features/dashboard/components/StudioHero';
import { StudioActivityChart } from '../features/dashboard/components/StudioActivityChart';
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
function Stat({ title, value, note, icon: Icon, path }: {
  title: string; value: number; note: string; icon: LucideIcon; path: string;
}) {
  const tones: Record<string, string> = {
    'Заказов сегодня': 'bg-[#e8f4f3] text-[#23766e]',
    'Ожидают доставки': 'bg-[#fff0d8] text-[#9c6924]',
    'Доставлено сегодня': 'bg-[#e5f5e9] text-[#28754f]',
    'Доступно на складах': 'bg-[#edf1f5] text-[#46647b]',
    'В работе': 'bg-[#e8f4f3] text-[#23766e]',
  };
  return <Link to={path} className="group relative flex min-h-43 flex-col justify-between rounded-[22px] border border-[#e0e8eb] bg-white p-5 shadow-[0_5px_16px_rgba(23,47,58,0.025)] transition-[border-color,box-shadow] hover:border-[#9ebfc2] hover:shadow-[0_12px_28px_rgba(23,47,58,0.08)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#267f79] sm:p-6">
    <div className="flex items-start justify-between gap-2">
      <span className={'flex size-11 items-center justify-center rounded-2xl '+(tones[title] || tones['В работе'])}>
        <Icon className="size-5" aria-hidden="true" />
      </span>
      <ArrowUpRight className="size-4 text-[#607985] transition-transform group-hover:translate-x-0.5 group-hover:-translate-y-0.5 motion-reduce:transition-none" aria-hidden="true"/>
    </div>
    <div className="mt-5">
      <p className="text-sm font-medium text-[#516977]">{title}</p>
      <p className="mt-1 text-[clamp(1.9rem,2.7vw,2.6rem)] font-bold leading-none tracking-[-0.035em] tabular-nums text-[#182d38]">{value.toLocaleString('ru-RU')}</p>
      <p className="mt-2 text-xs leading-relaxed text-[#516977]">{note}</p>
    </div>
  </Link>;
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
  return <section aria-label="Требует внимания" className="rounded-[24px] border border-[#e0e8eb] bg-white p-5 sm:p-7">
    <div className="mb-5 flex items-center gap-3"><span className="flex size-11 shrink-0 items-center justify-center rounded-2xl bg-[#fff0db] text-[#9c6924]"><TriangleAlert className="size-5" aria-hidden="true" /></span><div><h3 className="text-lg font-bold tracking-tight text-[#182d38]">Требует внимания</h3><p className="mt-1 text-xs text-[#516977]">Важные задачи на сегодня</p></div></div>
    {alerts.length === 0
      ? <p className="rounded-2xl bg-[#e8f5eb] p-5 text-sm font-medium leading-relaxed text-[#286b48]">Нет проблем по доступным вам показателям.</p>
      : <div className="space-y-3">{alerts.map(a =>
        <Link key={a.path} to={a.path} className="flex min-h-19 items-center gap-3 rounded-2xl border border-[#f1e7d8] bg-[#fffbf5] p-4 transition-colors hover:bg-[#fff3e2] focus-visible:outline-2 focus-visible:outline-[#267f79]">
          <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-[#fff0db] text-[#936125]">
            <a.icon className="size-5" aria-hidden="true" />
          </span>
          <span className="min-w-0 flex-1"><strong className="block text-sm">{a.name}</strong>
            <span className="mt-1 block text-xs leading-relaxed text-[#516977]">{a.desc}</span></span>
          <b className="flex size-9 shrink-0 items-center justify-center rounded-full bg-white text-sm font-bold tabular-nums text-[#915d1f]">{a.count}</b>
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
  return <section aria-label="Последние заказы" className="overflow-hidden rounded-[24px] border border-[#e0e8eb] bg-white">
    <div className="flex flex-wrap items-start justify-between gap-3 px-5 pb-4 pt-6 sm:px-7 sm:pt-7">
      <div><p className="mb-2 flex items-center gap-2 text-xs font-bold text-[#27756d]"><ShoppingCart className="size-4" aria-hidden="true" /> Активность</p><h3 className="text-xl font-bold tracking-tight text-[#182d38]">Последние заказы</h3>
        <p className="mt-1 text-xs text-[#516977]">Недавняя активность продаж</p></div>
      <Link to="/sales" className="inline-flex min-h-11 items-center gap-1 rounded-xl px-3 text-sm font-semibold text-[#267f79] hover:bg-[#edf3f6]">Все продажи <ArrowUpRight className="size-4" aria-hidden="true"/></Link>
    </div>
    <div className="flex flex-wrap items-center gap-2 border-b border-[#e8eff0] px-5 pb-4 sm:px-7">
      <div className="flex flex-wrap gap-1" role="group" aria-label="Фильтр статуса заказа">
        {availableStatuses.map(v=><button key={v} type="button" aria-pressed={status===v} onClick={()=>setStatus(v)}
          className={'min-h-10 rounded-xl px-3 py-2 text-xs font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-[#416c82] '+(status===v?'bg-[#182d38] text-white':'bg-[#f1f5f7] text-[#647783] hover:bg-[#e5edf1]')}>
          {v==='Все'?'Все':v==='DRAFT'?'Черновики':v==='CONFIRMED'?'Подтверждены':'Выполнены'}
        </button>)}
      </div>
      <label className="relative ml-auto"><Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-[#8495a0]" aria-hidden="true"/>
        <span className="sr-only">Поиск последних заказов</span>
        <input value={search} onChange={e=>setSearch(e.target.value)} placeholder="Номер, клиент, товар"
          className="w-full max-w-56 rounded-xl border border-[#d9e5e6] bg-[#f8faf9] py-2 pl-9 pr-3 text-xs text-[#182d38] placeholder:text-[#8b99a2] focus-visible:outline-2 focus-visible:outline-[#416c82]"/>
      </label>
    </div>
    {filtered.length===0 ? <p className="px-6 pb-6 text-sm text-[#516977]">{items.length?'По выбранным фильтрам заказов нет.':'Заказов пока нет.'}</p>
    : <div className="divide-y divide-[#ebeff1]">
      {filtered.map(s=><Link key={s.id} to={'/sales/'+encodeURIComponent(s.id)}
        aria-label={'Открыть продажу '+s.saleNumber}
        className="group grid min-w-0 grid-cols-[minmax(0,1fr)_auto] items-center gap-3 px-5 py-4 transition-colors hover:bg-[#f4f8fa] focus-visible:outline-2 focus-visible:outline-[#416c82] lg:grid-cols-[100px_minmax(120px,1.1fr)_minmax(120px,1.3fr)_110px_110px_20px] lg:px-7">
        <span className="font-semibold text-[#416c82]">{displaySaleNumber(s.saleNumber)}</span>
        <span className="min-w-0 text-sm text-[#273b47] sm:truncate">{s.customerName||'Без имени'}</span>
        <span className="col-span-2 min-w-0 truncate text-xs text-[#516977] lg:col-span-1">{s.products||'—'}</span>
        <span className="font-semibold text-[#273b47]">{money(s.total)}</span>
        <span className={'justify-self-end rounded-full px-2.5 py-1 text-xs font-medium lg:justify-self-start '+badgeClass(s.status)}>{saleStatusNames[s.status]}</span>
        <ArrowUpRight className="hidden size-4 text-[#96a5ae] group-hover:text-[#416c82] lg:block" aria-hidden="true"/>
        <span className="col-span-2 text-xs text-[#9aa5ae] lg:hidden">{saleDate(s.createdAt)}</span>
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
  const name = user?.displayName?.trim().split(/\s+/u)[0] || 'коллега';
  const quickLinks = [
    { label: 'Склады', path: '/inventory' },
    { label: 'Доставки', path: '/deliveries' },
    { label: 'Закрыть день', path: '/daily-closing' },
  ].filter(x => {
    const nav = navigationItems.find(i => i.path === x.path);
    return !!nav && isNavigationAllowed(nav, hasAnyPermission);
  });

  return <div className="min-w-0 space-y-5 pb-5 text-[#182d38] sm:space-y-6">
    <header className="flex flex-wrap items-center justify-between gap-4">
      <div>
        <h2 className="text-[clamp(1.65rem,3vw,2.4rem)] font-bold leading-tight tracking-[-0.035em]">Добро пожаловать, {name}!</h2>
        <p className="mt-1 text-sm text-slate-500">
          Управляйте заказами, командой и операциями из одного рабочего пространства.
        </p>
      </div>
      {hasPermission('SALES_CREATE') && <Button asChild className="min-h-12 rounded-xl bg-[#172f3c] px-5 text-white hover:bg-[#2a505a]">
        <Link to="/sales/new"><Plus className="size-4" aria-hidden="true" />Новая продажа</Link>
      </Button>}
    </header>
    {allowed && overview.isPending && <LoadingState label="Загружаем актуальные показатели…" />}
    {allowed && overview.isError && <ErrorState error={overview.error} onRetry={() => void overview.refetch()} />}
    {allowed && data && <>
      <StudioHero data={data} name={name} sales={sales} deliveries={deliveries} inventory={inventory} />
      <section aria-label="Показатели" className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-5">
        {sales && <Stat title="Заказов сегодня"
          value={data.ordersByDay.find(day=>day.day===data.businessDate)?.orders??0}
          note="Создано сегодня" icon={ShoppingCart} path={'/sales?operational=false&from='+data.businessDate+'&to='+data.businessDate} />}
        {deliveries && data.pendingDeliveries !== null && <Stat title="Ожидают доставки"
          value={data.pendingDeliveries} note="Готовы к отправке или в пути" icon={Truck} path="/deliveries?operational=false&pendingOnly=true" />}
        {deliveries && data.deliveredToday !== null && <Stat title="Доставлено сегодня"
          value={data.deliveredToday} note="Завершены за текущий день" icon={CheckCircle2} path={'/deliveries?operational=false&deliveredOn='+data.businessDate} />}
        {inventory && data.availableUnits !== null && <Stat title="Доступно на складах"
          value={data.availableUnits} note="С учётом резерва и блокировок" icon={Package} path="/inventory?availability=recorded" />}
        {sales && data.activeOrders !== null && <Stat title="В работе"
          value={data.activeOrders} note="Черновики и подтверждённые" icon={Clock3} path="/sales?operational=false&activeOnly=true" />}
      </section>
      <div className="grid min-w-0 items-stretch gap-4 xl:grid-cols-[minmax(0,1.55fr)_minmax(300px,1fr)]">
        {sales && <StudioActivityChart days={data.ordersByDay} />}
        <Attention data={data} deliveryRight={deliveries} inventoryRight={inventory} />
      </div>
      {sales && <RecentSales items={data.recentSales} />}
    </>}
    {!allowed && <section className="rounded-[24px] border border-[#e0e8eb] bg-white p-6">
      <h3 className="font-bold text-slate-900">Показатели недоступны</h3>
      <p className="mt-2 text-sm text-slate-500">Права на просмотр заказов, доставок или складов ещё не назначены.</p>
    </section>}
    {!!quickLinks.length && <section className="rounded-[24px] border border-[#e0e8eb] bg-[#f3f8f6] p-5 sm:p-7"><h3 className="mb-4 flex items-center gap-2 text-lg font-bold"><Sparkles className="size-5 text-[#27756d]" aria-hidden="true" />Основные действия</h3><nav aria-label="Быстрые действия" className="grid gap-2 sm:grid-cols-3">
      {quickLinks.map(x => <Button asChild variant="outline" className="min-h-14 justify-between rounded-2xl border-[#e1e9e8] bg-white text-[#24424b]" key={x.path}>
        <Link to={x.path}>{x.label}<ArrowRight className="size-4" aria-hidden="true" /></Link>
      </Button>)}
    </nav></section>}
  </div>;
}
