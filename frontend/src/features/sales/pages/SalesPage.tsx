import { useCallback } from 'react';
import { useSearchParams } from 'react-router';
import { QuickViewDrawer } from '../../../shared/components/QuickViewDrawer';
import { SaleQuickView } from '../../../shared/components/QuickViewContent';
import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { CheckCircle2, Clock3, LayoutGrid, List, Plus, Search, ShoppingCart } from 'lucide-react';
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
import { saleStatuses, saleStatusNames } from '../validation';
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
  const [quickParams, setQuickParams] = useSearchParams();
  const selected = quickParams.get('quick');
  const goQuick = (id: string) => setQuickParams(prev => { const next = new URLSearchParams(prev); next.set('quick', id); return next; });
  const closeQuick = useCallback(() => setQuickParams(prev => {
    const next = new URLSearchParams(prev); next.delete('quick'); return next;
  }), [setQuickParams]);
  const interceptDetail = (event: React.MouseEvent<HTMLDivElement>) => {
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    const target = event.target;
    if (!(target instanceof Element)) return;
    const link = target.closest('a[href]');
    if (!link) return;
    const path = new URL(link.getAttribute('href') || '', window.location.origin).pathname;
    if (!path.startsWith('/sales/')) return;
    const id = path.slice('/sales/'.length);
    if (!id || id.includes('/')) return;
    event.preventDefault();
    setQuickParams(prev => { const next = new URLSearchParams(prev); next.set('quick', decodeURIComponent(id)); return next; });
  };

  const read = hasPermission('SALES_READ');
  const create = hasPermission('SALES_CREATE');
  // Table is retained as the default for existing operational workflows.
  // Staff can switch to the visual Kanban without losing their filters.
  const [view, setView] = useState<View>('table');
  const [history,setHistory]=useState(false);
  const [search, setSearch] = useState('');
  const [term, setTerm] = useState('');
  const [filters, setFilters] = useState<SaleFilters>(empty);
  useEffect(() => {
    const timeout = setTimeout(() => setTerm(search.trim()), 350);
    return () => clearTimeout(timeout);
  }, [search]);
  const filtered = { ...filters, number: term, operational:!history };
  const valid = !(filters.from && filters.to && filters.from > filters.to);

  const drafts=useQuery({queryKey:['sales','metric','drafts'],
    queryFn:()=>salesApi.list({status:'DRAFT',from:'',to:'',number:'',page:0,operational:true}),
    enabled:read,staleTime:30_000});
  const confirmed=useQuery({queryKey:['sales','metric','confirmed'],
    queryFn:()=>salesApi.list({status:'CONFIRMED',from:'',to:'',number:'',page:0,operational:true}),
    enabled:read,staleTime:30_000});
  const completed=useQuery({queryKey:['sales','metric','completed'],
    queryFn:()=>salesApi.list({status:'FULFILLED',from:'',to:'',number:'',page:0,operational:true}),
    enabled:read,staleTime:30_000});
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

  if (!read || listing.error instanceof ApiClientError && listing.error.status === 403) {
    return <ForbiddenPage />;
  }

  const visibleIds = (listing.data?.items ?? []).map(record => record.id);
  const selectedIndex = visibleIds.indexOf(selected ?? '');
  return <div className="flex min-w-0 items-start gap-4"><div onClickCapture={interceptDetail} className="space-y-5 sm:space-y-6 min-w-0 flex-1">
    <PageHeader title="Продажи" description="Заказы, резервирование товара, выдача и доставка."
      action={create ? <Button asChild className="bg-blue-600 hover:bg-blue-700">
        <Link to="/sales/new"><Plus className="size-4" aria-hidden="true" />Новая продажа</Link>
      </Button> : undefined}/>

    {!history&&<section aria-label="Текущие показатели продаж" className="grid gap-3 sm:grid-cols-3">
      {([
        {title:'Черновики в работе',value:drafts.data?.total,icon:ShoppingCart},
        {title:'Ожидают выполнения',value:confirmed.data?.total,icon:Clock3},
        {title:'Выданы сегодня или ещё не закрыты',value:completed.data?.total,icon:CheckCircle2},
      ]).map(m=><article key={m.title} className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <m.icon className="mb-2 size-5 text-blue-600" aria-hidden="true"/>
        <p className="text-xs font-semibold text-slate-600">{m.title}</p>
        <p className="mt-1 text-3xl font-bold">{m.value===undefined?'—':m.value}</p>
      </article>)}
    </section>}
    <div className="flex flex-wrap gap-3">
      <Button aria-pressed={!history} variant={!history?'default':'outline'}
        onClick={()=>{setHistory(false);setFilters(empty);}}>Актуальные заказы</Button>
      <Button aria-pressed={history} variant={history?'default':'outline'}
        onClick={()=>{setHistory(true);setFilters({...empty,...dayRange(7)});}}>История продаж</Button>
    </div>
    <section aria-label="Фильтры продаж" className="space-y-4 rounded-2xl border border-slate-200 bg-white p-3 shadow-sm sm:p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h3 className="text-base font-bold text-slate-900">{history?'История продаж':'Сегодня и незавершённые заказы'}</h3>
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
      {history&&<div className="flex flex-wrap items-center gap-2">
        {[
          { name: 'Сегодня', days: 1 },
          { name: '7 дней', days: 7 },
          { name: '30 дней', days: 30 },
        ].map(p => <Button key={p.days} type="button" variant="outline" size="sm"
          onClick={() => preset(p.days)}>{p.name}</Button>)}
        <Button type="button" variant="ghost" size="sm" onClick={() => {
          setSearch(''); setTerm(''); setFilters(empty);
        }}>Сбросить фильтры</Button>
      </div>}
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
  </div>{selected && <QuickViewDrawer title={'Продажа'} detailUrl={'/sales/' + encodeURIComponent(selected)} onClose={closeQuick} previous={selectedIndex > 0 ? () => goQuick(visibleIds[selectedIndex - 1]!) : undefined} next={selectedIndex >= 0 && selectedIndex < visibleIds.length - 1 ? () => goQuick(visibleIds[selectedIndex + 1]!) : undefined}><SaleQuickView key={selected} id={selected}/></QuickViewDrawer>}</div>;
}
