import { useState } from 'react';
import { useSearchParams } from 'react-router';
import { useQuery,useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { salesKeys } from '../../sales/api';
import { customersApi } from '../../customers/api';
import { financeApi,financeKeys } from '../api';
import { GuardedPost } from './GuardedPost';
import { ApiClientError } from '../../../shared/api/api-error';
import { Button } from '../../../shared/ui/button';
import { Input } from '../../../shared/ui/input';
import { ErrorState } from '../../../shared/components/ErrorState';
import { LoadingState } from '../../../shared/components/LoadingState';
import {
 channels,accountFor,methodFor,optional,validUUID,som,validateRefund,positiveSom,dateLabel
} from '../validation';
import type {
 PaymentChannel,PaymentInput,PaymentResponse,RefundInput,RefundMethod
} from '../types';
import type { SaleResponse } from '../../sales/types';
type PaymentDraft=PaymentInput&{localOperationId:string};
type CancelDraft={paymentId:string;reason:string;localOperationId:string};
const pending=(error:unknown)=>error instanceof ApiClientError&&error.kind==='http'&&error.status===404;
const card='space-y-4 rounded-2xl border border-slate-200 bg-white p-5';
export function SaleFinancialSection({sale}:{sale:SaleResponse}){
 const {hasPermission}=useAuth(),client=useQueryClient();
 const [query]=useSearchParams();
 const paymentRead=hasPermission('PAYMENTS_READ'),paymentCreate=hasPermission('PAYMENTS_CREATE'),
  paymentCancel=hasPermission('PAYMENTS_CANCEL'),refundRead=hasPermission('REFUNDS_READ'),
  refundCreate=hasPermission('REFUNDS_CREATE'),financeRead=hasPermission('FINANCE_READ');
 const relevant=paymentRead||paymentCreate||paymentCancel||refundRead||refundCreate;
 const payments=useQuery({queryKey:financeKeys.payments(sale.id),queryFn:()=>financeApi.payments(sale.id),
  enabled:paymentRead&&relevant});
 const active=useQuery({queryKey:financeKeys.activePayment(sale.id),queryFn:()=>financeApi.activePayment(sale.id),
  enabled:paymentRead&&relevant});
 const refunds=useQuery({queryKey:financeKeys.refunds(sale.id),queryFn:()=>financeApi.refunds(sale.id),
  enabled:refundRead});
 const accounts=useQuery({queryKey:financeKeys.accounts,queryFn:financeApi.accounts,
  enabled:financeRead&&(paymentCreate||paymentCancel||refundCreate)});
 const customer=useQuery({queryKey:['customers','customer',sale.customerId],
  queryFn:()=>customersApi.get(sale.customerId!),enabled:hasPermission('CUSTOMERS_READ')&&!!sale.customerId});
 const [channel,setChannel]=useState<PaymentChannel>('CASH'),[reference,setReference]=useState('');
 const [comment,setComment]=useState(''),[refundAmount,setRefundAmount]=useState('');
 const [refundMethod,setRefundMethod]=useState<RefundMethod>('CASH'),[refundReturn,setRefundReturn]=useState(()=>{
  const supplied=query.get('returnId')??'';return validUUID(supplied)?supplied:'';
 });
 const [refundReason,setRefundReason]=useState(''),[refundReference,setRefundReference]=useState('');
 const [refundComment,setRefundComment]=useState(''),[error,setError]=useState('');
 const activePayment=payments.data?.find(p=>p.status==='PAID');
 const noActive=payments.isSuccess&&!activePayment&&(active.isSuccess===false&&pending(active.error));
 const account=accounts.data?.find(a=>a.code===accountFor(channel));
 const canPayStatus=sale.status==='CONFIRMED'||sale.status==='FULFILLED';
 const canPay=paymentCreate&&canPayStatus&&(!paymentRead||noActive)&&
  (!financeRead||account?.initialized===true);
 const customerLabel=customer.data?.fullName||sale.customerId||'Без карточки клиента';
 async function refresh(){
  await Promise.all([
   client.invalidateQueries({queryKey:financeKeys.payments(sale.id)}),
   client.invalidateQueries({queryKey:financeKeys.refunds(sale.id)}),
   client.invalidateQueries({queryKey:financeKeys.accounts}),
   client.invalidateQueries({queryKey:financeKeys.flow}),
   client.invalidateQueries({queryKey:salesKeys.detail(sale.id)}),
   client.invalidateQueries({queryKey:salesKeys.lists}),
   client.invalidateQueries({queryKey:salesKeys.kanban}),
  ]);
 }
 if(!relevant)return null;
 return <div className="space-y-5">
  <section aria-label="Оплата заказа" className={card}>
   <h3 className="text-lg font-bold">Оплата заказа</h3>
   <p className="text-sm">Заказ {sale.saleNumber}, сумма {som(sale.total)}. Клиент: {customerLabel}.</p>
   {paymentRead&&(payments.isPending?<LoadingState label="Загружаем платежи продажи…"/>:
    payments.isError?<ErrorState error={payments.error} onRetry={()=>void payments.refetch()}/>:
    <div className="space-y-2">{payments.data.length===0?
     <p className="text-sm text-slate-600">Платежей пока нет.</p>:
     payments.data.map(p=><PaymentLine key={p.id} payment={p} onChanged={refresh}
      canCancel={paymentCancel} financeRead={financeRead} accounts={accounts.data} />)}</div>)}
   {paymentCreate&&<GuardedPost<PaymentDraft> kind="payment-create" scope={sale.id}
    allowed={paymentCreate} title="Подтвердить полную оплату продажи?" label="Подтвердить оплату"
    operationKey={p=>p.localOperationId}
    meta={p=>({method:p.method,channel:p.channel,saleId:p.saleId})}
    post={async p=>{const {localOperationId:_unused,...body}=p;void _unused;await financeApi.postPayment(body);}}
    onDone={refresh}
    reconcile={paymentRead?async _record=>{
      const latest=await financeApi.payments(sale.id);
      client.setQueryData(financeKeys.payments(sale.id),latest);
      // Backend has NO idempotencyKey in PaymentResponse; an existing
      // payment alone does not prove which HTTP request created it.
      return false;
    }:undefined}>
    {({start,locked})=><form aria-label="Регистрация оплаты" className="space-y-3"
      onSubmit={e=>{e.preventDefault();
       if(!canPay){setError('Продажа не подтверждена, уже оплачена, либо счёт не проверен. Если заказ создан обменом, обычная оплата запрещена backend.');return;}
       if(reference.trim().length>200||comment.trim().length>1000){setError('Слишком длинный reference или комментарий.');return;}
       setError('');
       const p:PaymentDraft={saleId:sale.id,channel,method:methodFor(channel),reference:optional(reference),comment:optional(comment),
        localOperationId:crypto.randomUUID()};
       start(p,'Заказ '+sale.saleNumber+'. Клиент '+customerLabel+'. Полная сумма '+som(sale.total)+
         '. '+(channels.find(x=>x.value===channel)?.label??channel)+'. Зачисление: '+accountFor(channel)+
         '. При банковской рассрочке убедитесь, что деньги фактически поступили от банка.');}}>
       <label className="block space-y-1 text-sm font-semibold">Как клиент оплатил
        <select aria-label="Канал клиентской оплаты" value={channel} disabled={locked}
          onChange={e=>setChannel(e.target.value as PaymentChannel)}
          className="min-h-11 w-full rounded-xl border p-2">
          {channels.map(x=><option key={x.value} value={x.value}>{x.label}</option>)}
        </select>
       </label>
       <p className="text-sm">Метод: <strong>{methodFor(channel)}</strong> · Счёт: <strong>{accountFor(channel)}</strong> · Сумма: <strong>{som(sale.total)}</strong></p>
       {financeRead&&<p className="text-sm">
        {account?.initialized===false?'Счёт не инициализирован':
          accounts.isPending?'Проверяем счёт…':account?.initialized===true?'Счёт инициализирован':'Нет подтверждённых данных счёта'}
       </p>}
       <label className="block space-y-1 text-sm font-semibold">Reference (при необходимости)
        <Input aria-label="Reference платежа" value={reference} maxLength={200} disabled={locked}
         onChange={e=>setReference(e.target.value)}/></label>
       <label className="block space-y-1 text-sm font-semibold">Комментарий
        <textarea aria-label="Комментарий платежа" value={comment} maxLength={1000} disabled={locked}
         onChange={e=>setComment(e.target.value)} className="w-full rounded-xl border p-2"/></label>
       {error&&<p role="alert" className="text-red-700">{error}</p>}
       {!canPayStatus&&<p className="text-sm text-amber-800">DRAFT и CANCELLED нельзя оплачивать.</p>}
       {paymentRead&&activePayment&&<p className="text-sm text-amber-800">Уже зарегистрирована активная оплата; повтор запрещён.</p>}
       {!paymentRead&&<p className="text-sm text-amber-800">Без PAYMENTS_READ нельзя проверить историю. При неизвестном результате обращайтесь к уполномоченному сотруднику.</p>}
       <p className="text-xs text-slate-500">Частичных оплат нет: backend проводит полную сумму автоматически. Для новой продажи из обмена используется ExchangeSettlement.</p>
       <Button type="submit" disabled={locked||!canPay}>Проверить и подтвердить оплату</Button>
      </form>}
   </GuardedPost>}
  </section>
  {(refundRead||refundCreate)&&<section aria-label="Денежные возвраты" className={card}>
   <h3 className="text-lg font-bold">Денежные возвраты клиенту</h3>
   <p className="text-sm text-slate-600">Денежный Refund не делает RETURN_IN и не изменяет склад.</p>
   {refundRead&&(refunds.isPending?<LoadingState label="Загружаем возвраты денег…"/>:
    refunds.isError?<ErrorState error={refunds.error} onRetry={()=>void refunds.refetch()}/>:
    refunds.data.length===0?<p className="text-sm">Денежных возвратов пока нет.</p>:
    refunds.data.map(r=><p key={r.id} className="border-b p-2 text-sm">
     {som(r.amount)} · {r.method} · {r.reason} · {dateLabel(r.refundedAt)} · {r.returnId||'Без физического возврата'}
    </p>))}
   {refundCreate&&<GuardedPost<RefundInput> kind="refund-create" scope={sale.id} canReplay
    allowed={refundCreate} title="Подтвердить возврат денег клиенту?" label="Вернуть деньги" destructive
    operationKey={p=>p.idempotencyKey}
    meta={p=>({sale:p.saleId,returnId:p.returnId||'',amount:p.amount,method:p.method})}
    post={financeApi.createRefund} onDone={async()=>{
      setRefundAmount('');setRefundReason('');setRefundReference('');setRefundComment('');setRefundReturn('');
      await refresh();
    }}
    reconcile={refundRead?async ()=>{
      const latest=await financeApi.refunds(sale.id);
      client.setQueryData(financeKeys.refunds(sale.id),latest);
      // RefundResponse exposes no idempotencyKey; cannot prove exactly
      // which POST produced a similar amount. Keep locked for reconciliation.
      return false;
    }:undefined}>
    {({start,locked})=><form aria-label="Новый денежный возврат" className="space-y-3"
      onSubmit={e=>{e.preventDefault();
       const p:RefundInput={idempotencyKey:crypto.randomUUID(),saleId:sale.id,
        returnId:refundReturn.trim()||null,amount:refundAmount.trim(),method:refundMethod,
        reason:refundReason.trim(),reference:optional(refundReference),comment:optional(refundComment)};
       const issue=validateRefund(p);
       if(issue){setError(issue);return;}
       setError('');start(p,'Продажа '+sale.saleNumber+'. Вернуть '+som(p.amount)+' клиенту со счёта '+
        (p.method==='CASH'?'CASH':'BANK')+'. Физический Return: '+(p.returnId||'не указан')+'. Причина: '+p.reason+
        '. Backend проверит активную оплату, допустимую сумму и отсутствие Exchange.');}}>
     <label className="block space-y-1 text-sm font-semibold">UUID физического возврата (необязательно)
      <Input aria-label="UUID связанного возврата" value={refundReturn} disabled={locked}
       onChange={e=>setRefundReturn(e.target.value.trim())}/></label>
     <label className="block space-y-1 text-sm font-semibold">Сумма возврата, сом
      <Input aria-label="Сумма денежного возврата" value={refundAmount} maxLength={17}
       disabled={locked} inputMode="numeric" onChange={e=>setRefundAmount(e.target.value)}/></label>
     <label className="block space-y-1 text-sm font-semibold">Метод возврата
      <select aria-label="Метод Refund" value={refundMethod} disabled={locked}
       onChange={e=>setRefundMethod(e.target.value as RefundMethod)}
       className="min-h-11 w-full rounded-xl border p-2">
       <option value="CASH">CASH — наличные</option><option value="TRANSFER">TRANSFER — банк</option>
      </select></label>
     <label className="block space-y-1 text-sm font-semibold">Причина
      <Input aria-label="Причина денежного возврата" value={refundReason} maxLength={500}
       disabled={locked} onChange={e=>setRefundReason(e.target.value)}/></label>
     <label className="block space-y-1 text-sm font-semibold">Reference
      <Input aria-label="Reference возврата" value={refundReference} maxLength={200} disabled={locked}
       onChange={e=>setRefundReference(e.target.value)}/></label>
     <label className="block space-y-1 text-sm font-semibold">Комментарий
      <textarea aria-label="Комментарий Refund" value={refundComment} maxLength={1000} disabled={locked}
       onChange={e=>setRefundComment(e.target.value)} className="w-full rounded-xl border p-2"/></label>
     <p className="text-xs text-slate-600">Сумма доступной компенсации определяется backend по исходной цене продажи, не по каталогу. Refund и Exchange одного Return несовместимы.</p>
     {error&&<p role="alert" className="text-red-700">{error}</p>}
     <Button type="submit" disabled={locked||!positiveSom(refundAmount)||!!refundReturn&&!validUUID(refundReturn)}>
      Подтвердить денежный возврат</Button>
    </form>}
   </GuardedPost>}
  </section>}
 </div>;
}

function PaymentLine({payment:p,onChanged,canCancel,financeRead,accounts}:{
 payment:PaymentResponse;onChanged:()=>Promise<void>;canCancel:boolean;financeRead:boolean;
 accounts?:{code:'CASH'|'BANK';balance:string|number;initialized:boolean}[];
}){
 const [reason,setReason]=useState(''),[error,setError]=useState('');
 const account=p.method==='CASH'?'CASH':'BANK';
 const can=p.status==='PAID'&&canCancel;
 return <article className="space-y-2 rounded-xl border p-3 text-sm">
  <p><strong>{p.status}</strong> · {som(p.amount)} · {p.channel} · {dateLabel(p.paidAt)} · {p.paidBy}</p>
  {p.cancellationReason&&<p>Отменён: {p.cancellationReason}</p>}
  {can&&<GuardedPost<CancelDraft> kind="payment-cancel" scope={p.id} allowed={can}
   title="Отменить платёж обратной проводкой?" label="Отменить оплату" destructive
   operationKey={x=>x.localOperationId} meta={x=>({payment:x.paymentId})}
   post={x=>financeApi.cancelPayment(x.paymentId,x.reason)} onDone={onChanged}
   reconcile={async ()=>{
    const fresh=await financeApi.payment(p.id);
    return fresh.status==='CANCELLED';
   }}>
   {({start,locked})=><div className="space-y-2">
    <label className="block font-semibold">Причина отмены
     <Input aria-label={'Причина отмены оплаты '+p.id} maxLength={1000} value={reason} disabled={locked}
      onChange={e=>setReason(e.target.value)}/></label>
    {financeRead&&<p>Счёт {account}: {accounts?.find(x=>x.code===account)?.initialized?'Инициализирован':'Требует проверки'}</p>}
    {error&&<p role="alert" className="text-red-700">{error}</p>}
    <Button type="button" variant="destructive" disabled={locked||!reason.trim()} onClick={()=>{
     if(!reason.trim()||reason.length>1000){setError('Причина обязательна (до 1000 символов).');return;}
     setError('');
     start({paymentId:p.id,reason:reason.trim(),localOperationId:crypto.randomUUID()},
      'Заказ '+p.saleId+'. Обратная операция '+som(p.amount)+' со счёта '+account+
      '. Отмена оплаты не отменяет продажу. Если уже есть Refund/Exchange, backend отклонит действие.');
    }}>Отменить платёж</Button>
   </div>}
  </GuardedPost>}
 </article>;
}
