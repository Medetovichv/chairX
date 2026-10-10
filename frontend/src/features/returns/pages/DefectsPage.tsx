import { useState } from 'react';
import { Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { PageHeader } from '../../../shared/components/PageHeader';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState';
import { EmptyState } from '../../../shared/components/EmptyState';
import { PurchasePager } from '../../purchases/components/PurchasePager';
import { ForbiddenPage } from '../../../pages/ForbiddenPage';
import { ApiClientError } from '../../../shared/api/api-error';
import { Button } from '../../../shared/ui/button';
import { ReturnsTabs } from '../components/ReturnsTabs';
import { defectsApi,returnKeys } from '../api';
import { dateLabel,defectLabels,defectStatuses } from '../validation';
import type { DefectStatus } from '../types';
import { DefectForm } from '../components/DefectForm';
export function DefectsPage(){
 const {hasPermission}=useAuth(),read=hasPermission('DEFECTS_READ'),manage=hasPermission('DEFECTS_MANAGE');
 const [status,setStatus]=useState<DefectStatus|''>(''),[page,setPage]=useState(0),[creating,setCreating]=useState(false);
 const q=useQuery({queryKey:returnKeys.defectList(status,page),queryFn:()=>defectsApi.list(status,page),enabled:read});
 if((!read&&!manage)||(read&&q.error instanceof ApiClientError&&q.error.status===403))return <ForbiddenPage/>;
 return <div className="space-y-5">
  <PageHeader title="Брак" description="Регистрация и обработка неисправных кресел."
   action={manage?<Button onClick={()=>setCreating(x=>!x)}>{creating?'Закрыть форму':'+ Зарегистрировать брак'}</Button>:undefined}/>
  <ReturnsTabs/>
  {creating&&manage&&<DefectForm onCreated={()=>{setCreating(false);void q.refetch();}}/>}
  {read&&<label className="block space-y-1 text-sm font-semibold">Статус дефекта
   <select aria-label="Фильтр статуса брака" className="min-h-11 w-full rounded-xl border bg-white p-2"
    value={status} onChange={e=>{setStatus(e.target.value as DefectStatus|'');setPage(0);}}>
    <option value="">Все</option>
    {defectStatuses.map(s=><option key={s} value={s}>{defectLabels[s]}</option>)}
   </select>
  </label>}
  {!read&&<p className="rounded-xl bg-amber-50 p-3 text-sm">Для списка и карточек дефектов требуется DEFECTS_READ. Регистрация доступна с DEFECTS_MANAGE.</p>}
  {read&&(q.isPending?<LoadingState label="Загружаем брак…"/>:
   q.isError?<ErrorState error={q.error} onRetry={()=>void q.refetch()}/>:
   !q.data.items.length?<EmptyState title="Дефектов не найдено" description="Попробуйте другой статус или зарегистрируйте дефект."/>:
   <section aria-label="Список дефектов" className="space-y-3">
    <div className="hidden overflow-x-auto rounded-xl border bg-white lg:block">
      <table className="w-full text-left text-sm"><thead><tr>{['Вариация','Склад','Кол-во','Статус','Описание','Дата','Сотрудник',''].map(h=><th key={h} className="p-3">{h}</th>)}</tr></thead>
        <tbody>{q.data.items.map(d=><tr key={d.id} className="border-t">
        <td className="break-all p-3">{d.productVariantId}</td><td className="break-all p-3">{d.warehouseId}</td>
        <td className="p-3">{d.quantity}</td><td className="p-3">{defectLabels[d.status]}</td>
        <td className="p-3">{d.description}</td><td className="p-3">{dateLabel(d.createdAt)}</td>
        <td className="p-3">{d.createdBy}</td><td className="p-3"><Link className="font-semibold text-indigo-700 underline" to={'/returns/defects/'+d.id}>Открыть</Link></td>
       </tr>)}</tbody></table>
    </div>
    <div className="space-y-3 lg:hidden">{q.data.items.map(d=><article key={d.id} className="space-y-2 rounded-xl border bg-white p-4 text-sm">
      <p className="break-all">Вариация: {d.productVariantId} · Склад: {d.warehouseId}</p>
      <p>{d.quantity} шт. · {defectLabels[d.status]}</p>
      <p>{d.description}</p><p>{dateLabel(d.createdAt)} · {d.createdBy}</p>
      <Link className="font-semibold text-indigo-700 underline" to={'/returns/defects/'+d.id}>Открыть</Link>
    </article>)}</div>
    <PurchasePager page={q.data.page} size={q.data.size} total={q.data.total} label="дефектов" onPage={setPage}/>
   </section>)}
 </div>;
}
