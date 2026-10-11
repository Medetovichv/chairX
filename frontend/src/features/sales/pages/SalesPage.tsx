import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { CheckCircle2, Clock3, LayoutGrid, List, Plus, Search, ShoppingCart, Wallet } from 'lucide-react';
import { useAuth } from '../../auth/AuthProvider';
import { ApiClientError } from '../../../shared/api/api-error';
import { PageHeader } from '../../../shared/components/PageHeader';
import { EmptyState } from '../../../shared/components/EmptyState';
import { ErrorState } from '../../../shared/components/ErrorState';
import { LoadingState } from '../../../shared/components/LoadingState';
import { Button } from '../../../shared/ui/button';
import { Input } from '../../../shared/ui/input';
import { PurchasePager } from '../../purchases/components/PurchasePager';
import { ForbiddenPage } from '../../../pages/ForbiddenPage';
import { SalesTable } from '../components/SalesTable';
import { SalesKanban } from '../components/SalesKanban';
import { money, saleStatuses, saleStatusNames } from '../validation';
import { saleStatisticsApi, saleStatisticsKeys } from '../statistics';
import { salesApi, salesKeys } from '../api';
import type { SaleFilters, SaleStatus } from '../types';

const empty: SaleFilters = { status: '', from: '', to: '', number: '', page: 0 };
type View = 'table' | 'kanban';

/** Pure Bishkek calendar calculation: independent of browser timezone. */
function dayRange(days: number): { from: string; to: string } {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Bishkek', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(new Date());
  const fields = Object.fromEntries(parts.map(p => [p.type, p.value]));
  const y = Number(fields.year), m = Number(fields.month), d = Number(fields.day);
  const iso = (date: Date) => date.toISOString().slice(0, 10);
  return {
    from: iso(new Date(Date.UTC(y, m - 1, d - (days - 1), 12))),
    to: iso(new Date(Date.UTC(y, m - 1, d, 12))),
  };
}

