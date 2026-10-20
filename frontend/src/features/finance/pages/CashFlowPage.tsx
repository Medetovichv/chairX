import { useState,type FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { financeApi,financeKeys } from '../api';
import {useFinancePeriod} from '../useFinancePeriod';
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
 const [period,setPeriod]=useFinancePeriod();
 const [draft,setDraft]=useState(period),[error,setError]=useState('');
 const q=useQuery({queryKey:financeKeys.flowPeriod(period.from,period.to),
  queryFn:()=>financeApi.flow(period.from,period.to),enabled:allowed});
 function submit(e:FormEvent){e.preventDefault();if(!validPeriod(draft.from,draft.to)){
  setError('Период должен быть корректным и не превышать 93 дня; дата «по» не включается.');
 }else{setPeriod({...draft});setError('');}}
 if(!allowed)return <ForbiddenPage/>;
 const incoming=[['Платежи клиентов','payments'],['Коррекции платежей','paymentCorrections'],['Доплаты по обменам','exchangePayments']] as const;
 const outgoing=[['Возвраты клиентам','refunds'],['Выплаты по обменам','exchangeRefunds'],['Операционные расходы','operatingExpenses'],['Оплаты закупок и карго','purchasePayments']] as const;
 return <div className="space-y-5">
  <PageHeader title="Движение денег" description="Поступления, выплаты и чистое движение средств за выбранный период."/>
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
   <>
    <section aria-label="Итоги движения денег" className="grid gap-3 md:grid-cols-3">
     {([['Поступило','totalIn'],['Выплачено','totalOut'],['Чистый денежный поток','netCashFlow']] as const).map(([title,key])=>
      <article key={key} className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
       <p className="text-sm font-medium text-slate-500">{title}</p>
       <p className="mt-2 text-2xl font-bold tabular-nums text-slate-950">{som(q.data[key])}</p>
      </article>)}
    </section>
    <div className="grid gap-4 lg:grid-cols-2">
     <section aria-label="Источники поступлений" className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <h2 className="text-lg font-bold">Поступления</h2>
      <p className="mt-1 text-xs text-slate-500">Денежные поступления за выбранный период</p>
      <dl className="mt-4 divide-y divide-slate-100">{incoming.map(([title,key])=>
       <div key={key} className="flex items-center justify-between gap-3 py-3 text-sm"><dt className="text-slate-600">{title}</dt><dd className="font-semibold tabular-nums">{som(q.data[key])}</dd></div>)}</dl>
     </section>
     <section aria-label="Направления выплат" className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <h2 className="text-lg font-bold">Выплаты</h2>
      <p className="mt-1 text-xs text-slate-500">Расход денежных средств, а не расчёт прибыли</p>
      <dl className="mt-4 divide-y divide-slate-100">{outgoing.map(([title,key])=>
       <div key={key} className="flex items-center justify-between gap-3 py-3 text-sm"><dt className="text-slate-600">{title}</dt><dd className="font-semibold tabular-nums">{som(q.data[key])}</dd></div>)}</dl>
     </section>
    </div>
   </>}
  <p className="text-xs text-slate-600">Даты: [{dateLabel(period.from)}, {dateLabel(period.to)}) в Asia/Bishkek. Переводы CASH/BANK не считаются внешними доходами или расходами.</p>
 </div>;
}
