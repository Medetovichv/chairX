import { Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { financeApi,financeKeys } from '../api';
import {useFinancePeriod} from '../useFinancePeriod';
import { reportingApi } from '../reporting-api';
import { som } from '../validation';
import { FinanceTabs } from '../components/FinanceTabs';
import { PageHeader } from '../../../shared/components/PageHeader';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState';
import { ForbiddenPage } from '../../../pages/ForbiddenPage';
import { Button } from '../../../shared/ui/button';
export function FinanceOverviewPage(){
 const {hasPermission,user}=useAuth();
 const allowed=hasPermission('FINANCE_READ');
 const [period]=useFinancePeriod();
 const accounts=useQuery({queryKey:financeKeys.accounts,queryFn:financeApi.accounts,enabled:allowed});
 const pnl=useQuery({queryKey:['f15','overview','pnl',period.from,period.to],
   queryFn:()=>reportingApi.profitability(period.from,period.to),enabled:allowed});
 const ledger=useQuery({queryKey:['f15','overview','latest',period.from,period.to],
   queryFn:()=>reportingApi.ledger(period.from,period.to,0,''),enabled:allowed});
 const valuation=useQuery({queryKey:['f15','overview','valuation'],queryFn:reportingApi.valuation,enabled:allowed});
 const obligations=useQuery({queryKey:['f15','overview','obligations'],queryFn:reportingApi.obligations,enabled:allowed});
 const flow=useQuery({queryKey:financeKeys.flowPeriod(period.from,period.to),
   queryFn:()=>financeApi.flow(period.from,period.to),enabled:allowed});
 if(!allowed)return <ForbiddenPage/>;
 return <div className="space-y-6">
  <PageHeader title="Финансы" description="Фактические остатки и движение денег ChairX в кыргызских сомах."/>
  <FinanceTabs/>
  <section aria-label="Основные финансовые показатели" className="grid grid-cols-2 gap-3 lg:grid-cols-4">
    {pnl.isSuccess&&([
     ['Чистая выручка',som(pnl.data.netRevenue)],
     ['Себестоимость FIFO',som(pnl.data.verifiedFifoCost)],
     ['Валовая прибыль',pnl.data.netProfitAvailable&&pnl.data.grossProfit!=null?som(pnl.data.grossProfit):'Недоступно'],
     ['Операционная прибыль',pnl.data.netProfitAvailable&&pnl.data.operatingProfit!=null?som(pnl.data.operatingProfit):'Недоступно'],
     ['Операционные расходы',som(pnl.data.otherOperatingExpenses)],
     ['Начисленные расходы',som(pnl.data.recognizedAccruals)]
    ] as const).map(([title,value])=><article key={title} className="min-w-0 rounded-2xl border border-slate-100 bg-white p-4 shadow-sm">
     <p className="text-xs text-slate-500">{title}</p><p className="mt-2 break-words text-xl font-bold tabular-nums">{value}</p>
    </article>)}
    {flow.isSuccess&&([
     ['Поступления',som(flow.data.totalIn)],['Выплаты',som(flow.data.totalOut)]
    ] as const).map(([title,value])=><article key={title} className="min-w-0 rounded-2xl border border-slate-100 bg-white p-4 shadow-sm">
     <p className="text-xs text-slate-500">{title}</p><p className="mt-2 break-words text-xl font-bold tabular-nums">{value}</p></article>)}
    {(pnl.isPending||flow.isPending)&&<LoadingState label="Загружаем основные показатели…"/>}
    {(pnl.isError||flow.isError)&&<div role="alert" className="col-span-full rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">Часть финансовых показателей недоступна. <Button variant="outline" onClick={()=>{void pnl.refetch();void flow.refetch();}}>Повторить</Button></div>}
  </section>
  {pnl.isSuccess&&!pnl.data.netProfitAvailable&&<p role="status" className="rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm">Расчёт прибыли требует сверки: {pnl.data.caveat||'Не все исходные данные подтверждены'}. Денежный поток не заменяет прибыль.</p>}
  <div className="grid min-w-0 grid-cols-1 gap-4 lg:grid-cols-2">
   <section className="min-w-0 overflow-hidden rounded-2xl border bg-white p-4 shadow-sm"><div className="flex min-w-0 flex-wrap justify-between gap-3"><h2 className="font-bold">Текущие активы и обязательства</h2><Link to="/finance/obligations" className="text-sm text-blue-700">Подробнее →</Link></div>
    <div className="mt-3 min-w-0 space-y-2 break-words text-sm">{valuation.isSuccess&&<p>Стоимость склада FIFO: <strong>{som(valuation.data.totalCost)}</strong> · {valuation.data.quantity} кресел</p>}{obligations.isSuccess&&<><p>Долг поставщикам: <strong>{som(obligations.data.totalOutstanding)}</strong></p><p>Авансы: <strong>{som(obligations.data.totalPrepaid)}</strong></p></>}{(valuation.isError||obligations.isError)&&<p role="alert">Данные активов или обязательств временно недоступны.</p>}</div>
    <p className="mt-3 text-xs text-slate-500">Показатели на текущий момент, а не на конец выбранного месяца.</p>
   </section>
   <section className="min-w-0 overflow-hidden rounded-2xl border bg-white p-4 shadow-sm"><div className="flex justify-between gap-3"><h2 className="font-bold">Последние денежные операции</h2><Link to="/finance/operations" className="text-sm text-blue-700">Все операции →</Link></div>
    {ledger.isSuccess?<div className="mt-2 min-w-0 divide-y">{ledger.data.items.slice(0,5).map(item=><div key={item.id} className="flex justify-between gap-2 py-2 text-sm"><span className="min-w-0 truncate">{item.type.replaceAll('_',' ')} · {new Date(item.postedAt).toLocaleDateString('ru-RU',{timeZone:'Asia/Bishkek'})}</span><strong>{som(item.amount)}</strong></div>)}{!ledger.data.items.length&&<p className="text-sm text-slate-500">Проводок пока нет.</p>}</div>:ledger.isError?<p role="alert" className="text-sm text-rose-700">Не удалось загрузить журнал.</p>:<LoadingState label="Загружаем последние операции…"/>}
   </section>
  </div>
  {accounts.isPending?<LoadingState label="Загружаем финансовые счета…"/>:
   accounts.isError?<ErrorState error={accounts.error} onRetry={()=>void accounts.refetch()}/>:
   <div className="grid gap-4 sm:grid-cols-2">
   {(['CASH','BANK'] as const).map(code=>{
    const d=accounts.data.find(x=>x.code===code);
    return <article key={code} className="space-y-2 rounded-2xl border bg-white p-5">
      <h3 className="font-bold">{code} — {code==='CASH'?'Наличные':'Банковский счёт'}</h3>
      {d?<><p className="text-2xl font-bold">{som(d.balance)}</p>
       <p className={'text-sm '+(d.initialized?'text-emerald-700':'text-amber-800')}>
         {d.initialized?'Инициализирован':'Не инициализирован — операции недоступны'}</p></>:
       <p className="text-sm text-red-700">Счёт отсутствует в ответе backend.</p>}
    </article>;
   })}</div>}
  <section aria-label="Денежное движение за месяц" className="space-y-3 rounded-2xl border bg-white p-5">
    <h3 className="font-bold">Денежное движение за текущий месяц</h3>
    <p className="text-sm text-slate-600">Период: {period.from} — {period.to} (верхняя граница не включается)</p>
    {flow.isPending?<LoadingState label="Загружаем агрегаты…"/>:
     flow.isError?<ErrorState error={flow.error} onRetry={()=>void flow.refetch()}/>:
     <div className="grid gap-3 sm:grid-cols-3">
      {([['Поступления',flow.data.totalIn],['Выплаты',flow.data.totalOut],
        ['Чистое движение денег',flow.data.netCashFlow]] as const).map(([title,value])=>
        <div key={title} className="rounded-xl bg-slate-50 p-4">
         <p className="text-sm">{title}</p><p className="text-lg font-bold">{som(value)}</p>
        </div>)}
     </div>}
    <p className="text-xs text-slate-600">Cash Flow — движение денег, а не выручка или прибыль.</p>
  </section>
  <div className="flex flex-wrap gap-2">
   <Button asChild><Link className="max-w-full min-w-0 whitespace-normal break-words text-center" to="/finance/report">Открыть финансовый отчёт →</Link></Button>
   {hasPermission('EXPENSES_READ')&&<Button variant="outline" asChild><Link to="/finance/expenses">Расходы</Link></Button>}
   <Button variant="outline" asChild><Link to="/finance/cash-flow">Движение денег</Link></Button>
   {hasPermission('FINANCE_TRANSFER')&&<Button asChild><Link to="/finance/transfers">Перевести CASH / BANK</Link></Button>}
   {user?.roles.includes('ADMIN')&&hasPermission('FINANCE_INITIALIZE')&&<Button variant="outline" asChild><Link to="/finance/opening-balances">Начальные остатки</Link></Button>}
  </div>
 </div>;
}
