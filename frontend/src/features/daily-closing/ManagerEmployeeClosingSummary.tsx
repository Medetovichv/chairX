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
    <div className="grid gap-3 sm:grid-cols-3">
     <div className="rounded-xl bg-slate-50 p-4"><p className="text-xs text-slate-500">Закрыто смен</p>
      <p className="mt-1 text-2xl font-bold">{report.data.closedEmployees} / {report.data.totalEmployees}</p></div>
     <div className="rounded-xl bg-slate-50 p-4"><p className="text-xs text-slate-500">Все расходы наличными</p>
      <p className="mt-1 text-2xl font-bold">{som(report.data.cashExpenses)}</p></div>
     <div className="rounded-xl bg-slate-50 p-4"><p className="text-xs text-slate-500">Все расходы переводами</p>
      <p className="mt-1 text-2xl font-bold">{som(report.data.bankExpenses)}</p></div>
    </div>
    <div className="grid gap-3 sm:grid-cols-2">
     <div className="rounded-xl border border-slate-200 p-4"><p className="text-xs text-slate-600">Сдано наличными по закрытым сменам</p>
      <p className="mt-1 text-2xl font-bold">{som(report.data.totalActualCash)}</p>
      <p className="mt-1 text-xs text-slate-500">Расчёт: {som(report.data.totalExpectedCash)}</p></div>
     <div className="rounded-xl border border-slate-200 p-4"><p className="text-xs text-slate-600">Подтверждено переводами по закрытым сменам</p>
      <p className="mt-1 text-2xl font-bold">{som(report.data.totalActualBank)}</p>
      <p className="mt-1 text-xs text-slate-500">Расчёт: {som(report.data.totalExpectedBank)}</p></div>
    </div>
    <div className="overflow-x-auto"><h3 className="mb-2 font-semibold">Закрытия за день</h3>
     <table className="w-full text-left text-sm"><thead><tr className="border-b">
      <th className="p-2">Сотрудник</th><th className="p-2">Состояние</th>
      <th className="p-2">Факт наличные</th><th className="p-2">Факт переводы</th>
     </tr></thead><tbody>{report.data.employees.map(e=><tr className="border-b" key={e.employeeId}>
      <td className="p-2">{e.displayName||e.username}</td>
      <td className="p-2">{e.closed?'Закрыто':'Ожидает закрытия'}</td>
      <td className="p-2">{e.actualCash===null?'—':som(e.actualCash)}</td>
      <td className="p-2">{e.actualBank===null?'—':som(e.actualBank)}</td>
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
