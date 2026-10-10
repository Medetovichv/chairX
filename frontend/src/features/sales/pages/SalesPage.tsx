import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
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

const empty:SaleFilters={status:'',from:'',to:'',number:'',page:0};
export function SalesPage() {
  const {hasPermission}=useAuth();
  const read=hasPermission('SALES_READ');
  const create=hasPermission('SALES_CREATE');
  const [view,setView]=useState<'table'|'kanban'>('table');
  const [search,setSearch]=useState('');
  const [term,setTerm]=useState('');
  const [filters,setFilters]=useState<SaleFilters>(empty);
  useEffect(()=>{
    const timeout=setTimeout(()=>setTerm(search.trim()),350);
    return ()=>clearTimeout(timeout);
  },[search]);
  const filtered={...filters,number:term};
  const valid=!(filters.from&&filters.to&&filters.from>filters.to);
  const listing=useQuery({
    queryKey:salesKeys.list(filtered),queryFn:()=>salesApi.list(filtered),
    enabled:read&&view==='table'&&valid,
  });
  const change=<K extends keyof SaleFilters>(key:K,value:SaleFilters[K])=>{
    setFilters(old=>({...old,[key]:value,page:0}));
  };
  if(!read||listing.error instanceof ApiClientError&&listing.error.status===403)return <ForbiddenPage/>;
  return <div className="space-y-6">
    <PageHeader title="Продажи" description="Все заказы: черновики, подтверждение и выдача кресел."
      action={create?<Button asChild><Link to="/sales/new">+ Новая продажа</Link></Button>:undefined}/>
    <section aria-label="Фильтры продаж" className="rounded-2xl border border-slate-200 bg-white p-4">
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <label className="space-y-1 text-sm font-semibold">Статус
          <select aria-label="Фильтр статуса продажи" value={filters.status}
            onChange={e=>change('status',e.target.value as SaleStatus|'')}
            className="min-h-11 w-full rounded-xl border border-slate-200 px-3">
            <option value="">Все</option>
            {saleStatuses.map(s=><option value={s} key={s}>{saleStatusNames[s]}</option>)}
          </select>
        </label>
        <label className="space-y-1 text-sm font-semibold">Номер заказа
          <Input type="search" aria-label="Поиск по номеру заказа" maxLength={100}
            placeholder="Например, CHX-..."
            value={search} onChange={e=>{setSearch(e.target.value);setFilters(old=>({...old,page:0}));}}/>
        </label>
        <label className="space-y-1 text-sm font-semibold">С даты
          <Input type="date" aria-label="Продажи с даты" value={filters.from}
            onChange={e=>change('from',e.target.value)}/>
        </label>
        <label className="space-y-1 text-sm font-semibold">По дату
          <Input type="date" aria-label="Продажи по дату" value={filters.to}
            onChange={e=>change('to',e.target.value)}/>
        </label>
      </div>
      <div className="mt-4 flex flex-wrap gap-2">
        <Button aria-pressed={view==='table'} variant={view==='table'?'default':'outline'}
          onClick={()=>setView('table')}>Таблица</Button>
        <Button aria-pressed={view==='kanban'} variant={view==='kanban'?'default':'outline'}
          onClick={()=>setView('kanban')}>Канбан</Button>
      </div>
      {view==='kanban'&&filters.status&&
        <p className="mt-2 text-sm text-slate-600">
          В Kanban показываются все четыре статуса. Общий фильтр статуса применяется только к таблице.
        </p>}
    </section>
    {!valid?<p role="alert" className="rounded-xl bg-amber-50 p-4 text-sm text-amber-900">
      Начальная дата не должна быть позже конечной.</p>:
      view==='kanban'?<SalesKanban filters={filtered}/>:
      listing.isPending?<LoadingState label="Загружаем заказы…"/>:
      listing.isError?<ErrorState error={listing.error} onRetry={()=>void listing.refetch()}/>:
      !listing.data?.items.length?<EmptyState title="Продажи не найдены" description="Измените фильтры или создайте черновик. Реальных записей пока нет."/>:
      <div>
        <SalesTable rows={listing.data.items}/>
        <PurchasePager page={listing.data.page} size={listing.data.size}
          total={listing.data.total} label="продаж"
          onPage={p=>setFilters(old=>({...old,page:p}))}/>
      </div>}
  </div>;
}
