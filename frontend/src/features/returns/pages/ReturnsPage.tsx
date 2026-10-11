import { useState } from 'react';
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
      className="font-semibold text-blue-700 underline-offset-2 hover:underline">
      {label}</Link>:<strong>{label}</strong>;
  };
  const returnLink=(r:ReturnSummary)=><Link to={'/returns/'+encodeURIComponent(r.id)}
    className="font-semibold text-blue-700 underline-offset-2 hover:underline">
    Открыть возврат</Link>;
  if(!read||(q.error instanceof ApiClientError&&q.error.status===403))return <ForbiddenPage/>;
  return <div className="space-y-5">
    <PageHeader title="Возвраты"
      action={create?<Button asChild><Link to="/returns/new">+ Оформить возврат</Link></Button>:undefined}/>
    <ReturnsTabs/>
    <form aria-label="Фильтр возвратов"
      onSubmit={e=>{e.preventDefault();setFilter(typed.trim());setPage(0);}}
      className="flex flex-wrap items-end gap-3 rounded-2xl border border-slate-200 bg-white p-4">
      <label className="min-w-48 flex-1 space-y-2 text-sm font-semibold">Номер заказа
        <Input type="search" aria-label="Поиск по номеру заказа" maxLength={100}
          placeholder="Например, #1258 или CHX-101" value={typed}
          onChange={e=>setTyped(e.target.value)}/>
      </label>
      <Button type="submit">Найти</Button>
      <Button type="button" variant="outline"
        onClick={()=>{setTyped('');setFilter('');setPage(0);}}>Сбросить</Button>
    </form>
    {q.isPending?<LoadingState label="Загружаем возвраты…"/>:
      q.isError?<ErrorState error={q.error} onRetry={()=>void q.refetch()}/>:
      !q.data.items.length?<EmptyState title="Возвратов не найдено"
        description="Попробуйте другой номер заказа или сбросьте фильтр."/>:
      <section aria-label="Список возвратов" className="space-y-3">
        <div className="hidden overflow-x-auto rounded-2xl border border-slate-200 bg-white lg:block">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-600">
              <tr>{['Возврат','Продажа','Склад возврата','Дата','Причина','Сотрудник'].map(label=>
                <th key={label} className="p-4 font-semibold">{label}</th>)}</tr>
            </thead>
            <tbody>{q.data.items.map(r=><tr key={r.id} className="border-t border-slate-100">
              <td className="p-4">{returnLink(r)}</td>
              <td className="p-4">{saleLabel(r)}</td>
              <td className="p-4">{warehouseName(r.warehouseId)}</td>
              <td className="whitespace-nowrap p-4">{dateLabel(r.createdAt)}</td>
              <td className="max-w-xs break-words p-4">{r.reason}</td>
              <td className="p-4">{r.createdBy}</td>
            </tr>)}</tbody>
          </table>
        </div>
        <div className="space-y-3 lg:hidden">{q.data.items.map(r=><article key={r.id}
          className="space-y-3 rounded-2xl border border-slate-200 bg-white p-4 text-sm">
          <div className="flex items-center justify-between gap-3">
            <strong className="text-slate-950">Возврат · {dateLabel(r.createdAt)}</strong>
            {returnLink(r)}
          </div>
          <p>Продажа: {saleLabel(r)}</p>
          <p>Склад возврата: {warehouseName(r.warehouseId)}</p>
          <p className="break-words">{r.reason}</p>
          <p className="text-xs text-slate-500">Сотрудник: {r.createdBy}</p>
        </article>)}</div>
        <PurchasePager page={q.data.page} size={q.data.size} total={q.data.total}
          onPage={setPage} label="возвратов"/>
      </section>}
  </div>;
}
