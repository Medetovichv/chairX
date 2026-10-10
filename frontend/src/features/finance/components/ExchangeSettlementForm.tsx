import { useState } from 'react';
import { useQuery,useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { exchangesApi,returnKeys } from '../../returns/api';
import { salesKeys } from '../../sales/api';
import { financeApi,financeKeys } from '../api';
import { GuardedPost } from './GuardedPost';
import { Input } from '../../../shared/ui/input';
import { Button } from '../../../shared/ui/button';
import { amountCents,positiveSom,validateSettlement,som,optional } from '../validation';
import type { SettlementInput,FinanceAccount } from '../types';
import type { ExchangeResponse } from '../../returns/types';
export function ExchangeSettlementForm({exchange:d}:{exchange:ExchangeResponse}){
 const {hasPermission}=useAuth(),client=useQueryClient();
 const allowed=hasPermission('EXCHANGES_READ')&&hasPermission('EXCHANGES_SETTLE');
 const readFinance=hasPermission('FINANCE_READ');
 const settlements=useQuery({queryKey:returnKeys.settlements(d.id),
  queryFn:()=>exchangesApi.settlements(d.id),enabled:allowed});
 const accounts=useQuery({queryKey:financeKeys.accounts,queryFn:financeApi.accounts,enabled:allowed&&readFinance});
 const direction=amountCents(d.additionalPaymentDue)!==null&&amountCents(d.additionalPaymentDue)!>0n?'IN':'OUT';
 const due=direction==='IN'?d.additionalPaymentDue:d.refundDue;
 const posted=settlements.data?.filter(s=>s.direction===direction)
  .reduce((sum,s)=>sum+(amountCents(s.amount)??0n),0n)??0n;
 const remaining=amountCents(due)!==null?amountCents(due)!-posted:null;
 const [amount,setAmount]=useState(''),[method,setMethod]=useState<'CASH'|'TRANSFER'>('CASH');
 const [reference,setReference]=useState(''),[error,setError]=useState('');
 const account:FinanceAccount=method==='CASH'?'CASH':'BANK';
 const activeAccount=accounts.data?.find(x=>x.code===account);
 const canPost=d.status==='PENDING_SETTLEMENT'&&remaining!==null&&remaining>0n&&
  settlements.isSuccess&&(!readFinance||activeAccount?.initialized===true);
 async function refreshed(){
  await Promise.all([
   client.invalidateQueries({queryKey:returnKeys.exchange(d.id)}),
   client.invalidateQueries({queryKey:returnKeys.settlements(d.id)}),
   client.invalidateQueries({queryKey:financeKeys.accounts}),
   client.invalidateQueries({queryKey:financeKeys.flow}),
   client.invalidateQueries({queryKey:salesKeys.lists}),
  ]);
 }
 if(!allowed||d.status!=='PENDING_SETTLEMENT')return null;
 return <section aria-label="Расчёт обмена" className="space-y-4 rounded-xl border border-indigo-200 bg-white p-4">
  <h3 className="font-bold">{direction==='IN'?'Принять доплату клиента':'Выплатить разницу клиенту'}</h3>
  <p className="text-sm">Обязательство: {som(due)}. Уже проведено: {som((posted/100n).toString()+'.'+(posted%100n).toString().padStart(2,'0'))}. Остаток: {
   remaining!==null&&remaining>=0n?som((remaining/100n).toString()+'.'+(remaining%100n).toString().padStart(2,'0')):'Недоступен'}</p>
  <GuardedPost<SettlementInput> kind="settlement" scope={d.id} allowed={allowed}
   title="Подтвердить финансовый расчёт обмена?" label={direction==='IN'?'Принять доплату':'Вернуть разницу'}
   destructive={direction==='OUT'} operationKey={p=>p.idempotencyKey}
   meta={p=>({direction:p.direction,method:p.method,amount:p.amount})}
   post={p=>financeApi.settle(d.id,p)} onDone={refreshed}
   reconcile={async record=>{
    const list=await exchangesApi.settlements(d.id);
    client.setQueryData(returnKeys.settlements(d.id),list);
    return list.some(s=>s.idempotencyKey===record.key&&s.direction===record.meta.direction&&
      s.method===record.meta.method&&amountCents(s.amount)===BigInt(record.meta.amount)*100n);
   }}>
   {({start,locked})=><form aria-label="Новый расчёт обмена" className="space-y-3"
    onSubmit={e=>{e.preventDefault();
      const p:SettlementInput={idempotencyKey:crypto.randomUUID(),direction,method,amount:amount.trim(),reference:optional(reference)};
      const issue=validateSettlement(p);
      if(issue||remaining===null||BigInt(positiveSom(amount)?amount:'0')*100n>remaining||!canPost){
       setError(issue||'Сумма превышает оставшуюся задолженность или нет подтверждённого состояния обмена/счёта.');return;
      }
      setError('');
      start(p,(direction==='IN'?'Получить от клиента ':'Выплатить клиенту ')+som(p.amount)+
       ' через '+method+' ('+account+'). Backend проверит текущий остаток обязательства и финансовое закрытие.');}}>
    <label className="block space-y-1 text-sm font-semibold">Сумма расчёта, сом
     <Input aria-label="Сумма расчёта обмена" value={amount} maxLength={17} inputMode="numeric"
      disabled={locked} onChange={e=>setAmount(e.target.value)}/></label>
    <label className="block space-y-1 text-sm font-semibold">Способ
     <select aria-label="Метод расчёта обмена" value={method} disabled={locked}
      onChange={e=>setMethod(e.target.value as 'CASH'|'TRANSFER')}
      className="min-h-11 w-full rounded-xl border p-2">
      <option value="CASH">Наличные (CASH)</option><option value="TRANSFER">Перевод (BANK)</option>
     </select></label>
    <label className="block space-y-1 text-sm font-semibold">Reference
     <Input aria-label="Reference обмена" value={reference} maxLength={200} disabled={locked}
      onChange={e=>setReference(e.target.value)}/></label>
    {readFinance&&<p className="text-sm">Счёт {account}: {activeAccount?.initialized===true?
       som(activeAccount.balance):'Не инициализирован или нет данных'}</p>}
    {error&&<p role="alert" className="text-red-700">{error}</p>}
    <Button type="submit" disabled={locked||!canPost||!positiveSom(amount)}>
      {direction==='IN'?'Подтвердить доплату':'Подтвердить выплату'}</Button>
   </form>}
  </GuardedPost>
 </section>;
}
