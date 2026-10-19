import {useState,type FormEvent} from 'react';
import {Link} from 'react-router';
import {useQuery} from '@tanstack/react-query';
import {useAuth} from '../../auth/AuthProvider';
import {financeApi,financeKeys} from '../api';
import {FinanceTabs} from '../components/FinanceTabs';
import {PageHeader} from '../../../shared/components/PageHeader';
import {LoadingState} from '../../../shared/components/LoadingState';
import {ErrorState} from '../../../shared/components/ErrorState';
import {ForbiddenPage} from '../../../pages/ForbiddenPage';
import {Input} from '../../../shared/ui/input';
import {Button} from '../../../shared/ui/button';
import {periodMonth,validPeriod,som} from '../validation';

const rows=[
 ['Клиентские оплаты','payments','in'],
 ['Доплаты по обменам','exchangePayments','in'],
 ['Отменённые оплаты','paymentCorrections','out'],
 ['Возвраты клиентам','refunds','out'],
 ['Возвраты по обменам','exchangeRefunds','out'],
 ['Оплаченные операционные расходы','operatingExpenses','out'],
 ['Оплаты закупок и карго','purchasePayments','out'],
] as const;

export function FinanceReportPage(){
 const {hasPermission}=useAuth();
 const allowed=hasPermission('FINANCE_READ');
 const [draft,setDraft]=useState(periodMonth);
 const [dates,setDates]=useState(periodMonth);
 const [error,setError]=useState('');
 const flow=useQuery({queryKey:financeKeys.flowPeriod(dates.from,dates.to),
  queryFn:()=>financeApi.flow(dates.from,dates.to),enabled:allowed});
 const expense=useQuery({queryKey:financeKeys.expenseList(dates.from,dates.to),
  queryFn:()=>financeApi.expenses(dates.from,dates.to),
  enabled:allowed&&hasPermission('EXPENSES_READ')});
 const accrual=useQuery({queryKey:['finance','report','accruals',dates.from,dates.to],
  queryFn:()=>financeApi.accruals(dates.from,dates.to),
  enabled:allowed&&hasPermission('EXPENSES_READ')});
 function changePeriod(e:FormEvent){
  e.preventDefault();
  if(!validPeriod(draft.from,draft.to)){setError('Период должен быть корректным и не длиннее 93 дней.');return;}
  setDates({...draft});setError('');
 }
 if(!allowed)return <ForbiddenPage/>;
 return <div className="space-y-5 pb-6">
  <PageHeader title="Финансовый отчёт" description="Проверяемые поступления, выплаты и начисления в сомах"/>
  <FinanceTabs/>
  <form className="flex flex-wrap items-end gap-3 rounded-2xl border bg-white p-4" onSubmit={changePeriod}>
   <label className="text-sm font-semibold">С даты (включительно)
    <Input type="date" value={draft.from} onChange={e=>setDraft(p=>({...p,from:e.target.value}))}/></label>
   <label className="text-sm font-semibold">По дату (не включительно)
    <Input type="date" value={draft.to} onChange={e=>setDraft(p=>({...p,to:e.target.value}))}/></label>
   <Button type="submit">Показать</Button>
  </form>
  {error&&<p role="alert" className="text-red-700">{error}</p>}
  {flow.isPending?<LoadingState label="Загружаем финансовый отчёт…"/>:
   flow.isError?<ErrorState error={flow.error} onRetry={()=>void flow.refetch()}/>:
   <>
    <div aria-label="Денежные показатели" className="grid grid-cols-2 gap-3 lg:grid-cols-3">
     {([['Поступления',flow.data.totalIn],['Выплаты',flow.data.totalOut],
       ['Чистый денежный поток',flow.data.netCashFlow]] as const).map(([name,value])=>
      <article key={name} className="min-w-0 rounded-2xl border bg-white p-4 shadow-sm">
       <p className="text-xs text-slate-500">{name}</p>
       <p className="mt-2 break-words text-xl font-bold tabular-nums">{som(value)}</p>
      </article>)}
    </div>
    <section className="overflow-hidden rounded-2xl border bg-white shadow-sm">
     <div className="border-b p-4">
      <h2 className="text-lg font-bold">Все виды денежных поступлений и выплат</h2>
      <p className="text-sm text-slate-500">Сводная таблица по источникам за выбранный период. Это агрегаты, а не отдельные транзакции.</p>
     </div>
     <div className="overflow-x-auto"><table aria-label="Сводная таблица финансовых потоков" className="w-full min-w-[480px] text-left text-sm">
      <thead className="bg-slate-50 text-slate-600"><tr><th className="p-3">Категория</th><th className="p-3 text-right">Поступило</th><th className="p-3 text-right">Выплачено</th></tr></thead>
      <tbody>{rows.map(([title,key,kind])=><tr key={key} className="border-t">
       <th scope="row" className="p-3 font-medium">{title}</th>
       <td className="p-3 text-right tabular-nums text-emerald-700">{kind==='in'?som(flow.data[key]):'—'}</td>
       <td className="p-3 text-right tabular-nums text-rose-700">{kind==='out'?som(flow.data[key]):'—'}</td>
      </tr>)}</tbody>
      <tfoot className="border-t bg-slate-50 font-bold"><tr>
       <td className="p-3">Итого</td><td className="p-3 text-right">{som(flow.data.totalIn)}</td>
       <td className="p-3 text-right">{som(flow.data.totalOut)}</td></tr></tfoot>
     </table></div>
     <p className="border-t p-3 text-xs text-slate-600">Перемещения CASH ↔ BANK не являются внешним доходом или расходом. Выручка и прибыль рассчитываются отдельно от денежного потока.</p>
    </section>
   </>}
  {hasPermission('EXPENSES_READ')&&<section className="rounded-2xl border bg-white p-4 shadow-sm">
   <div className="flex flex-wrap justify-between gap-2"><h2 className="text-lg font-bold">Расходы и начисления</h2>
    <Link className="text-sm text-blue-700 underline" to="/finance/expenses">Открыть расходы</Link></div>
   {expense.isPending||accrual.isPending?<LoadingState label="Загружаем расходы и начисления…"/>:
    expense.isError?<ErrorState error={expense.error} onRetry={()=>void expense.refetch()}/>:
    accrual.isError?<ErrorState error={accrual.error} onRetry={()=>void accrual.refetch()}/>:
    <div className="mt-3 grid gap-3 sm:grid-cols-2">
     <div className="rounded-xl bg-slate-50 p-3"><p className="text-xs text-slate-600">Проведённые расходы: {expense.data.length}</p>
      <p className="text-xs text-slate-500">Детализация доступна в разделе расходов</p></div>
     <div className="rounded-xl bg-slate-50 p-3"><p className="text-xs text-slate-600">Начислений за период: {accrual.data.length}</p>
      <p className="text-xs text-slate-500">Начисление и его выплата не суммируются повторно</p></div>
    </div>}
  </section>}
  <section className="rounded-2xl border border-amber-200 bg-white p-4">
   <h2 className="font-bold">Прибыль и себестоимость</h2>
   <p className="mt-2 text-sm text-slate-700">P&L появится после сверки признания продаж, FIFO себестоимости, возвратов и начислений. Пока эти показатели не подтверждены, система не подставляет вместо прибыли денежный поток.</p>
  </section>
 </div>;
}
