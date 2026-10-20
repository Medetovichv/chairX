import {useState} from 'react';
import {useFinancePeriod} from '../useFinancePeriod';
import {useQuery} from '@tanstack/react-query';
import {useAuth} from '../../auth/AuthProvider';
import {reportingApi} from '../reporting-api';
import {som,categories as expenseLabels} from '../validation';
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
 const margins=useQuery({queryKey:['f15','model-margins',expensePeriod.from,expensePeriod.to],queryFn:()=>reportingApi.margins(expensePeriod.from,expensePeriod.to),enabled:allowed});
 const turnover=useQuery({queryKey:['f15','turnover',expensePeriod.from,expensePeriod.to],queryFn:()=>reportingApi.inventoryTurnover(expensePeriod.from,expensePeriod.to),enabled:allowed});
 const categories=useQuery({queryKey:['f15','expense-categories',expensePeriod.from,expensePeriod.to],queryFn:()=>reportingApi.expenseCategories(expensePeriod.from,expensePeriod.to),enabled:allowed});
 const q=useQuery({queryKey:['f15','analytics',year],queryFn:()=>reportingApi.analytics(from,to),enabled:allowed});
 const previous=useQuery({queryKey:['f15','analytics',year-1],queryFn:()=>reportingApi.analytics((year-1)+'-01-01',year+'-01-01'),enabled:allowed});
 const totals=(months: {netRevenue:string|number;netCash:string|number}[])=>months.reduce((acc,m)=>({revenue:acc.revenue+Number(m.netRevenue),cash:acc.cash+Number(m.netCash)}),{revenue:0,cash:0});
 const difference=(current:number,older:number)=>older===0?'Нет базы для %':((current-older)/Math.abs(older)*100).toFixed(1)+'%';
 // Compare equal month ranges: for the current year, exclude the unfinished month.
 const today=new Date();
 const monthsToCompare=year===today.getFullYear()?today.getMonth():12;
 const comparable=(months: {from:string;netRevenue:string|number;netCash:string|number}[])=>months.filter(m=>Number(m.from.slice(5,7))<=monthsToCompare);
 if(!allowed)return <ForbiddenPage/>;
 return <div className="w-full min-w-0 max-w-full space-y-5 overflow-x-hidden pb-10">
  <PageHeader title="Аналитика" description="Динамика выручки, экономической прибыли и кассовых потоков по месяцам"/>
  <FinanceTabs/>
  <label className="inline-flex items-center gap-3 rounded-xl border bg-white p-3 text-sm">Год
   <select aria-label="Год аналитики" className="rounded-lg border p-2" value={year} onChange={e=>setYear(Number(e.target.value))}>
    {[year-2,year-1,year,year+1].filter((x,i,a)=>a.indexOf(x)===i).map(x=><option key={x} value={x}>{x}</option>)}
   </select></label>
  <section aria-label="Маржинальность моделей" className="min-w-0 rounded-2xl border bg-white p-4">
   <h2 className="font-semibold">Продажи и маржинальность моделей</h2>
   <p className="mt-1 text-xs text-slate-500">Фактическая FIFO-себестоимость проданных вариаций; неполная себестоимость не заменяется текущей ценой закупки.</p>
   {margins.isPending?<LoadingState label="Загружаем модели…"/>:margins.isError?<ErrorState error={margins.error} onRetry={()=>void margins.refetch()}/>:
    margins.data.length===0?<p className="mt-3 text-sm text-slate-500">Нет продаж моделей за выбранный период.</p>:
    <>
     <div className="mt-3 hidden overflow-x-auto sm:block"><table className="w-full min-w-[620px] text-left text-sm">
      <thead className="bg-slate-50"><tr>{['Модель / вариация','Продано','Выручка','Себестоимость FIFO','Валовая прибыль'].map(x=><th key={x} className="p-3">{x}</th>)}</tr></thead>
      <tbody>{margins.data.map(m=><tr key={m.variantId} className="border-t"><td className="p-3 font-medium">{m.model} · {m.variation}</td><td className="p-3">{m.quantity}</td><td className="p-3">{som(m.revenue)}</td><td className="p-3">{m.costComplete?som(m.knownFifoCost):'Не подтверждена'}</td><td className="p-3">{m.costComplete&&m.grossProfit!=null?som(m.grossProfit):'Недоступно'}</td></tr>)}</tbody>
     </table></div>
     <div className="mt-3 space-y-3 sm:hidden">{margins.data.map(m=><article key={m.variantId} className="min-w-0 rounded-xl border p-3 text-sm">
      <h3 className="break-words font-semibold">{m.model} · {m.variation}</h3><p>Продано: {m.quantity}</p><p>Выручка: {som(m.revenue)}</p><p>FIFO: {m.costComplete?som(m.knownFifoCost):'Не подтверждена'}</p><p className="font-medium">Валовая прибыль: {m.costComplete&&m.grossProfit!=null?som(m.grossProfit):'Недоступно'}</p>
     </article>)}</div>
    </>}
  </section>
  <section aria-label="Оборачиваемость запасов" className="min-w-0 rounded-2xl border bg-white p-4">
   <h2 className="font-semibold">Оборачиваемость склада · {expensePeriod.from} — {expensePeriod.to}</h2>
   <p className="mt-1 text-xs text-slate-500">FIFO-себестоимость реализованных товаров за вычетом восстановленной стоимости возвратов, разделённая на среднюю стоимость запасов на границах периода.</p>
   {turnover.isPending?<LoadingState label="Рассчитываем оборачиваемость…"/>:turnover.isError?<ErrorState error={turnover.error} onRetry={()=>void turnover.refetch()}/>:
    <div className="mt-3 grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
     {([['На начало',som(turnover.data.openingCost)],['На конец',som(turnover.data.closingCost)],['Средний остаток',som(turnover.data.averageCost)],['Обороты',turnover.data.available&&turnover.data.turns!=null?Number(turnover.data.turns).toLocaleString('ru-RU',{maximumFractionDigits:2})+' ×':'Недоступно']] as const).map(([label,value])=><div key={label} className="min-w-0 rounded-xl bg-slate-50 p-3"><p className="text-slate-500">{label}</p><strong className="mt-1 block break-words">{value}</strong></div>)}
    </div>}
   {turnover.isSuccess&&!turnover.data.available&&<p className="mt-2 text-xs text-amber-700">Средний остаток равен нулю либо исходные FIFO-суммы требуют сверки. Коэффициент не рассчитывается.</p>}
  </section>
  <section aria-label="Сравнение финансовых годов" className="min-w-0 rounded-2xl border bg-white p-4">
   <h2 className="font-semibold">Сравнение с {year-1} годом</h2>
   <p className="mt-1 text-xs text-slate-500">Сравниваются одинаковые месяцы: для текущего года — только завершённые месяцы ({monthsToCompare>0?`январь–${String(monthsToCompare).padStart(2,"0")}`:"завершённых месяцев ещё нет"}), для прошлых лет — весь год. Кассовый поток не является прибылью.</p>
   {q.isSuccess&&previous.isSuccess?(()=>{const now=totals(comparable(q.data.months)),old=totals(comparable(previous.data.months));return <div className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2">{([["Чистая выручка",now.revenue,old.revenue],["Чистый денежный поток",now.cash,old.cash]] as const).map(([label,current,prior])=><article key={label} className="min-w-0 rounded-xl bg-slate-50 p-3 text-sm"><h3 className="font-medium">{label}</h3><p className="mt-2 break-words">{year}: <strong>{som(current)}</strong></p><p className="break-words text-slate-600">{year-1}: {som(prior)}</p><p className="mt-1 text-xs text-slate-600">Изменение: {difference(current,prior)}</p></article>)}</div>})():q.isError||previous.isError?<ErrorState error={q.error||previous.error} onRetry={()=>{void q.refetch();void previous.refetch();}}/>:<LoadingState label="Загружаем сравнение годов…"/>}
  </section>
  <section className="rounded-2xl border bg-white p-4">
   <h2 className="font-semibold">Структура расходов · {expensePeriod.from} — {expensePeriod.to}</h2>
   <p className="mt-1 text-xs text-slate-500">Начисления F14 и прямые расходы; выплаты по начислениям не учитываются повторно.</p>
   {categories.isPending?<LoadingState label="Загружаем структуру расходов…"/>:
    categories.isError?<ErrorState error={categories.error} onRetry={()=>void categories.refetch()}/>:
    <div className="mt-3 space-y-3">
     {categories.data.categories.map(c=><div key={c.category} className="text-sm">
      <div className="flex justify-between gap-3"><strong>{expenseLabels.find(x=>x.value===c.category)?.label??c.category.replaceAll('_',' ')}</strong><strong>{som(c.total)}</strong></div>
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