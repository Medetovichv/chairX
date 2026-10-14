import { useState,type FormEvent } from 'react';
import { Link } from 'react-router';
import { useAuth } from '../../auth/AuthProvider';
import { useQuery,useQueryClient } from '@tanstack/react-query';
import { financeApi,financeKeys } from '../api';
import { categories,periodMonth,validPeriod,bishkekToday,validLocalDate,
 positiveSom,validateExpense,som,dateLabel,optional } from '../validation';
import type {ExpenseCategory,ExpenseInput,FinanceAccount} from '../types';
import { GuardedPost } from '../components/GuardedPost';
import { FinanceTabs } from '../components/FinanceTabs';
import { PageHeader } from '../../../shared/components/PageHeader';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState';
import { EmptyState } from '../../../shared/components/EmptyState';
import { ForbiddenPage } from '../../../pages/ForbiddenPage';
import { Input } from '../../../shared/ui/input';
import { Button } from '../../../shared/ui/button';
export function ExpensesPage(){
 const {hasPermission,user}=useAuth(),read=hasPermission('EXPENSES_READ'),create=hasPermission('EXPENSES_CREATE');
 const client=useQueryClient();
 const [draft,setDraft]=useState(periodMonth),[period,setPeriod]=useState(periodMonth),[periodError,setPeriodError]=useState('');
 const [category,setCategory]=useState<ExpenseCategory>('ADVERTISING'),[amount,setAmount]=useState('');
 const [account,setAccount]=useState<FinanceAccount>('CASH'),[date,setDate]=useState(bishkekToday);
 const [comment,setComment]=useState(''),[error,setError]=useState('');
 const list=useQuery({queryKey:financeKeys.expenseList(period.from,period.to),
  queryFn:()=>financeApi.expenses(period.from,period.to),enabled:read});
 const total=useQuery({queryKey:financeKeys.expenseTotal(period.from,period.to),
  queryFn:()=>financeApi.expenseTotal(period.from,period.to),enabled:read});
 const accounts=useQuery({queryKey:financeKeys.accounts,queryFn:financeApi.accounts,
  enabled:create&&hasPermission('FINANCE_READ')});
 function setRange(e:FormEvent){e.preventDefault();if(!validPeriod(draft.from,draft.to)){
  setPeriodError('Выберите корректный период не длиннее 93 дней. Дата «по» исключительная.');
 }else{setPeriodError('');setPeriod({...draft});}}
 async function refreshed(){
  setAmount('');setComment('');
  await Promise.all([
   client.invalidateQueries({queryKey:financeKeys.expenses}),
   client.invalidateQueries({queryKey:financeKeys.accounts}),
   client.invalidateQueries({queryKey:financeKeys.flow}),
  ]);
 }
 if(!read&&!create)return <ForbiddenPage/>;
 return <div className="space-y-5">
  <PageHeader title="Расходы"/>
  <FinanceTabs/>
  {read&&<section className="space-y-3">
   <form aria-label="Период расходов" className="flex flex-wrap items-end gap-3 rounded-xl border bg-white p-4" onSubmit={setRange}>
    <label className="space-y-1 text-sm font-semibold">С (включительно)
     <Input aria-label="Расходы с" type="date" value={draft.from} onChange={e=>setDraft(d=>({...d,from:e.target.value}))}/></label>
    <label className="space-y-1 text-sm font-semibold">По (исключительно)
     <Input aria-label="Расходы по" type="date" value={draft.to} onChange={e=>setDraft(d=>({...d,to:e.target.value}))}/></label>
    <Button type="submit">Показать</Button>
   </form>
   {periodError&&<p role="alert" className="text-red-700">{periodError}</p>}
   <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"><p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Расходы за выбранный период</p><p className="mt-2 text-3xl font-bold text-slate-950">{total.isPending?"Загружаем…":total.isError?"Не удалось получить сумму":som(total.data)}</p></div>
   {list.isPending?<LoadingState label="Загружаем расходы…"/>:
     list.isError?<ErrorState error={list.error} onRetry={()=>void list.refetch()}/>:
     list.data.length===0?<EmptyState title="Расходов не найдено" description="Для этого периода записей нет."/>:
     <div aria-label="Список расходов" className="space-y-2">
       {list.data.map(e=><article key={e.id} className="grid gap-2 rounded-xl border bg-white p-4 text-sm sm:grid-cols-3">
        <div><strong>{categories.find(x=>x.value===e.category)?.label??e.category}</strong>
         <p>{dateLabel(e.expenseDate)} · {e.paymentMethod==='CASH'?'Наличные':'Перевод'}</p></div>
        <p className="font-semibold">{som(e.amount)}</p>
        <div><p>{e.comment||'Без комментария'}</p><p>{e.createdBy} · {dateLabel(e.createdAt)}</p></div>
       </article>)}</div>}
  </section>}
  {create&&<section className="space-y-3">
   <h3 className="text-lg font-bold">Добавить расход</h3>
   {hasPermission("FINANCE_READ")&&accounts.isSuccess&&accounts.data.some(x=>!x.initialized)&&<div role="status" className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-950"><p className="font-semibold">Не все финансовые счета настроены</p><p className="mt-1">Для учёта расхода необходимо один раз задать начальный остаток соответствующего счёта.</p>{user?.roles.includes("ADMIN")&&hasPermission("FINANCE_INITIALIZE")&&<Link className="mt-2 inline-block font-semibold text-blue-700 underline" to="/finance/opening-balances">Перейти к настройке счетов</Link>}</div>}
   <GuardedPost<ExpenseInput> kind="expense" scope="create" canReplay allowed={create}
    title="Подтвердить списание расхода?" label="Провести расход" destructive
    operationKey={p=>p.idempotencyKey} meta={p=>({date:p.expenseDate,category:p.category,amount:p.amount,account:p.paymentMethod})}
    post={financeApi.createExpense} onDone={refreshed}>
    {({start,locked})=><form aria-label="Создание расхода" className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"
      onSubmit={e=>{e.preventDefault();const p:ExpenseInput={idempotencyKey:crypto.randomUUID(),category,amount:amount.trim(),
        paymentMethod:account,expenseDate:date,comment:optional(comment)};
       const issue=validateExpense(p);
       const snapshot=accounts.data?.find(x=>x.code===account);
       if(issue){setError(issue);return;}
       if(hasPermission('FINANCE_READ')&&(!snapshot?.initialized||accounts.isError||accounts.isPending)){
        setError(snapshot?.initialized===false?'Счёт ещё не настроен. Администратор должен задать начальный остаток в разделе финансов.':'Не удалось подтвердить доступность счёта. Обновите страницу или обратитесь к администратору.');return;}
       setError('');
       start(p,'Категория: '+(categories.find(x=>x.value===p.category)?.label??p.category)+
        '. Сумма: '+som(p.amount)+' со счёта '+account+'. Бизнес-дата: '+p.expenseDate+
        '. Backend отклонит запись за закрытый день; дата не будет изменена.');}}>
     <label className="block space-y-1 text-sm font-semibold">Категория
      <select aria-label="Категория расхода" value={category} disabled={locked}
       onChange={e=>setCategory(e.target.value as ExpenseCategory)}
       className="min-h-11 w-full rounded-xl border p-2">
       {categories.map(c=><option key={c.value} value={c.value}>{c.label}</option>)}
      </select></label>
     <label className="block space-y-1 text-sm font-semibold">Счёт оплаты
      <select aria-label="Счёт расхода" value={account} disabled={locked}
       onChange={e=>setAccount(e.target.value as FinanceAccount)}
       className="min-h-11 w-full rounded-xl border p-2">
       <option value="CASH">Наличные</option><option value="BANK">Банковский перевод</option>
      </select></label>
     {hasPermission("FINANCE_READ")&&accounts.isSuccess&&<p className="text-sm text-slate-600">Доступно на счёте: {accounts.data.find(x=>x.code===account)?.initialized?som(accounts.data.find(x=>x.code===account)!.balance):"Не настроен"}</p>}
     <label className="block space-y-1 text-sm font-semibold">Сумма, сом
      <Input aria-label="Сумма расхода" value={amount} maxLength={17} disabled={locked}
       inputMode="numeric" onChange={e=>setAmount(e.target.value)}/></label>
     <label className="block space-y-1 text-sm font-semibold">Дата расхода (Бишкек)
      <Input aria-label="Дата расхода" type="date" value={date} disabled={locked}
       onChange={e=>setDate(e.target.value)}/></label>
     <label className="block space-y-1 text-sm font-semibold">Комментарий
      <textarea aria-label="Комментарий расхода" value={comment} maxLength={1000}
       disabled={locked} onChange={e=>setComment(e.target.value)}
       className="w-full rounded-xl border p-3" rows={2}/></label>
     {error&&<p role="alert" className="text-red-700">{error}</p>}
     <Button type="submit" disabled={locked||!positiveSom(amount)||!validLocalDate(date)}>
       Создать расход</Button>
    </form>}
   </GuardedPost>
  </section>}
 </div>;
}
