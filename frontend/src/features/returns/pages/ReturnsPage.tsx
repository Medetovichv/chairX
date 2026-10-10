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
import { ReturnsTabs } from '../components/ReturnsTabs';
import { returnsApi,returnKeys } from '../api';
import { dateLabel,uuid } from '../validation';
export function ReturnsPage(){
 const {hasPermission}=useAuth();
 const read=hasPermission('RETURNS_READ'),create=hasPermission('RETURNS_CREATE');
 const [typed,setTyped]=useState(''),[filter,setFilter]=useState(''),[page,setPage]=useState(0);
 const valid=!typed||uuid(typed);
 const q=useQuery({queryKey:returnKeys.list(filter,page),
   queryFn:()=>returnsApi.list(filter,page),enabled:read});
 if(!read||(q.error instanceof ApiClientError&&q.error.status===403))return <ForbiddenPage/>;
 return <div className="space-y-5">
   <PageHeader title="Возвраты" description="Возвраты выполненных продаж. Данные со склада поступают только из backend."
     action={create?<Button asChild><Link to="/returns/new">+ Оформить возврат</Link></Button>:undefined}/>
   <ReturnsTabs/>
   <form onSubmit={e=>{e.preventDefault();if(!valid)return;setFilter(typed);setPage(0);}}
     aria-label="Фильтр возвратов" className="flex flex-wrap items-end gap-3 rounded-xl bg-white p-4">
     <label className="space-y-2 text-sm font-semibold">UUID продажи
       <Input aria-label="Фильтр по UUID продажи" placeholder="UUID или пусто — все возвраты"
         value={typed} onChange={e=>setTyped(e.target.value.trim())}/>
     </label>
     <Button type="submit" disabled={!valid}>Применить</Button>
     <Button type="button" variant="outline" onClick={()=>{setTyped('');setFilter('');setPage(0);}}>Сбросить</Button>
     {!valid&&<p role="alert" className="text-sm text-red-700">Введите корректный UUID.</p>}
   </form>
   {q.isPending?<LoadingState label="Загружаем возвраты…"/>:
   q.isError?<ErrorState error={q.error} onRetry={()=>void q.refetch()}/>:
   !q.data.items.length?<EmptyState title="Возвратов не найдено" description="Пока нет возвратов с такими параметрами."/>:
   <section aria-label="Список возвратов" className="space-y-3">
     <div className="hidden overflow-x-auto rounded-xl border bg-white lg:block">
       <table className="w-full text-left text-sm"><thead><tr>{['Возврат','Продажа','Склад','Дата','Причина','Сотрудник',''].map(x=>
         <th key={x} className="p-3">{x}</th>)}</tr></thead><tbody>{q.data.items.map(r=>
         <tr key={r.id} className="border-t">
          <td className="break-all p-3">{r.id}</td><td className="break-all p-3">{r.saleId}</td>
          <td className="break-all p-3">{r.warehouseId}</td><td className="p-3">{dateLabel(r.createdAt)}</td>
          <td className="p-3">{r.reason}</td><td className="p-3">{r.createdBy}</td>
          <td className="p-3"><Link className="font-semibold text-indigo-700 underline" to={'/returns/'+r.id}>Открыть</Link></td>
         </tr>)}</tbody></table>
     </div>
     <div className="space-y-3 lg:hidden">{q.data.items.map(r=><article key={r.id}
       className="space-y-2 rounded-xl border bg-white p-4 text-sm">
       <p className="break-all font-semibold">{r.id}</p><p>Продажа: {r.saleId}</p>
       <p>Склад: {r.warehouseId}</p><p>{dateLabel(r.createdAt)} · {r.createdBy}</p>
       <p>{r.reason}</p><Link className="font-semibold text-indigo-700 underline" to={'/returns/'+r.id}>Открыть</Link>
     </article>)}</div>
     <PurchasePager page={q.data.page} size={q.data.size} total={q.data.total} onPage={setPage} label="возвратов"/>
   </section>}
 </div>;
}
