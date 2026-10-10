import { Link,useParams } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { PageHeader } from '../../../shared/components/PageHeader';
import { ErrorState } from '../../../shared/components/ErrorState';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ForbiddenPage } from '../../../pages/ForbiddenPage';
import { NotFoundPage } from '../../../pages/NotFoundPage';
import { ApiClientError } from '../../../shared/api/api-error';
import { exchangesApi,returnKeys } from '../api';
import { ExchangeSettlementForm } from '../../finance/components/ExchangeSettlementForm';
import { som,uuid } from '../validation';
export function ExchangeDetailPage(){
 const {exchangeId=''}=useParams(),{hasPermission}=useAuth();
 const allowed=hasPermission('EXCHANGES_READ');
 const data=useQuery({queryKey:returnKeys.exchange(exchangeId),queryFn:()=>exchangesApi.get(exchangeId),enabled:allowed&&uuid(exchangeId)});
 const settlements=useQuery({queryKey:returnKeys.settlements(exchangeId),queryFn:()=>exchangesApi.settlements(exchangeId),
  enabled:allowed&&data.isSuccess});
 if(!allowed)return <ForbiddenPage/>;
 if(!uuid(exchangeId))return <NotFoundPage/>;
 if(data.isPending)return <LoadingState label="Загружаем обмен…"/>;
 if(data.isError){
  if(data.error instanceof ApiClientError&&data.error.status===403)return <ForbiddenPage/>;
  if(data.error instanceof ApiClientError&&data.error.status===404)return <NotFoundPage/>;
  return <ErrorState error={data.error} onRetry={()=>void data.refetch()}/>;
 }
 const d=data.data;
 const values:[string,number|string][]=[
  ['Стоимость возвращённого товара',d.returnedValue],['Стоимость нового заказа',d.newSaleTotal],
  ['Зачтённая сумма',d.creditApplied],['Доплата клиента',d.additionalPaymentDue],['Сумма к возврату клиенту',d.refundDue],
 ];
 return <div className="space-y-5">
  <Link to="/returns/exchanges" className="font-semibold text-indigo-700 underline">← Обмены</Link>
  <PageHeader title="Карточка обмена" description={'ID: '+d.id}/>
  <p className={'rounded-xl p-4 text-sm font-semibold '+(d.status==='COMPLETED'?'bg-emerald-50 text-emerald-800':'bg-amber-50 text-amber-900')}>
   {d.status==='COMPLETED'?'Завершён':'Ожидает финансового расчёта'} ({d.status})
  </p>
  <section aria-label="Связанные записи обмена" className="grid gap-3 rounded-xl border bg-white p-4 text-sm sm:grid-cols-3">
   <div><h3 className="font-semibold">Исходная продажа</h3>{hasPermission('SALES_READ')?
    <Link className="break-all text-indigo-700 underline" to={'/sales/'+d.originalSaleId}>{d.originalSaleId}</Link>:<p className="break-all">{d.originalSaleId}</p>}</div>
   <div><h3 className="font-semibold">Новая продажа</h3>{hasPermission('SALES_READ')?
    <Link className="break-all text-indigo-700 underline" to={'/sales/'+d.newSaleId}>{d.newSaleId}</Link>:<p className="break-all">{d.newSaleId}</p>}</div>
   <div><h3 className="font-semibold">Исходный возврат</h3>{hasPermission('RETURNS_READ')?
    <Link className="break-all text-indigo-700 underline" to={'/returns/'+d.returnId}>{d.returnId}</Link>:<p className="break-all">{d.returnId}</p>}</div>
  </section>
  <section aria-label="Финансовая разница обмена" className="grid gap-3 sm:grid-cols-2">
   {values.map(([title,value])=><article key={title} className="rounded-xl border bg-white p-4 text-sm">
    <p className="text-slate-500">{title}</p><p className="text-lg font-bold">{som(value)}</p>
   </article>)}
  </section>
  <section className="space-y-2 rounded-xl border bg-white p-4">
   <h3 className="font-semibold">Финансовые расчёты</h3>
   {settlements.isPending?<LoadingState label="Загружаем историю расчётов…"/>:
    settlements.isError?<ErrorState error={settlements.error} onRetry={()=>void settlements.refetch()}/>:
    settlements.data.length===0?<p className="text-sm">Расчётов пока нет.</p>:
    settlements.data.map(s=><p key={s.id} className="border-b py-2 text-sm">
      {s.direction} · {s.method} · {som(s.amount)} · {s.createdAt} · {s.createdBy}
    </p>)}
   <p className="text-xs text-slate-600">История отображает только подтверждённые backend расчёты CASH/BANK.</p>
  </section>
  {hasPermission('EXCHANGES_SETTLE')&&<ExchangeSettlementForm exchange={d}/>}
 </div>;
}
