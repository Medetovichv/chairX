import {useQuery} from '@tanstack/react-query';
import {managerClosingApi} from './personal-api';
import {som} from '../finance/validation';
import {LoadingState} from '../../shared/components/LoadingState';
import {ErrorState} from '../../shared/components/ErrorState';

/** Company-level data: render only in the finance-authorized closing page. */
export function ManagerEmployeeClosingSummary({date}:{date:string}){
 const report=useQuery({queryKey:['employee-closings','manager',date],
  queryFn:()=>managerClosingApi.report(date)});
 return <section aria-label="Закрытия сотрудников и все расходы" className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
  <div><h2 className="text-lg font-bold">Закрытия сотрудников и все расходы</h2>
   <p className="mt-1 text-sm text-slate-600">Общая сверка руководителя: кто закрыл смену, какие расходы зарегистрировал каждый сотрудник.</p></div>
  {report.isPending?<LoadingState label="Загружаем общий отчёт сотрудников…"/>:
   report.isError?<ErrorState error={report.error} onRetry={()=>void report.refetch()}/>:
   <>
    <div className="grid gap-3 sm:grid-cols-2">
     <div className="rounded-xl bg-slate-50 p-4"><p className="text-xs text-slate-500">Расходы предприятия за день</p>
      <p className="mt-1 text-2xl font-bold">{som(report.data.expenseTotal)}</p></div>
    </div>
    <div className="rounded-xl bg-slate-50 p-5">
      <p className="text-sm text-slate-500">Сумма подтверждённых сотрудниками закрытий</p>
      <p className="mt-2 text-3xl font-bold">{som(report.data.actualTotal??0)}</p>
      <p className="mt-1 text-sm text-slate-600">Расчётная сумма: {som(report.data.expectedTotal??0)}. Окончательное финансовое закрытие подтверждается отдельно менеджером.</p>
    </div>
    <details className="rounded-xl border border-slate-200 p-3"><summary className="cursor-pointer text-sm font-semibold">Разбивка старых закрытий по счётам (где сохранена)</summary>
    <div className="mt-3 grid gap-3 sm:grid-cols-2">
     <div className="rounded-xl border border-slate-200 p-4"><p className="text-xs text-slate-600">Сдано наличными по старым закрытиям</p>
      <p className="mt-1 text-2xl font-bold">{som(report.data.totalActualCash)}</p>
      <p className="mt-1 text-xs text-slate-500">Расчёт: {som(report.data.totalExpectedCash)}</p></div>
     <div className="rounded-xl border border-slate-200 p-4"><p className="text-xs text-slate-600">Подтверждено переводами по закрытым сменам</p>
      <p className="mt-1 text-2xl font-bold">{som(report.data.totalActualBank)}</p>
      <p className="mt-1 text-xs text-slate-500">Расчёт: {som(report.data.totalExpectedBank)}</p></div>
    </div>
    </details>
    <div className="overflow-x-auto"><h3 className="mb-2 font-semibold">Закрытия за день</h3>
     <table className="w-full text-left text-sm"><thead><tr className="border-b">
      <th className="p-2">Сотрудник</th><th className="p-2">Состояние</th>
      <th className="p-2">Подтверждённая сумма</th><th className="p-2">Причина расхождения</th>
     </tr></thead><tbody>{report.data.employees.map(e=><tr className="border-b" key={e.employeeId}>
      <td className="p-2">{e.displayName||e.username}</td>
      <td className="p-2">{e.closed?'Закрыто':'Ожидает закрытия'}</td>
      <td className="p-2">{!e.closed?'—':som(e.actualTotal??(Number(e.actualCash??0)+Number(e.actualBank??0)))}</td>
      <td className="p-2">{e.totalNote||'—'}</td>
     </tr>)}</tbody></table>
    </div>
    <div className="overflow-x-auto"><h3 className="mb-2 font-semibold">Расходы всех сотрудников · {som(report.data.expenseTotal)}</h3>
     {report.data.expenses.length===0?<p className="text-sm text-slate-500">Расходов за этот день нет.</p>:
      <table className="w-full text-left text-sm"><thead><tr className="border-b">
       <th className="p-2">Создал</th><th className="p-2">Категория</th>
       <th className="p-2">Назначение</th><th className="p-2">Счёт</th>
       <th className="p-2 text-right">Сумма</th>
      </tr></thead><tbody>{report.data.expenses.map(e=><tr className="border-b" key={e.id}>
       <td className="p-2">{e.createdBy}</td><td className="p-2">{e.category}</td>
       <td className="p-2">{e.comment||'—'}</td>
       <td className="p-2">{e.paymentMethod==='CASH'?'Наличные':'Перевод'}</td>
       <td className="p-2 text-right font-semibold">{som(e.amount)}</td>
      </tr>)}</tbody></table>}
    </div>
   </>}
 </section>;
}
