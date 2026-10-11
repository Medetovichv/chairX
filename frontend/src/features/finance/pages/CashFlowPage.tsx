import { useState,type FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { financeApi,financeKeys } from '../api';
import { periodMonth,validPeriod,som,dateLabel } from '../validation';
import { FinanceTabs } from '../components/FinanceTabs';
import { PageHeader } from '../../../shared/components/PageHeader';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState';
import { ForbiddenPage } from '../../../pages/ForbiddenPage';
import { Input } from '../../../shared/ui/input';
import { Button } from '../../../shared/ui/button';
export function CashFlowPage(){
 const {hasPermission}=useAuth(),allowed=hasPermission('FINANCE_READ');
 const [draft,setDraft]=useState(periodMonth),[period,setPeriod]=useState(periodMonth),[error,setError]=useState('');
 const q=useQuery({queryKey:financeKeys.flowPeriod(period.from,period.to),
  queryFn:()=>financeApi.flow(period.from,period.to),enabled:allowed});
 function submit(e:FormEvent){e.preventDefault();if(!validPeriod(draft.from,draft.to)){
  setError('Период должен быть корректным и не превышать 93 дня; дата «по» не включается.');
 }else{setPeriod({...draft});setError('');}}
 if(!allowed)return <ForbiddenPage/>;
 const fields=[
  ['Платежи клиентов','payments'],['Коррекции платежей','paymentCorrections'],
  ['Возвраты клиентам','refunds'],['Доплаты по обменам','exchangePayments'],
  ['Выплаты по обменам','exchangeRefunds'],['Операционные расходы','operatingExpenses'],
  ['Оплаты закупок и карго','purchasePayments'],['Итого поступило','totalIn'],
  ['Итого выплачено','totalOut'],['Чистое движение денег','netCashFlow'],
 ] as const;
 return <div className="space-y-5">
  <PageHeader title="Движение денег" description="Реальный Cash Flow по данным backend (не прибыль)."/>
  <FinanceTabs/>
  <form className="flex flex-wrap items-end gap-3 rounded-xl border bg-white p-4" onSubmit={submit}>
   <label className="text-sm font-semibold">С (включительно)
    <Input type="date" aria-label="Cash Flow с" value={draft.from}
     onChange={e=>setDraft(x=>({...x,from:e.target.value}))}/>
   </label>
   <label className="text-sm font-semibold">По (исключительно)
    <Input type="date" aria-label="Cash Flow по" value={draft.to}
     onChange={e=>setDraft(x=>({...x,to:e.target.value}))}/>
   </label>
   <Button type="submit">Применить</Button>
  </form>
  {error&&<p role="alert" className="text-red-700">{error}</p>}
  {q.isPending?<LoadingState label="Загружаем Cash Flow…"/>:
   q.isError?<ErrorState error={q.error} onRetry={()=>void q.refetch()}/>:
   <section aria-label="Агрегаты Cash Flow" className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
    {fields.map(([title,key])=><article key={key} className="rounded-xl border bg-white p-4">
      <p className="text-sm text-slate-600">{title}</p>
      <p className="text-lg font-semibold">{som(q.data[key])}</p>
    </article>)}
   </section>}
  <p className="text-xs text-slate-600">Даты: [{dateLabel(period.from)}, {dateLabel(period.to)}) в Asia/Bishkek. Переводы CASH/BANK не считаются внешними доходами или расходами.</p>
 </div>;
}
