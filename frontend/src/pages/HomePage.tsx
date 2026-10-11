import { useQuery } from '@tanstack/react-query';
import { ArrowRight, CalendarDays, Clock3, Package, Plus, ShoppingCart, TriangleAlert, Truck, type LucideIcon } from 'lucide-react';
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
  return <Link to={path} className="group rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition hover:border-blue-300 hover:shadow-md focus-visible:outline-2 focus-visible:outline-blue-600">
    <div className="flex items-center justify-between">
      <span className="flex size-11 items-center justify-center rounded-xl bg-blue-50 text-blue-600">
        <Icon className="size-5" aria-hidden="true" />
      </span>
      <ArrowRight className="size-4 text-slate-400 group-hover:text-blue-600" aria-hidden="true" />
    </div>
    <p className="mt-3 text-sm font-medium text-slate-600">{title}</p>
    <p className="mt-1 text-3xl font-extrabold text-slate-950">{value.toLocaleString('ru-RU')}</p>
    <p className="mt-2 text-xs text-slate-500">{note}</p>
  </Link>;
}
function WeekChart({ days }: { days: DashboardOverview['ordersByDay'] }) {
  if (!days.length) return null;
  const maximum = Math.max(1, ...days.map(x => x.orders));
  const points = days.map((d, i) => ({
    ...d, x: 24 + i * 672 / Math.max(1, days.length - 1),
    y: 160 - 132 * d.orders / maximum,
  }));
  const line = points.map((p, i) => (i ? 'L' : 'M') + p.x.toFixed(1) + ' ' + p.y.toFixed(1)).join(' ');
  const fill = line + ' L' + points.at(-1)!.x + ' 160 L' + points[0]!.x + ' 160 Z';
  return <section aria-label="Заказы за неделю" className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
    <div className="mb-4 flex items-center justify-between gap-3">
      <h3 className="flex items-center gap-2 text-lg font-bold text-slate-900">
        <CalendarDays className="size-5 text-blue-600" aria-hidden="true" />Заказы за 7 дней
      </h3>
      <strong className="rounded-lg bg-blue-50 px-3 py-2 text-sm text-blue-700">
        Всего: {days.reduce((a, d) => a + d.orders, 0)}
      </strong>
    </div>
    <svg viewBox="0 0 720 175" role="img"
      aria-label={days.map(d => dayLabel(d.day) + ': ' + d.orders).join('; ')}
      className="w-full overflow-visible">
      {[0, 0.5, 1].map(n => <line key={n} x1="24" x2="696" y1={160 - n * 132}
        y2={160 - n * 132} stroke="#e2e8f0" strokeDasharray="4 5" />)}
      <path d={fill} fill="#dbeafe" fillOpacity="0.8" />
      <path d={line} stroke="#2563eb" strokeWidth="3" strokeLinejoin="round" fill="none" />
      {points.map(p => <circle key={p.day} cx={p.x} cy={p.y} r="5"
        fill="#2563eb" stroke="white" strokeWidth="2">
        <title>{dayLabel(p.day)}: {p.orders} заказов</title>
      </circle>)}
    </svg>
    <div className="grid grid-cols-7 gap-1 text-center text-[10px] text-slate-500 sm:text-xs">
      {days.map(d => <span key={d.day}>{dayLabel(d.day)}</span>)}
    </div>
    <p className="mt-4 text-xs text-slate-500">Дата создания заказа · время Бишкек · данные сервера</p>
  </section>;
}
function Attention({ data, deliveryRight, inventoryRight }: {
  data: DashboardOverview; deliveryRight: boolean; inventoryRight: boolean;
}) {
  if (!deliveryRight && !inventoryRight) return null;
  const alerts = [
    ...(deliveryRight && data.delayedDeliveries !== null && data.delayedDeliveries > 0
      ? [{ name: 'Просроченные доставки', count: data.delayedDeliveries,
        desc: 'Плановая дата прошла; доставка не завершена', path: '/deliveries', icon: Truck }] : []),
    ...(inventoryRight && data.lowStockVariants !== null && data.lowStockVariants > 0
      ? [{ name: 'Мало товара', count: data.lowStockVariants,
        desc: 'Вариации с доступным остатком 1–5 шт.', path: '/inventory', icon: Package }] : []),
  ];
  return <section aria-label="Требует внимания" className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
    <h3 className="mb-3 flex items-center gap-2 text-lg font-bold text-slate-900">
      <TriangleAlert className="size-5 text-amber-500" aria-hidden="true" />Требует внимания
    </h3>
    {alerts.length === 0
      ? <p className="rounded-xl bg-emerald-50 p-4 text-sm text-emerald-800">Нет проблем по доступным вам показателям.</p>
      : <div className="divide-y divide-slate-100">{alerts.map(a =>
        <Link key={a.path} to={a.path} className="flex items-center gap-3 py-4 hover:text-blue-700">
          <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-amber-50 text-amber-600">
            <a.icon className="size-5" aria-hidden="true" />
          </span>
          <span className="min-w-0 flex-1"><strong className="block text-sm">{a.name}</strong>
            <span className="text-xs text-slate-500">{a.desc}</span></span>
          <b className="rounded-full bg-amber-50 px-2 py-1 text-sm text-amber-700">{a.count}</b>
          <ArrowRight className="size-4 shrink-0" aria-hidden="true" />
        </Link>)}</div>}
  </section>;
}
function RecentSales({ items }: { items: DashboardOverview['recentSales'] }) {
  return <section aria-label="Последние заказы" className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
    <div className="flex items-center justify-between gap-2 px-5 py-4">
      <h3 className="text-lg font-bold text-slate-900">Последние заказы</h3>
      <Link to="/sales" className="inline-flex items-center gap-1 text-sm font-semibold text-blue-600 hover:underline">
        Смотреть все <ArrowRight className="size-4" aria-hidden="true" /></Link>
    </div>
    {items.length === 0 ? <p className="px-5 pb-5 text-sm text-slate-500">Заказов пока нет.</p> :
    <div className="overflow-x-auto"><table className="w-full min-w-[650px] text-left text-sm">
      <thead className="bg-slate-50 text-slate-600"><tr>
        {['№ заказа', 'Дата', 'Клиент', 'Товары', 'Сумма', 'Статус'].map(h =>
          <th key={h} scope="col" className="px-4 py-3 font-semibold">{h}</th>)}
      </tr></thead>
      <tbody className="divide-y divide-slate-100">{items.map(s =>
        <tr key={s.id} className="entity-clickable-row hover:bg-blue-50">
          <td className="px-4 py-3 font-bold text-blue-700">
            <Link to={'/sales/' + encodeURIComponent(s.id)} className="entity-stretched-link"
              title={s.saleNumber} aria-label={'Открыть продажу ' + s.saleNumber}>{displaySaleNumber(s.saleNumber)}</Link>
          </td>
          <td className="whitespace-nowrap px-4 py-3 text-slate-600">{saleDate(s.createdAt)}</td>
          <td className="px-4 py-3">{s.customerName || 'Без имени'}</td>
          <td className="max-w-64 break-words px-4 py-3 text-slate-600">{s.products || '—'}</td>
          <td className="whitespace-nowrap px-4 py-3 font-semibold">{money(s.total)}</td>
          <td className="px-4 py-3">{saleStatusNames[s.status]}</td>
        </tr>)}</tbody>
    </table></div>}
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

  return <div className="space-y-5 sm:space-y-6">
    <header className="flex flex-wrap items-center justify-between gap-4">
      <div>
        <h2 className="text-2xl font-extrabold tracking-tight text-slate-950 sm:text-3xl">
          Добро пожаловать, {name}!
        </h2>
        <p className="mt-1 text-sm text-slate-500">
          {data ? dayLabel(data.businessDate, true) : 'Рабочее пространство ChairX'}
        </p>
      </div>
      {hasPermission('SALES_CREATE') && <Button asChild className="bg-blue-600 hover:bg-blue-700">
        <Link to="/sales/new"><Plus className="size-4" aria-hidden="true" />Новая продажа</Link>
      </Button>}
    </header>
    {allowed && overview.isPending && <LoadingState label="Загружаем актуальные показатели…" />}
    {allowed && overview.isError && <ErrorState error={overview.error} onRetry={() => void overview.refetch()} />}
    {allowed && data && <>
      <section aria-label="Показатели" className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {sales && data.orders30Days !== null && <Stat title="Заказов за 30 дней"
          value={data.orders30Days} note="Создано за период" icon={ShoppingCart} path="/sales" />}
        {deliveries && data.pendingDeliveries !== null && <Stat title="Ожидают доставки"
          value={data.pendingDeliveries} note="Готовы к отправке или в пути" icon={Truck} path="/deliveries" />}
        {inventory && data.availableUnits !== null && <Stat title="Доступно на складах"
          value={data.availableUnits} note="С учётом резерва и блокировок" icon={Package} path="/inventory" />}
        {sales && data.activeOrders !== null && <Stat title="В работе"
          value={data.activeOrders} note="Черновики и подтверждённые" icon={Clock3} path="/sales" />}
      </section>
      <div className="grid items-start gap-4 xl:grid-cols-[minmax(0,1.7fr)_minmax(320px,1fr)]">
        {sales && <WeekChart days={data.ordersByDay} />}
        <Attention data={data} deliveryRight={deliveries} inventoryRight={inventory} />
      </div>
      {sales && <RecentSales items={data.recentSales} />}
    </>}
    {!allowed && <section className="rounded-2xl border border-slate-200 bg-white p-6">
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
