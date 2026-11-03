import { useState } from 'react';
import { ArrowUpRight, CalendarDays, ClipboardList, Plus, Search } from 'lucide-react';
import { ReturnsWorkspaceOverview } from '../components/ReturnsWorkspaceOverview';
import { Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { PageHeader } from '../../../shared/components/PageHeader';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState';
import { EmptyState } from '../../../shared/components/EmptyState';
import { PurchasePager } from '../../purchases/components/PurchasePager';
import { Input } from '../../../shared/ui/input';
import { Button } from '../../../shared/ui/button';
import { ForbiddenPage } from '../../../pages/ForbiddenPage';
import { ApiClientError } from '../../../shared/api/api-error';
import { inventoryApi, inventoryKeys } from '../../inventory/api';
import { displaySaleNumber } from '../../../shared/lib/format';
import { ReturnsTabs } from '../components/ReturnsTabs';
import { returnsApi, returnKeys } from '../api';
import { dateLabel } from '../validation';
import type { ReturnSummary } from '../types';

export function ReturnsPage() {
  const {hasPermission}=useAuth();
  const read=hasPermission('RETURNS_READ'), create=hasPermission('RETURNS_CREATE');
  const salesRead=hasPermission('SALES_READ'), inventoryRead=hasPermission('INVENTORY_READ');
  const [typed,setTyped]=useState(''),[filter,setFilter]=useState(''),[page,setPage]=useState(0);
  const q=useQuery({
    queryKey:returnKeys.list('',page,filter),
    queryFn:()=>returnsApi.list('',page,filter),
    enabled:read,
  });
  const warehouses=useQuery({
    queryKey:inventoryKeys.warehouses,
    queryFn:inventoryApi.warehouses,
    enabled:read&&inventoryRead,
  });
  const warehouseName=(id:string)=>!inventoryRead?'Данные склада недоступны':
    warehouses.data?.find(w=>w.id===id)?.name??'Название склада недоступно';
  const saleLabel=(r:ReturnSummary)=>{
    const label=r.saleNumber?displaySaleNumber(r.saleNumber):'Номер заказа недоступен';
    return salesRead?<Link to={'/sales/'+encodeURIComponent(r.saleId)}
      className="font-semibold text-[#236e66] underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-[#267f79]">
      {label}</Link>:<strong>{label}</strong>;
  };
  const returnLink=(r:ReturnSummary)=><Link to={'/returns/'+encodeURIComponent(r.id)}
    className="font-semibold text-[#416c82] underline-offset-2 hover:underline">
    Открыть возврат</Link>;
  if(!read||(q.error instanceof ApiClientError&&q.error.status===403))return <ForbiddenPage/>;
  return <div className="space-y-5 text-[#182d38] sm:space-y-6">
    <PageHeader title="Возвраты" description="История возвратов, причины и связь с исходными заказами."
      action={create?<Button asChild className="min-h-11 rounded-xl bg-[#172f3c] text-white hover:bg-[#305063]"><Link to="/returns/new"><Plus className="size-4" aria-hidden="true"/>Оформить возврат</Link></Button>:undefined}/>
    <ReturnsTabs/>
    {q.isSuccess && <ReturnsWorkspaceOverview rows={q.data.items} total={q.data.total} page={q.data.page} filtered={Boolean(filter)}/>}
    <form aria-label="Фильтр возвратов"
      onSubmit={e=>{e.preventDefault();setFilter(typed.trim());setPage(0);}}
      className="flex flex-wrap items-end gap-3 rounded-2xl border border-[#dfe7eb] bg-white p-4 sm:p-5">
      <label className="min-w-48 flex-1 space-y-2 text-sm font-semibold"><span className="inline-flex items-center gap-2"><Search className="size-4 text-[#267f79]" aria-hidden="true"/>Номер заказа</span>
        <Input type="search" aria-label="Поиск по номеру заказа" maxLength={100}
          placeholder="Например, #1258 или CHX-101" value={typed}
          onChange={e=>setTyped(e.target.value)}/>
      </label>
      <Button className="min-h-11 rounded-xl bg-[#172f3c] text-white" type="submit">Найти</Button>
      <Button className="min-h-11 rounded-xl" type="button" variant="outline"
        onClick={()=>{setTyped('');setFilter('');setPage(0);}}>Сбросить</Button>
    </form>
    {q.isPending?<LoadingState label="Загружаем возвраты…"/>:
      q.isError?<ErrorState error={q.error} onRetry={()=>void q.refetch()}/>:
      !q.data.items.length?<EmptyState title="Возвратов не найдено"
        description="Попробуйте другой номер заказа или сбросьте фильтр."/>:
      <section aria-label="Список возвратов" className="space-y-3">
        <div className="hidden overflow-x-auto rounded-2xl border border-[#dfe7eb] bg-white lg:block">
          <table className="w-full text-left text-sm">
            <thead className="bg-[#f5f9f7] text-[#516977]">
              <tr>{['Возврат','Продажа','Склад возврата','Дата','Причина','Сотрудник'].map(label=>
                <th key={label} className="p-4 text-xs font-bold">{label}</th>)}</tr>
            </thead>
            <tbody>{q.data.items.map(r=><tr key={r.id} className="border-t border-[#eaf0f0] transition-colors hover:bg-[#f5faf8]">
              <td className="p-4">{returnLink(r)}</td>
              <td className="p-4">{saleLabel(r)}</td>
              <td className="p-4">{warehouseName(r.warehouseId)}</td>
              <td className="whitespace-nowrap p-4">{dateLabel(r.createdAt)}</td>
              <td className="max-w-xs break-words p-4">{r.reason}</td>
              <td className="p-4">{r.createdBy}</td>
            </tr>)}</tbody>
          </table>
        </div>
        <div className="space-y-3 lg:hidden">{q.data.items.map(r=>
          <article key={r.id} className="space-y-4 rounded-2xl border border-[#dfe7eb] bg-white p-4 text-sm sm:p-5">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p className="flex items-center gap-1.5 text-xs font-semibold text-[#516977]">
                  <CalendarDays className="size-4" aria-hidden="true"/>Возврат от {dateLabel(r.createdAt)}
                </p>
                <p className="mt-2 font-bold text-[#182d38]">Продажа: {saleLabel(r)}</p>
              </div>
              <Link className="inline-flex min-h-11 items-center gap-1.5 font-semibold text-[#236e66] underline-offset-2 hover:underline"
                to={'/returns/'+encodeURIComponent(r.id)}>
                Открыть возврат<ArrowUpRight className="size-4" aria-hidden="true"/>
              </Link>
            </div>
            <div className="rounded-xl bg-[#f2f7f6] p-4">
              <p className="flex items-center gap-2 text-xs font-semibold text-[#516977]">
                <ClipboardList className="size-4" aria-hidden="true"/>Причина возврата
              </p>
              <p className="mt-2 break-words font-medium text-[#263e49]">{r.reason}</p>
            </div>
            <div className="flex flex-wrap items-center justify-between gap-3 text-xs text-[#516977]">
              <span>Склад: {warehouseName(r.warehouseId)}</span>
              <span>Оформил: {r.createdBy}</span>
            </div>
          </article>)}</div>
        <PurchasePager page={q.data.page} size={q.data.size} total={q.data.total}
          onPage={setPage} label="возвратов"/>
      </section>}
  </div>;
}
