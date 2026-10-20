import {useState} from 'react';
import {useFinancePeriod} from '../useFinancePeriod';
import {useQuery} from '@tanstack/react-query';
import {useAuth} from '../../auth/AuthProvider';
import {reportingApi} from '../reporting-api';
import {som} from '../validation';
import {FinanceTabs} from '../components/FinanceTabs';
import {PageHeader} from '../../../shared/components/PageHeader';
import {LoadingState} from '../../../shared/components/LoadingState';
import {ErrorState} from '../../../shared/components/ErrorState';
import {ForbiddenPage} from '../../../pages/ForbiddenPage';
export function FinanceAnalyticsPage(){
 const {hasPermission}=useAuth(),allowed=hasPermission('FINANCE_READ');
 const [year,setYear]=useState(new Date().getFullYear());
 const from=year+'-01-01',to=String(year+1)+'-01-01';
 const [expensePeriod]=useFinancePeriod();
 const categories=useQuery({queryKey:['f15','expense-categories',expensePeriod.from,expensePeriod.to],queryFn:()=>reportingApi.expenseCategories(expensePeriod.from,expensePeriod.to),enabled:allowed});
 const q=useQuery({queryKey:['f15','analytics',year],queryFn:()=>reportingApi.analytics(from,to),enabled:allowed});
 if(!allowed)return <ForbiddenPage/>;
 return <div className="w-full min-w-0 max-w-full space-y-5 overflow-x-hidden pb-10">
  <PageHeader title="Аналитика" description="Динамика выручки, экономической прибыли и кассовых потоков по месяцам"/>
  <FinanceTabs/>
  <label className="inline-flex items-center gap-3 rounded-xl border bg-white p-3 text-sm">Год
   <select aria-label="Год аналитики" className="rounded-lg border p-2" value={year} onChange={e=>setYear(Number(e.target.value))}>
    {[year-2,year-1,year,year+1].filter((x,i,a)=>a.indexOf(x)===i).map(x=><option key={x} value={x}>{x}</option>)}
   </select></label>
  <section className="rounded-2xl border bg-white p-4">
   <h2 className="font-semibold">Структура расходов · {expensePeriod.from} — {expensePeriod.to}</h2>
   <p className="mt-1 text-xs text-slate-500">Начисления F14 и прямые расходы; выплаты по начислениям не учитываются повторно.</p>
   {categories.isPending?<LoadingState label="Загружаем структуру расходов…"/>:
    categories.isError?<ErrorState error={categories.error} onRetry={()=>void categories.refetch()}/>:
    <div className="mt-3 space-y-3">
     {categories.data.categories.map(c=><div key={c.category} className="text-sm">
      <div className="flex justify-between gap-3"><strong>{c.category.replaceAll('_',' ')}</strong><strong>{som(c.total)}</strong></div>
      <div className="mt-1 h-2 rounded-full bg-slate-100"><div className="h-2 rounded-full bg-blue-600"
       style={{width:(()=>{
        const value=Number(c.total),grand=Number(categories.data.grandTotal);
        return Number.isFinite(value)&&Number.isFinite(grand)&&grand>0?Math.min(100,Math.max(0,value/grand*100))+'%':'0%';
       })()}}/></div>
      <p className="mt-1 text-xs text-slate-500">Начислено: {som(c.accrued)} · Прямые расходы: {som(c.direct)}</p>
     </div>)}
     {!categories.data.categories.length&&<p className="text-sm text-slate-500">Расходов за период нет.</p>}
     <div className="flex justify-between border-t pt-3 text-sm font-bold"><span>Итого</span><span>{som(categories.data.grandTotal)}</span></div>
    </div>}
  </section>
  {q.isPending?<LoadingState label="Загружаем динамику…"/>:q.isError?<ErrorState error={q.error} onRetry={()=>void q.refetch()}/>:
   <section className="rounded-2xl border bg-white p-4"><h2 className="font-semibold">Месячные результаты · {year}</h2>
    <div className="mt-4 space-y-3">{q.data.months.map(m=><div key={m.from} className="grid grid-cols-[55px_1fr] items-center gap-3 border-b pb-3 text-sm sm:grid-cols-[85px_1fr_1fr]">
     <strong>{m.from.slice(5,7)}.{year}</strong><div className="min-w-0">
      <p className="mb-1 text-xs">Выручка {som(m.netRevenue)}</p>
      <div className="h-3 rounded-full bg-slate-100"><div className="h-3 rounded-full bg-blue-500" style={{width:(()=>{
       const n=Math.max(0,Number(m.netRevenue));
       const max=Math.max(1,...q.data.months.map(x=>Math.max(0,Number(x.netRevenue))));
       return Number.isFinite(n)&&Number.isFinite(max)?Math.min(100,100*n/max)+'%':'0%';
      })()}}/></div></div>
      <p className="col-start-2 text-slate-600 sm:col-auto">Прибыль: {m.profitVerified&&m.operatingProfit!=null?som(m.operatingProfit):'Недоступно'}</p>
    </div>)}</div>
    {!q.data.months.length&&<p className="p-4 text-slate-500">Нет данных за период</p>}
   </section>}
 </div>;
}