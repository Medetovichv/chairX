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
 const {hasPermission,user}=useAuth(),allowed=user?.roles.includes('ADMIN')===true&&hasPermission('FINANCE_INITIALIZE'),read=hasPermission('FINANCE_READ');
 const client=useQueryClient();
 const [account,setAccount]=useState<FinanceAccount>('CASH'),[amount,setAmount]=useState('0');
 const [agreed,setAgreed]=useState(false),[error,setError]=useState('');
 const [target,setTarget]=useState(''),[reason,setReason]=useState(''),[adjustmentError,setAdjustmentError]=useState('');
 const accounts=useQuery({queryKey:financeKeys.accounts,queryFn:financeApi.accounts,enabled:read&&allowed});
 const snapshot=accounts.data?.find(x=>x.code===account);
 if(!allowed)return <ForbiddenPage/>;
 return <div className="space-y-5">
  <PageHeader title="Начальные остатки" description="Только администратор: начальный остаток или документированная корректировка текущего баланса."/>
  <FinanceTabs/>
  {accounts.isError&&<ErrorState error={accounts.error} onRetry={()=>void accounts.refetch()}/>}
  {!snapshot?.initialized&&<GuardedPost<OpeningInput> key={account} kind="initialize" scope={account} allowed={allowed}
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
  </GuardedPost>}
  {snapshot?.initialized&&<section aria-label="Административная корректировка баланса" className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
   <h3 className="text-lg font-bold">Корректировка текущего остатка</h3>
   <p className="text-sm text-slate-600">Не переписывает историю платежей: разница будет отражена отдельным движением и записью аудита. Закрытый финансовый день менять нельзя.</p>
   <label className="block space-y-1 text-sm font-semibold">Счёт
    <select aria-label="Счёт корректировки" className="min-h-11 w-full rounded-xl border p-2" value={account}
      onChange={e=>{setAccount(e.target.value as FinanceAccount);setTarget('');setReason('');}}>
     <option value="CASH">Наличные</option><option value="BANK">Банк</option>
    </select></label>
   <p className="text-sm">Текущий остаток: <strong>{som(snapshot.balance)}</strong></p>
   <GuardedPost<{account:FinanceAccount;targetBalance:string;reason:string;idempotencyKey:string}>
     kind="admin-balance-adjustment" scope={account} allowed={allowed} canReplay destructive
     title="Провести финансовую корректировку?" label="Подтвердить корректировку"
     operationKey={x=>x.idempotencyKey}
     meta={x=>({account:x.account,target:x.targetBalance})}
     post={financeApi.adjustBalance}
     onDone={async()=>{
      setTarget('');setReason('');
      await Promise.all([client.invalidateQueries({queryKey:financeKeys.accounts}),client.invalidateQueries({queryKey:financeKeys.flow})]);
     }}>
    {({start,locked})=><form className="space-y-3" aria-label="Изменить текущий остаток"
      onSubmit={e=>{e.preventDefault();if(!nonnegativeSom(target)||reason.trim().length<5||reason.trim().length>500){
       setAdjustmentError('Укажите сумму в целых сомах и причину не короче 5 символов.');return;
      }setAdjustmentError('');start({account,targetBalance:target.trim(),reason:reason.trim(),idempotencyKey:crypto.randomUUID()},
       'Счёт '+account+': изменить текущий остаток с '+som(snapshot.balance)+' на '+som(target)+'. Причина: '+reason.trim());}}>
      <label className="block space-y-1 text-sm font-semibold">Новый остаток, сом
       <Input aria-label="Новый остаток счёта" inputMode="numeric" maxLength={17} value={target}
        disabled={locked} onChange={e=>setTarget(e.target.value)}/></label>
      <label className="block space-y-1 text-sm font-semibold">Причина корректировки
       <textarea aria-label="Причина корректировки" className="w-full rounded-xl border p-3" rows={2}
         maxLength={500} disabled={locked} value={reason} onChange={e=>setReason(e.target.value)}/></label>
      {adjustmentError&&<p role="alert" className="text-sm text-red-700">{adjustmentError}</p>}
      <Button type="submit" disabled={locked||!nonnegativeSom(target)||reason.trim().length<5}>
       Проверить и изменить остаток</Button>
    </form>}
   </GuardedPost>
  </section>}
 </div>;
}
function amountMatch(balance:string|number,amount:string){
 if(typeof balance==='number'&&!Number.isSafeInteger(balance))return false;
 return String(balance).replace(/\.0+$/,'')===amount;
}
