import { Link,useParams } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { salesApi,salesKeys } from '../../sales/api';
import { PageHeader } from '../../../shared/components/PageHeader';
import { ErrorState } from '../../../shared/components/ErrorState';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ForbiddenPage } from '../../../pages/ForbiddenPage';
import { NotFoundPage } from '../../../pages/NotFoundPage';
import { ApiClientError } from '../../../shared/api/api-error';
import { Button } from '../../../shared/ui/button';
import { returnsApi,returnKeys } from '../api';
import { dateLabel,conditionLabels,uuid } from '../validation';
export function ReturnDetailPage(){
 const {returnId=''}=useParams(),{hasPermission}=useAuth();
 const read=hasPermission('RETURNS_READ');
 const q=useQuery({queryKey:returnKeys.detail(returnId),queryFn:()=>returnsApi.get(returnId),
   enabled:read&&uuid(returnId)});
 const sale=useQuery({queryKey:salesKeys.detail(q.data?.saleId??''),
   queryFn:()=>salesApi.get(q.data!.saleId),enabled:hasPermission('SALES_READ')&&!!q.data});
 if(!read)return <ForbiddenPage/>;
 if(!uuid(returnId))return <NotFoundPage/>;
 if(q.isPending)return <LoadingState label="Загружаем возврат…"/>;
 if(q.isError){
   if(q.error instanceof ApiClientError&&q.error.status===403)return <ForbiddenPage/>;
   if(q.error instanceof ApiClientError&&q.error.status===404)return <NotFoundPage/>;
   return <ErrorState error={q.error} onRetry={()=>void q.refetch()}/>;
 }
 const r=q.data;
 return <div className="space-y-5">
  <Link to="/returns" className="font-semibold text-indigo-700 underline">← Возвраты</Link>
  <PageHeader title="Карточка возврата" description={'Операция '+r.id}/>
  <section className="grid gap-3 rounded-xl border bg-white p-4 text-sm sm:grid-cols-2">
   <div><strong>Продажа:</strong> {hasPermission('SALES_READ')?
     <Link className="break-all text-indigo-700 underline" to={'/sales/'+r.saleId}>{sale.data?.saleNumber??r.saleId}</Link>:<span className="break-all">{r.saleId}</span>}</div>
   <p className="break-all"><strong>Склад:</strong> {r.warehouseId}</p>
   <p><strong>Дата:</strong> {dateLabel(r.createdAt)}</p>
   <p><strong>Создал:</strong> {r.createdBy}</p>
   <p className="sm:col-span-2"><strong>Причина:</strong> {r.reason}</p>
   {r.comment&&<p className="sm:col-span-2"><strong>Комментарий:</strong> {r.comment}</p>}
  </section>
  <section aria-label="Возвращённые позиции" className="space-y-3">
   <h3 className="text-lg font-bold">Позиции возврата</h3>
   {r.items.map(i=>{
     const original=sale.data?.items.find(x=>x.id===i.saleItemId);
     return <article key={i.id} className="rounded-xl border bg-white p-4 text-sm">
      <p className="break-all font-semibold">Позиция продажи: {i.saleItemId}</p>
      {original&&<p className="break-all">Вариация: {original.productVariantId}</p>}
      <p>Количество: {i.quantity} · Состояние: {conditionLabels[i.condition]}</p>
     </article>;
   })}
  </section>
  {hasPermission('EXCHANGES_CREATE')&&<Button asChild><Link to={'/returns/exchanges?returnId='+encodeURIComponent(r.id)}>
    Оформить обмен из этого возврата</Link></Button>}
  <p className="text-xs text-slate-500">Возврат уже обработан backend; исходная продажа не меняется вручную.</p>
 </div>;
}
