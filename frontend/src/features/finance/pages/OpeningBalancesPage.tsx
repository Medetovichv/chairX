import { useState } from 'react';
import { useQuery,useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { financeApi,financeKeys } from '../api';
import { nonnegativeSom,validateOpening,som } from '../validation';
import type { FinanceAccount,OpeningInput } from '../types';
import { GuardedPost } from '../components/GuardedPost';
import { FinanceTabs } from '../components/FinanceTabs';
import { PageHeader } from '../../../shared/components/PageHeader';
import { ForbiddenPage } from '../../../pages/ForbiddenPage';
import { Button } from '../../../shared/ui/button';
import { Input } from '../../../shared/ui/input';
import { ErrorState } from '../../../shared/components/ErrorState';
export function OpeningBalancesPage(){
 const {hasPermission}=useAuth(),allowed=hasPermission('FINANCE_INITIALIZE'),read=hasPermission('FINANCE_READ');
 const client=useQueryClient();
 const [account,setAccount]=useState<FinanceAccount>('CASH'),[amount,setAmount]=useState('0');
 const [agreed,setAgreed]=useState(false),[error,setError]=useState('');
 const accounts=useQuery({queryKey:financeKeys.accounts,queryFn:financeApi.accounts,enabled:read&&allowed});
 const snapshot=accounts.data?.find(x=>x.code===account);
 if(!allowed)return <ForbiddenPage/>;
 return <div className="space-y-5">
  <PageHeader title="Начальные остатки" description="Однократная административная настройка финансового счёта."/>
  <FinanceTabs/>
  {accounts.isError&&<ErrorState error={accounts.error} onRetry={()=>void accounts.refetch()}/>}
  <GuardedPost<OpeningInput> key={account} kind="initialize" scope={account} allowed={allowed}
   title="Установить начальный остаток без возможности повтора?" label="Инициализировать счёт"
   operationKey={()=>crypto.randomUUID()} meta={x=>({account:x.account,amount:x.amount})}
   post={financeApi.initialize} onDone={async()=>{await client.invalidateQueries({queryKey:financeKeys.accounts});}}
   reconcile={read?async record=>{
    const a=await financeApi.accounts();
    client.setQueryData(financeKeys.accounts,a);
    const item=a.find(x=>x.code===record.meta.account);
    return !!item?.initialized&&record.meta.amount!==undefined&&amountMatch(item.balance,record.meta.amount);
   }:undefined}>
   {({start,locked})=><form aria-label="Инициализация финансового счёта" className="space-y-4 rounded-xl border bg-white p-4"
      onSubmit={e=>{e.preventDefault();const p={account,amount:amount.trim()};
       const issue=validateOpening(p);
       if(issue||!agreed||read&&(!snapshot||snapshot.initialized)){
        setError(issue||'Требуется согласие и подтверждение неинициализированного счёта.');return;
       }
       setError('');start(p,'Счёт '+account+', начальный баланс '+amount+' сом. Установить повторно нельзя.');}}>
     <label className="block space-y-1 text-sm font-semibold">Финансовый счёт
      <select aria-label="Счёт инициализации" className="min-h-11 w-full rounded-xl border p-2"
        disabled={locked} value={account} onChange={e=>{setAccount(e.target.value as FinanceAccount);setAgreed(false);}}>
        <option value="CASH">CASH</option><option value="BANK">BANK</option>
      </select></label>
     <p className="text-sm">Текущий статус: {!read?'Нет FINANCE_READ — backend проверит счёт':accounts.isPending?'Проверяем…':
       snapshot?.initialized?'Инициализирован (повтор запрещён)':'Не инициализирован'}</p>
     {snapshot?.initialized&&<p className="text-sm">Остаток: {som(snapshot.balance)}</p>}
     <label className="block space-y-1 text-sm font-semibold">Начальный остаток в сомах
      <Input aria-label="Начальный остаток" value={amount} maxLength={17} disabled={locked}
       inputMode="numeric" onChange={e=>setAmount(e.target.value)}/></label>
     <label className="flex items-start gap-2 text-sm">
      <input aria-label="Согласие на однократную инициализацию" type="checkbox" checked={agreed}
       disabled={locked} onChange={e=>setAgreed(e.target.checked)}/>
      <span>Понимаю: начальный остаток нельзя установить повторно.</span>
     </label>
     {error&&<p role="alert" className="text-red-700">{error}</p>}
     <Button type="submit" disabled={locked||!agreed||!nonnegativeSom(amount)||read&&(!snapshot||snapshot.initialized)}>
      Установить начальный остаток</Button>
    </form>}
  </GuardedPost>
 </div>;
}
function amountMatch(balance:string|number,amount:string){
 if(typeof balance==='number'&&!Number.isSafeInteger(balance))return false;
 return String(balance).replace(/\.0+$/,'')===amount;
}
