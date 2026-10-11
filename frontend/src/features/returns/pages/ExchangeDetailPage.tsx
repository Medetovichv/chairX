import { Link,useParams } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { salesApi,salesKeys } from '../../sales/api';
import { displaySaleNumber } from '../../../shared/lib/format';
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
 const originSale=useQuery({queryKey:salesKeys.detail(data.data?.originalSaleId??''),
  queryFn:()=>salesApi.get(data.data!.originalSaleId),enabled:allowed&&hasPermission('SALES_READ')&&!!data.data});
 const newSale=useQuery({queryKey:salesKeys.detail(data.data?.newSaleId??''),
  queryFn:()=>salesApi.get(data.data!.newSaleId),enabled:allowed&&hasPermission('SALES_READ')&&!!data.data});
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
  <PageHeader title="Карточка обмена" description="Возврат и новая продажа"/>
  <p className={'rounded-xl p-4 text-sm font-semibold '+(d.status==='COMPLETED'?'bg-emerald-50 text-emerald-800':'bg-amber-50 text-amber-900')}>
   {d.status==='COMPLETED'?'Завершён':'Ожидает финансового расчёта'}
  </p>
  <section aria-label="Связанные записи обмена" className="grid gap-3 rounded-xl border bg-white p-4 text-sm sm:grid-cols-3">
   <div><h3 className="font-semibold">Исходная продажа</h3>{hasPermission('SALES_READ')?
    <Link className="text-indigo-700 underline" to={'/sales/'+encodeURIComponent(d.originalSaleId)}>
      {originSale.data?displaySaleNumber(originSale.data.saleNumber):'Открыть исходный заказ'}</Link>:
    <p>Недоступно для вашей роли</p>}</div>
   <div><h3 className="font-semibold">Новая продажа</h3>{hasPermission('SALES_READ')?
    <Link className="text-indigo-700 underline" to={'/sales/'+encodeURIComponent(d.newSaleId)}>
      {newSale.data?displaySaleNumber(newSale.data.saleNumber):'Открыть новый заказ'}</Link>:
    <p>Недоступно для вашей роли</p>}</div>
   <div><h3 className="font-semibold">Исходный возврат</h3>{hasPermission('RETURNS_READ')?
    <Link className="text-indigo-700 underline" to={'/returns/'+encodeURIComponent(d.returnId)}>Открыть возврат</Link>:
    <p>Недоступно для вашей роли</p>}</div>
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
  {hasPermission('EXCHANGES_SETTLE')&&<ExchangeSettlementForm key={d.id} exchange={d}/>}
 </div>;
}