export function SalesPage() {
  const { hasPermission } = useAuth();
  const read = hasPermission('SALES_READ');
  const create = hasPermission('SALES_CREATE');
  // Table is retained as the default for existing operational workflows.
  // Staff can switch to the visual Kanban without losing their filters.
  const [view, setView] = useState<View>('table');
  const [search, setSearch] = useState('');
  const [term, setTerm] = useState('');
  const [filters, setFilters] = useState<SaleFilters>(empty);
  useEffect(() => {
    const timeout = setTimeout(() => setTerm(search.trim()), 350);
    return () => clearTimeout(timeout);
  }, [search]);
  const filtered = { ...filters, number: term };
  const valid = !(filters.from && filters.to && filters.from > filters.to);

  const statistics = useQuery({
    queryKey: saleStatisticsKeys.last30Days,
    queryFn: saleStatisticsApi.last30Days,
    enabled: read,
    staleTime: 30_000,
  });
  const listing = useQuery({
    queryKey: salesKeys.list(filtered),
    queryFn: () => salesApi.list(filtered),
    enabled: read && view === 'table' && valid,
  });
  const change = <K extends keyof SaleFilters>(key: K, value: SaleFilters[K]) => {
    setFilters(old => ({ ...old, [key]: value, page: 0 }));
  };
  const preset = (days: number) => {
    const range = dayRange(days);
    setFilters(old => ({ ...old, ...range, page: 0 }));
  };

  if (!read || listing.error instanceof ApiClientError && listing.error.status === 403 ||
      statistics.error instanceof ApiClientError && statistics.error.status === 403) {
    return <ForbiddenPage />;
  }

  return <div className="space-y-5 sm:space-y-6">
    <PageHeader title="Продажи" description="Заказы, резервирование товара, выдача и доставка."
      action={create ? <Button asChild className="bg-blue-600 hover:bg-blue-700">
        <Link to="/sales/new"><Plus className="size-4" aria-hidden="true" />Новая продажа</Link>
      </Button> : undefined}/>

    {statistics.data && <section aria-label="Показатели продаж за 30 дней"
      className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
      {[
        { title: 'Заказов за 30 дней', value: statistics.data.orders30Days.toLocaleString('ru-RU'),
          note: 'Все статусы за период', icon: ShoppingCart, color: 'blue' },
        { title: 'Средний чек', value: money(statistics.data.averageCheck30Days),
          note: 'Подтверждённые и выполненные', icon: Wallet, color: 'blue' },
        { title: 'Выполнено', value: statistics.data.fulfilled30Days.toLocaleString('ru-RU'),
          note: 'За последние 30 дней', icon: CheckCircle2, color: 'emerald' },
        { title: 'В работе', value: statistics.data.inWork30Days.toLocaleString('ru-RU'),
          note: 'Черновики и подтверждённые', icon: Clock3, color: 'blue' },
      ].map(s => <article key={s.title} className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm sm:p-5">
        <div className="flex items-center gap-3">
          <div className={'flex size-11 shrink-0 items-center justify-center rounded-xl ' +
            (s.color === 'emerald' ? 'bg-emerald-50 text-emerald-600' : 'bg-blue-50 text-blue-600')}>
            <s.icon className="size-5" aria-hidden="true" />
          </div>
          <div className="min-w-0">
            <p className="text-xs font-medium text-slate-600 sm:text-sm">{s.title}</p>
            <p className="mt-1 break-words text-2xl font-extrabold text-slate-950">{s.value}</p>
          </div>
        </div>
        <p className="mt-3 text-xs text-slate-500">{s.note}</p>
      </article>)}
    </section>}
    {statistics.isError && !(statistics.error instanceof ApiClientError && statistics.error.status === 403) &&
      <div className="text-sm text-slate-600" role="status">
        Показатели пока недоступны. Списки заказов продолжают работать.
        <Button type="button" variant="ghost" size="sm" onClick={() => void statistics.refetch()}>
          Повторить загрузку</Button>
      </div>}

    <section aria-label="Фильтры продаж" className="space-y-4 rounded-2xl border border-slate-200 bg-white p-3 shadow-sm sm:p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h3 className="text-base font-bold text-slate-900">Все заказы</h3>
        <div className="flex items-center gap-1 rounded-xl border border-slate-200 bg-slate-50 p-1" aria-label="Режим отображения продаж">
          <Button size="sm" aria-pressed={view === 'kanban'}
            variant={view === 'kanban' ? 'default' : 'ghost'}
            className={view === 'kanban' ? 'bg-blue-600 hover:bg-blue-700' : ''}
            onClick={() => setView('kanban')}><LayoutGrid className="size-4" aria-hidden="true" />Канбан</Button>
          <Button size="sm" aria-pressed={view === 'table'}
            variant={view === 'table' ? 'default' : 'ghost'}
            className={view === 'table' ? 'bg-blue-600 hover:bg-blue-700' : ''}
            onClick={() => setView('table')}><List className="size-4" aria-hidden="true" />Таблица</Button>
        </div>
      </div>
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-[minmax(200px,1fr)_minmax(160px,0.65fr)_minmax(155px,0.6fr)_minmax(155px,0.6fr)]">
        <label className="space-y-1 text-sm font-semibold text-slate-700">
          <span className="inline-flex items-center gap-2"><Search className="size-4" aria-hidden="true" />Поиск заказа</span>
          <Input type="search" aria-label="Поиск по номеру заказа" maxLength={100}
            placeholder="По номеру заказа, например CHX-..."
            value={search} onChange={e => { setSearch(e.target.value); setFilters(old => ({ ...old, page: 0 })); }}/>
        </label>
        <label className="space-y-1 text-sm font-semibold text-slate-700">Статус
          <select aria-label="Фильтр статуса продажи" value={filters.status}
            onChange={e => change('status', e.target.value as SaleStatus | '')}
            className="min-h-11 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm">
            <option value="">Все статусы</option>
            {saleStatuses.map(s => <option value={s} key={s}>{saleStatusNames[s]}</option>)}
          </select>
        </label>
        <label className="space-y-1 text-sm font-semibold text-slate-700">С даты
          <Input type="date" aria-label="Продажи с даты" value={filters.from}
            onChange={e => change('from', e.target.value)}/>
        </label>
        <label className="space-y-1 text-sm font-semibold text-slate-700">По дату
          <Input type="date" aria-label="Продажи по дату" value={filters.to}
            onChange={e => change('to', e.target.value)}/>
        </label>
      </div>
      <div className="flex flex-wrap items-center gap-2">
        {[
          { name: 'Сегодня', days: 1 },
          { name: '7 дней', days: 7 },
          { name: '30 дней', days: 30 },
        ].map(p => <Button key={p.days} type="button" variant="outline" size="sm"
          onClick={() => preset(p.days)}>{p.name}</Button>)}
        <Button type="button" variant="ghost" size="sm" onClick={() => {
          setSearch(''); setTerm(''); setFilters(empty);
        }}>Сбросить фильтры</Button>
      </div>
      {view === 'kanban' && filters.status &&
        <p className="text-xs text-slate-500">Kanban всегда показывает все четыре статуса.
          Фильтр статуса применяется только к таблице.</p>}
    </section>
    {!valid ? <p role="alert" className="rounded-xl bg-amber-50 p-4 text-sm text-amber-900">
      Начальная дата не должна быть позже конечной.</p> :
      view === 'kanban' ? <SalesKanban filters={filtered}/> :
      listing.isPending ? <LoadingState label="Загружаем заказы…"/> :
      listing.isError ? <ErrorState error={listing.error} onRetry={() => void listing.refetch()}/> :
      !listing.data?.items.length ? <EmptyState title="Продажи не найдены"
        description="Измените фильтры или создайте черновик. Реальных записей пока нет."/> :
      <div className="space-y-3">
        <SalesTable rows={listing.data.items}/>
        <PurchasePager page={listing.data.page} size={listing.data.size}
          total={listing.data.total} label="продаж"
          onPage={p => setFilters(old => ({ ...old, page: p }))}/>
      </div>}
  </div>;
}
