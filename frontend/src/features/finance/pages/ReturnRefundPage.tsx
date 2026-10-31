import { useEffect, useRef, useState } from 'react';
import { Link,useParams } from 'react-router';
import { useQuery,useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { returnsApi,returnKeys } from '../../returns/api';
import { salesApi,salesKeys } from '../../sales/api';
import { integerSom } from '../../sales/validation';
import { displaySaleNumber } from '../../../shared/lib/format';
import { financeApi,financeKeys } from '../api';
import { GuardedPost } from '../components/GuardedPost';
import { validateRefund,positiveSom,optional,som,validUUID } from '../validation';
import type { RefundInput,RefundResponse,RefundMethod } from '../types';
import { PageHeader } from '../../../shared/components/PageHeader';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState';
import { ForbiddenPage } from '../../../pages/ForbiddenPage';
import { NotFoundPage } from '../../../pages/NotFoundPage';
import { Input } from '../../../shared/ui/input';
import { Button } from '../../../shared/ui/button';

/** Physically returned goods may be refunded without SALES_READ.
 * This screen only reads the Return under RETURNS_READ; all monetary
 * eligibility checks remain authoritative in RefundService.
 */
export function ReturnRefundPage(){
 const {returnId=''}=useParams();
 const {hasPermission}=useAuth();
 const allowed=hasPermission('REFUNDS_CREATE')&&hasPermission('RETURNS_READ');
 const readRefunds=hasPermission('REFUNDS_READ');
 const client=useQueryClient();
 const q=useQuery({queryKey:returnKeys.detail(returnId),
  queryFn:()=>returnsApi.get(returnId),enabled:allowed&&validUUID(returnId)});
 const sale=useQuery({queryKey:salesKeys.detail(q.data?.saleId??''),
  queryFn:()=>salesApi.get(q.data!.saleId),enabled:allowed&&hasPermission('SALES_READ')&&!!q.data});
 const [amount,setAmount]=useState(''),[method,setMethod]=useState<RefundMethod>('CASH');
 const [reason,setReason]=useState(''),[reference,setReference]=useState('');
 const [comment,setComment]=useState(''),[error,setError]=useState('');
 const [confirmed,setConfirmed]=useState<RefundResponse|null>(null);
 const initialized=useRef(false);
 useEffect(()=>{
   if(initialized.current||!q.data||!sale.data)return;
   const amounts=q.data.items.map(item=>{
     const original=sale.data!.items.find(x=>x.id===item.saleItemId);
     const unit=original?integerSom(original.unitSalePrice):null;
     return unit===null?null:BigInt(unit)*BigInt(item.quantity);
   });
   if(amounts.length&&amounts.every(value=>value!==null)) {
     setAmount(amounts.reduce<bigint>((sum,value)=>sum+(value??0n),0n).toString());
     setReason(q.data.reason);
     initialized.current=true;
   }
 },[q.data,sale.data]);

 if(!allowed)return <ForbiddenPage/>;
 if(!validUUID(returnId))return <NotFoundPage/>;
 if(q.isPending)return <LoadingState label="Загружаем физический возврат…"/>;
 if(q.isError)return <ErrorState error={q.error} onRetry={()=>void q.refetch()}/>;
 const r=q.data;
 async function refreshed(){
  await Promise.all([
   client.invalidateQueries({queryKey:financeKeys.refunds(r.saleId)}),
   client.invalidateQueries({queryKey:financeKeys.payments(r.saleId)}),
   client.invalidateQueries({queryKey:financeKeys.accounts}),
   client.invalidateQueries({queryKey:financeKeys.flow}),
  ]);
  setAmount('');setReason('');setReference('');setComment('');
 }
 return <div className="space-y-5">
  <Link to={'/returns/'+r.id} className="font-semibold text-indigo-700 underline">← Физический возврат</Link>
  <PageHeader title="Денежный возврат" description="Компенсация за оформленный физический возврат"/>
  <section className="rounded-xl border bg-white p-4 text-sm">
   <p>Продажа: {hasPermission('SALES_READ')? (sale.data?<Link className="font-semibold text-blue-700 underline" to={'/sales/'+encodeURIComponent(r.saleId)}>{displaySaleNumber(sale.data.saleNumber)}</Link>:'Номер заказа недоступен'):'Недоступно для вашей роли'}</p>
   <p>Причина физического возврата: {r.reason}</p>
   <p>Здесь проводится только денежная компенсация. Товар не возвращается на склад повторно.</p>
  </section>
  {confirmed&&<p role="status" className="rounded-xl bg-emerald-50 p-3 text-sm text-emerald-800">
   Денежный возврат подтверждён backend: {som(confirmed.amount)}.
   Операция зарегистрирована.
  </p>}
  <GuardedPost<RefundInput> kind="return-refund" scope={r.id} allowed={allowed}
   title="Подтвердить денежный возврат?" label="Списать деньги клиенту" destructive canReplay
   operationKey={p=>p.idempotencyKey}
   meta={p=>({saleId:p.saleId,returnId:p.returnId??'',amount:p.amount,method:p.method})}
   post={async p=>{const posted=await financeApi.createRefund(p);setConfirmed(posted);}}
   onDone={refreshed}
   reconcile={readRefunds?async ()=>{
    // RefundResponse has no idempotencyKey: this GET may inform an
    // administrator but cannot prove the *same* request was applied.
    const list=await financeApi.refunds(r.saleId);
    client.setQueryData(financeKeys.refunds(r.saleId),list);
    return false;
   }:undefined}>
   {({start,locked})=><form aria-label="Денежный возврат по физическому возврату"
    className="space-y-3 rounded-xl border bg-white p-4" onSubmit={e=>{
    e.preventDefault();
    const p:RefundInput={idempotencyKey:crypto.randomUUID(),saleId:r.saleId,returnId:r.id,
     amount:amount.trim(),method,reason:reason.trim(),reference:optional(reference),comment:optional(comment)};
    const issue=validateRefund(p);
    if(issue){setError(issue);return;}
    setError('');
    start(p,'Денежная компенсация '+som(p.amount)+' клиенту за физический возврат. Способ списания: '+(p.method==='CASH'?'наличные':'перевод')+
     '. Backend проверит активную оплату, стоимость исходных позиций, ранее проведённые компенсации и обмены.');
   }}>
    <p className="text-sm text-slate-600">Сумма предварительно рассчитана из количества возвращённых кресел и исходных цен заказа. Перед подтверждением проверьте и при необходимости измените её.</p>
    <label className="block space-y-1 text-sm font-semibold">Сумма, целые сомы
     <Input aria-label="Сумма денежного возврата" value={amount} maxLength={17} inputMode="numeric"
      disabled={locked} onChange={e=>setAmount(e.target.value)}/></label>
    <label className="block space-y-1 text-sm font-semibold">Метод денежного возврата
     <select aria-label="Способ денежного возврата" value={method} disabled={locked}
      onChange={e=>setMethod(e.target.value as RefundMethod)}
      className="min-h-11 w-full rounded-xl border p-2">
      <option value="CASH" >Наличные</option><option value="TRANSFER">Перевод</option>
     </select></label>
    <label className="block space-y-1 text-sm font-semibold">Причина *
     <Input aria-label="Причина денежного возврата" value={reason} maxLength={500}
      disabled={locked} onChange={e=>setReason(e.target.value)}/></label>
    <label className="block space-y-1 text-sm font-semibold">Назначение перевода
     <Input aria-label="Назначение перевода" value={reference} maxLength={200}
      disabled={locked} onChange={e=>setReference(e.target.value)}/></label>
    <label className="block space-y-1 text-sm font-semibold">Комментарий
     <textarea aria-label="Комментарий денежного возврата" className="w-full rounded-xl border p-2"
      value={comment} maxLength={1000} disabled={locked} onChange={e=>setComment(e.target.value)}/></label>
    <p className="text-xs text-slate-600">Точная доступная сумма неизвестна frontend: не рассчитываем её по текущему каталогу. Бизнес-правила проверит backend.</p>
    {error&&<p role="alert" className="text-red-700">{error}</p>}
    <Button type="submit" disabled={locked||!positiveSom(amount)}>Подтвердить возврат</Button>
   </form>}
  </GuardedPost>
 </div>;
}
