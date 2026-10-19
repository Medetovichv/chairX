import { Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { financeApi,financeKeys } from '../api';
import { som,periodMonth } from '../validation';
import { FinanceTabs } from '../components/FinanceTabs';
import { PageHeader } from '../../../shared/components/PageHeader';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState';
import { ForbiddenPage } from '../../../pages/ForbiddenPage';
import { Button } from '../../../shared/ui/button';
export function FinanceOverviewPage(){
 const {hasPermission,user}=useAuth();
 const allowed=hasPermission('FINANCE_READ'),period=periodMonth();
 const accounts=useQuery({queryKey:financeKeys.accounts,queryFn:financeApi.accounts,enabled:allowed});
 const flow=useQuery({queryKey:financeKeys.flowPeriod(period.from,period.to),
   queryFn:()=>financeApi.flow(period.from,period.to),enabled:allowed});
 if(!allowed)return <ForbiddenPage/>;
 return <div className="space-y-6">
  <PageHeader title="Финансы" description="Фактические остатки и движение денег ChairX в кыргызских сомах."/>
  <FinanceTabs/>
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
   <Button asChild><Link to="/finance/report">Финансовый отчёт →</Link></Button>
   {hasPermission('EXPENSES_READ')&&<Button variant="outline" asChild><Link to="/finance/expenses">Расходы</Link></Button>}
   <Button variant="outline" asChild><Link to="/finance/cash-flow">Движение денег</Link></Button>
   {hasPermission('FINANCE_TRANSFER')&&<Button asChild><Link to="/finance/transfers">Перевести CASH / BANK</Link></Button>}
   {user?.roles.includes('ADMIN')&&hasPermission('FINANCE_INITIALIZE')&&<Button variant="outline" asChild><Link to="/finance/opening-balances">Начальные остатки</Link></Button>}
  </div>
 </div>;
}
