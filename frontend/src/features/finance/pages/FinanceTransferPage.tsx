import { useState } from 'react';
import { useQuery,useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { financeApi,financeKeys } from '../api';
import { som,positiveSom,validateTransfer,amountCents } from '../validation';
import type { FinanceAccount,TransferInput } from '../types';
import { GuardedPost } from '../components/GuardedPost';
import { FinanceTabs } from '../components/FinanceTabs';
import { PageHeader } from '../../../shared/components/PageHeader';
import { ForbiddenPage } from '../../../pages/ForbiddenPage';
import { Input } from '../../../shared/ui/input';
import { Button } from '../../../shared/ui/button';
export function FinanceTransferPage(){
 const {hasPermission}=useAuth(),allowed=hasPermission('FINANCE_TRANSFER'),read=hasPermission('FINANCE_READ');
 const client=useQueryClient();
 const [from,setFrom]=useState<FinanceAccount>('CASH'),[to,setTo]=useState<FinanceAccount>('BANK'),[amount,setAmount]=useState(''),[error,setError]=useState('');
 const accounts=useQuery({queryKey:financeKeys.accounts,queryFn:financeApi.accounts,enabled:allowed&&read});
 const source=accounts.data?.find(x=>x.code===from),destination=accounts.data?.find(x=>x.code===to);
 const usable=read&&!!source?.initialized&&!!destination?.initialized;
 const check=(x:TransferInput)=>{
  const problem=validateTransfer(x);if(problem)return problem;
  if(read&&(!usable||accounts.isError||accounts.isPending))return 'Сначала нужно подтвердить, что оба счёта инициализированы.';
  if(read&&source&&amountCents(source.balance)!==null&&BigInt(x.amount)*100n>amountCents(source.balance)!)
    return 'Недостаточно средств на исходном счёте.';
  return null;
 };
 if(!allowed)return <ForbiddenPage/>;
 return <div className="space-y-5">
  <PageHeader title="Переводы" description="Внутреннее перемещение денег — не расход и не поступление."/>
  <FinanceTabs/>
  <GuardedPost<TransferInput> kind="transfer" scope="cash-bank" canReplay allowed={allowed}
   title="Подтвердить перевод?" label="Перевести деньги"
   operationKey={x=>x.transferId} meta={x=>({from:x.from,to:x.to,amount:x.amount})}
   post={financeApi.transfer} onDone={async()=>{await client.invalidateQueries({queryKey:financeKeys.accounts});}}>
   {({start,locked})=><form aria-label="Новый перевод" className="space-y-4 rounded-xl border bg-white p-4"
      onSubmit={e=>{e.preventDefault();const payload:TransferInput={transferId:crypto.randomUUID(),from,to,amount:amount.trim()};
       const issue=check(payload);if(issue){setError(issue);return;}setError('');
       start(payload,'Из '+from+' в '+to+': '+amount+' сом. Перевод не считается доходом или расходом.');}}>
    <div className="grid gap-3 sm:grid-cols-2">
     {([['Откуда',from,setFrom],['Куда',to,setTo]] as const).map(([title,value,change])=>
       <label key={title} className="space-y-1 text-sm font-semibold">{title}
        <select aria-label={title} value={value} disabled={locked}
         onChange={e=>change(e.target.value as FinanceAccount)}
         className="min-h-11 w-full rounded-xl border p-2">
         <option value="CASH">CASH — наличные</option><option value="BANK">BANK — банк</option>
        </select></label>)}
    </div>
    {read&&<p className="text-sm">{from}: {source?.initialized?som(source.balance):'Не инициализирован'} · {to}: {destination?.initialized?som(destination.balance):'Не инициализирован'}</p>}
    <label className="block space-y-1 text-sm font-semibold">Сумма перевода, сом
     <Input value={amount} maxLength={17} inputMode="numeric" disabled={locked}
      aria-label="Сумма перевода" onChange={e=>setAmount(e.target.value)}/></label>
    {error&&<p role="alert" className="text-red-700">{error}</p>}
    <Button type="submit" disabled={locked||!positiveSom(amount)||from===to}>Подтвердить перевод</Button>
   </form>}
  </GuardedPost>
 </div>;
}
