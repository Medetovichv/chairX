import { useState } from 'react';
import { ArrowUpRight, CalendarDays, ClipboardList, Plus, Search } from 'lucide-react';
import { DefectsWorkspaceOverview } from '../components/DefectsWorkspaceOverview';
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
import { ReturnChairLabel } from '../components/ReturnChairLabel';
import { DefectWarehouseLabel } from '../components/DefectWarehouseLabel';
export function DefectsPage(){
 const {hasPermission}=useAuth(),read=hasPermission('DEFECTS_READ'),manage=hasPermission('DEFECTS_MANAGE');
 const [status,setStatus]=useState<DefectStatus|''>(''),[page,setPage]=useState(0),[creating,setCreating]=useState(false);
 const q=useQuery({queryKey:returnKeys.defectList(status,page),queryFn:()=>defectsApi.list(status,page),enabled:read&&!creating});
 if((!read&&!manage)||(read&&q.error instanceof ApiClientError&&q.error.status===403))return <ForbiddenPage/>;
 return <div className="space-y-5">
  <PageHeader description="Учет неисправных товаров, ремонт, запчасти и списание." title={creating?'Регистрация брака':'Брак'}
   action={manage&&!creating?<Button className="min-h-11 rounded-xl bg-[#172f3c] text-white hover:bg-[#305063]" onClick={()=>setCreating(true)}><Plus className="size-4" aria-hidden="true"/>Зарегистрировать брак</Button>:undefined}/>
  <ReturnsTabs/>
  {read&&!creating&&q.isSuccess&&<DefectsWorkspaceOverview rows={q.data.items} total={q.data.total} page={q.data.page} status={status} onStatus={value=>{setStatus(value);setPage(0);}}/>}
  {creating&&manage&&<div className="space-y-3">
    <Button variant="outline" type="button" onClick={()=>setCreating(false)}>← К списку дефектов</Button>
    <DefectForm onCreated={()=>{setCreating(false);void q.refetch();}}/>
   </div>}
  {read&&!creating&&<label className="block space-y-2 rounded-2xl border border-[#dfe7eb] bg-white p-4 text-sm font-semibold"><span className="inline-flex items-center gap-2"><Search className="size-4 text-[#286e66]" aria-hidden="true"/>Статус дефекта</span>
   <select aria-label="Фильтр статуса брака" className="min-h-11 w-full rounded-xl border border-[#dce6eb] bg-[#f8fafb] px-3 text-[#182d38] focus-visible:outline-2 focus-visible:outline-[#267f79]"
    value={status} onChange={e=>{setStatus(e.target.value as DefectStatus|'');setPage(0);}}>
    <option value="">Все</option>
    {defectStatuses.map(s=><option key={s} value={s}>{defectLabels[s]}</option>)}
   </select>
  </label>}
  {!read&&!creating&&<p className="rounded-xl bg-amber-50 p-3 text-sm">Для списка и карточек дефектов требуется DEFECTS_READ. Регистрация доступна с DEFECTS_MANAGE.</p>}
  {read&&!creating&&(q.isPending?<LoadingState label="Загружаем брак…"/>:
   q.isError?<ErrorState error={q.error} onRetry={()=>void q.refetch()}/>:
   !q.data.items.length?<EmptyState title="Дефектов не найдено" description="Попробуйте другой статус или зарегистрируйте дефект."/>:
   <section aria-label="Список дефектов" className="space-y-3">
    <div className="hidden overflow-x-auto rounded-2xl border border-[#dfe7eb] bg-white lg:block">
      <table className="w-full text-left text-sm"><thead className="bg-[#f5f9f7] text-[#516977]"><tr>{['Вариация','Склад','Кол-во','Статус','Описание','Дата','Сотрудник',''].map(h=><th key={h} className="p-4 text-xs font-bold">{h}</th>)}</tr></thead>
        <tbody>{q.data.items.map(d=><tr key={d.id} className="border-t border-[#eaf0f0] transition-colors hover:bg-[#f5faf8]">
        <td className="p-3"><ReturnChairLabel variantId={d.productVariantId}/></td>
        <td className="p-3"><DefectWarehouseLabel warehouseId={d.warehouseId}/></td>
        <td className="p-3">{d.quantity}</td><td className="p-3">{defectLabels[d.status]}</td>
        <td className="p-3">{d.description}</td><td className="p-3">{dateLabel(d.createdAt)}</td>
        <td className="p-3">{d.createdBy}</td><td className="p-3"><Link className="font-semibold text-[#236e66] underline-offset-2 hover:underline" to={'/returns/defects/'+d.id}>Открыть</Link></td>
       </tr>)}</tbody></table>
    </div>
    <div className="space-y-3 lg:hidden">{q.data.items.map(d=>
      <article key={d.id} className="space-y-4 rounded-2xl border border-[#dfe7eb] bg-white p-4 text-sm sm:p-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="break-words text-base font-bold text-[#182d38]">Кресло: <ReturnChairLabel variantId={d.productVariantId}/></p>
            <p className="mt-1 flex items-center gap-1.5 text-xs text-[#516977]">
              <CalendarDays className="size-4" aria-hidden="true"/>{dateLabel(d.createdAt)}
            </p>
          </div>
          <span className="rounded-lg bg-[#e6efed] px-3 py-2 text-xs font-semibold text-[#285f58]">{defectLabels[d.status]}</span>
        </div>
        <div className="rounded-xl bg-[#f2f7f6] p-4">
          <p className="flex items-center gap-2 text-xs font-semibold text-[#516977]">
            <ClipboardList className="size-4" aria-hidden="true"/>Описание
          </p>
          <p className="mt-2 whitespace-pre-wrap break-words text-[#263e49]">{d.description}</p>
        </div>
        <div className="flex flex-wrap gap-x-4 gap-y-2 text-xs text-[#516977]">
          <span>Склад: <DefectWarehouseLabel warehouseId={d.warehouseId}/></span>
          <span>Количество: {d.quantity} шт.</span>
          <span>Сотрудник: {d.createdBy}</span>
        </div>
        <Link className="inline-flex min-h-11 items-center gap-1.5 font-semibold text-[#236e66] underline-offset-2 hover:underline"
          to={'/returns/defects/'+encodeURIComponent(d.id)}>
          Открыть<ArrowUpRight className="size-4" aria-hidden="true"/>
        </Link>
      </article>)}</div>
    <PurchasePager page={q.data.page} size={q.data.size} total={q.data.total} label="дефектов" onPage={setPage}/>
   </section>)}
 </div>;
}
