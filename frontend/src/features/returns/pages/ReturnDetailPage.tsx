import { Link,useParams } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { salesApi,salesKeys } from '../../sales/api';
import { inventoryApi,inventoryKeys } from '../../inventory/api';
import { displaySaleNumber } from '../../../shared/lib/format';
import { ReturnChairLabel } from '../components/ReturnChairLabel';
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
 const warehouseList=useQuery({
   queryKey:inventoryKeys.warehouses,queryFn:inventoryApi.warehouses,
   enabled:hasPermission('INVENTORY_READ')&&!!q.data,
 });
 if(!read)return <ForbiddenPage/>;
 if(!uuid(returnId))return <NotFoundPage/>;
 if(q.isPending)return <LoadingState label="Загружаем возврат…"/>;
 if(q.isError){
   if(q.error instanceof ApiClientError&&q.error.status===403)return <ForbiddenPage/>;
   if(q.error instanceof ApiClientError&&q.error.status===404)return <NotFoundPage/>;
   return <ErrorState error={q.error} onRetry={()=>void q.refetch()}/>;
 }
 const r=q.data;
 const warehouse=warehouseList.data?.find(w=>w.id===r.warehouseId);
 return <div className="space-y-5">
  <Link to="/returns" className="font-semibold text-indigo-700 underline">← Возвраты</Link>
  <PageHeader title="Карточка возврата"
    description={'Оформлен '+dateLabel(r.createdAt)}/>
  <section className="grid gap-3 rounded-xl border bg-white p-4 text-sm sm:grid-cols-2">
   <div><strong>Продажа:</strong> {hasPermission('SALES_READ')?
     (sale.data?
       <Link className="font-semibold text-blue-700 underline-offset-2 hover:underline"
         to={'/sales/'+encodeURIComponent(r.saleId)}>{displaySaleNumber(sale.data.saleNumber)}</Link>:
       <span>{sale.isPending?'Загружаем номер заказа…':'Номер заказа недоступен'}</span>):
     <span>Недоступно без SALES_READ</span>}</div>
   <div><strong>Склад возврата:</strong>{' '}
     {hasPermission('INVENTORY_READ')?
       (warehouse?
         <Link className="font-semibold text-blue-700 underline-offset-2 hover:underline"
           to={'/inventory?warehouseId='+encodeURIComponent(warehouse.id)}>{warehouse.name}</Link>:
         <span>Название склада недоступно</span>):
       <span>Недоступно без INVENTORY_READ</span>}
   </div>
   <p><strong>Дата:</strong> {dateLabel(r.createdAt)}</p>
   <p><strong>Создал:</strong> {r.createdBy}</p>
   <p className="sm:col-span-2"><strong>Причина:</strong> {r.reason}</p>
   {r.comment&&<p className="sm:col-span-2"><strong>Комментарий:</strong> {r.comment}</p>}
  </section>
  <section aria-label="Возвращённые позиции" className="space-y-3">
   <h3 className="text-lg font-bold">Позиции возврата</h3>
   {r.items.map((i,index)=>{
     const original=sale.data?.items.find(x=>x.id===i.saleItemId);
     return <article key={i.id} className="space-y-2 rounded-xl border border-slate-200 bg-white p-4 text-sm">
      <p className="text-xs font-semibold text-slate-500">Позиция {index+1}</p>
      <p className="font-semibold text-slate-900">
        {original?<ReturnChairLabel variantId={original.productVariantId}/>:
          'Кресло · подробности доступны в заказе'}
      </p>
      <p>Возвращено: <strong>{i.quantity} шт.</strong> ·
        Состояние: <strong>{conditionLabels[i.condition]}</strong></p>
     </article>;
   })}
  </section>
  {hasPermission('REFUNDS_CREATE')&&<Button asChild variant="outline">
    <Link to={'/returns/'+encodeURIComponent(r.id)+'/refund'}>
      Вернуть деньги клиенту</Link>
   </Button>}
  <p className="text-xs text-slate-500">Возврат уже обработан backend; исходная продажа не меняется вручную.</p>
 </div>;
}
