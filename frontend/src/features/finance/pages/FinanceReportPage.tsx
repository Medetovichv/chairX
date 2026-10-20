import {useState,type FormEvent} from 'react';
import {Link} from 'react-router';
import {useQuery} from '@tanstack/react-query';
import {useAuth} from '../../auth/AuthProvider';
import {reportingApi} from '../reporting-api';
import {financeApi} from '../api';
import {useFinancePeriod} from '../useFinancePeriod';
import {som,periodMonth,validPeriod} from '../validation';
import {FinanceTabs} from '../components/FinanceTabs';
import {PageHeader} from '../../../shared/components/PageHeader';
import {LoadingState} from '../../../shared/components/LoadingState';
import {ErrorState} from '../../../shared/components/ErrorState';
import {ForbiddenPage} from '../../../pages/ForbiddenPage';
import {Input} from '../../../shared/ui/input';
import {Button} from '../../../shared/ui/button';

const money=(v:string|number|null|undefined)=>v==null?'Недоступно':som(v);
const typeNames:Record<string,string>={
 SALE_PAYMENT:'Платёж клиента',PAYMENT_REVERSAL:'Отмена платежа',
 CUSTOMER_REFUND:'Возврат клиенту',PURCHASE_PAYMENT:'Оплата закупки',
 EXPENSE:'Расход',TRANSFER:'Перевод между счетами',OPENING_BALANCE:'Начальный остаток'
};
const card=(name:string,value:string,caption?:string)=><article key={name} className="min-w-0 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
 <p className="text-sm text-slate-500">{name}</p><p className="mt-2 break-words text-xl font-bold tabular-nums">{value}</p>{caption&&<p className="mt-1 text-xs text-slate-500">{caption}</p>}
</article>;
export function FinanceReportPage(){
 const {hasPermission}=useAuth(),allowed=hasPermission('FINANCE_READ');
 const [period,setPeriod]=useFinancePeriod();
 const [draft,setDraft]=useState(period);
 const [error,setError]=useState(''),[page,setPage]=useState(0),[account,setAccount]=useState('');
 const pnl=useQuery({queryKey:['reporting','pnl',period.from,period.to],queryFn:()=>reportingApi.profitability(period.from,period.to),enabled:allowed});
 const flow=useQuery({queryKey:['reporting','flow',period.from,period.to],queryFn:()=>financeApi.flow(period.from,period.to),enabled:allowed});
 const ledger=useQuery({queryKey:['reporting','ledger',period.from,period.to,page,account],queryFn:()=>reportingApi.ledger(period.from,period.to,page,account),enabled:allowed});
 const margins=useQuery({queryKey:['reporting','margins',period.from,period.to],queryFn:()=>reportingApi.margins(period.from,period.to),enabled:allowed});
 const valuation=useQuery({queryKey:['reporting','valuation'],queryFn:reportingApi.valuation,enabled:allowed});
 const obligations=useQuery({queryKey:['reporting','obligations'],queryFn:reportingApi.obligations,enabled:allowed});
 const year=period.from.slice(0,4),analytics=useQuery({queryKey:['reporting','analytics',year],queryFn:()=>reportingApi.analytics(year+'-01-01',String(Number(year)+1)+'-01-01'),enabled:allowed});
 function submit(e:FormEvent){e.preventDefault();if(!validPeriod(draft.from,draft.to)){setError('Выберите корректный период не длиннее 93 дней.');return;}setError('');setPage(0);setPeriod({...draft});}
 if(!allowed)return <ForbiddenPage/>;
 return <div className="space-y-5 pb-10">
  <PageHeader title="Финансовый отчёт" description="Прибыль, денежные потоки, склад и обязательства — отдельно, без двойного учёта"/>
  <FinanceTabs/>
  <form onSubmit={submit} className="flex flex-wrap items-end gap-3 rounded-2xl border bg-white p-4">
   <label className="text-sm">С (включительно)<Input aria-label="Отчёт с даты" type="date" value={draft.from} onChange={e=>setDraft(d=>({...d,from:e.target.value}))}/></label>
   <label className="text-sm">По (исключительно)<Input aria-label="Отчёт по дату" type="date" value={draft.to} onChange={e=>setDraft(d=>({...d,to:e.target.value}))}/></label>
   <Button type="submit">Применить</Button>{error&&<p role="alert" className="text-red-700">{error}</p>}
  </form>
  <section aria-label="Финансовые показатели" className="grid grid-cols-2 gap-3 lg:grid-cols-4">
   {pnl.isSuccess?<>
    {card('Признанная выручка',money(pnl.data.netRevenue))}
    {card('FIFO себестоимость',money(pnl.data.verifiedFifoCost),pnl.data.completeCost?'Подтверждена':'Есть неподтверждённые списания')}
    {card('Валовая прибыль',pnl.data.netProfitAvailable?money(pnl.data.grossProfit):'Недоступно')}
    {card('Операционная прибыль',pnl.data.netProfitAvailable?money(pnl.data.operatingProfit):'Недоступно')}
    {card('Начисленные расходы F14',money(pnl.data.recognizedAccruals))}
    {card('Прочие расходы',money(pnl.data.otherOperatingExpenses))}
   </>:pnl.isError?<ErrorState error={pnl.error} onRetry={()=>void pnl.refetch()}/>:<LoadingState label="Загружаем прибыль…"/>}
   {flow.isSuccess?<>{card('Поступления денег',money(flow.data.totalIn))}{card('Денежные выплаты',money(flow.data.totalOut))}</>:flow.isError?<ErrorState error={flow.error} onRetry={()=>void flow.refetch()}/>:<LoadingState label="Загружаем денежные потоки…"/>}
  </section>
  {pnl.isSuccess&&!pnl.data.netProfitAvailable&&<p role="alert" className="rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm">Прибыль не подтверждена. {pnl.data.caveat||'Требуется сверка FIFO или возвратов.'} Денежный поток не используется вместо прибыли.</p>}
  <section className="rounded-2xl border bg-white p-4">
   <h2 className="mb-3 font-semibold">Отчёт о прибыли и убытках (P&L)</h2>
   {pnl.isSuccess?<div className="space-y-2 text-sm">{([
    ['Выручка до корректировок',pnl.data.grossRevenue],
    ['Возвраты товаров',pnl.data.returnedRevenue],
    ['Возвраты без товара / скидки',pnl.data.refundOnlyDiscounts],
    ['Чистая выручка',pnl.data.netRevenue],
    ['FIFO стоимость',pnl.data.verifiedFifoCost],
    ['Восстановленная стоимость возвратов',pnl.data.restoredFifoCost],
    ['Валовая прибыль',pnl.data.grossProfit],
    ['Начисления по периодам',pnl.data.recognizedAccruals],
    ['Прочие операционные расходы',pnl.data.otherOperatingExpenses],
    ['Операционная прибыль',pnl.data.operatingProfit]] as const).map(([name,value])=>
    <div key={name} className="flex justify-between gap-3 border-b border-slate-100 py-2"><span>{name}</span><strong className="text-right tabular-nums">{money(value)}</strong></div>)}</div>:<p className="text-sm text-slate-500">Данные P&L загружаются отдельно от денежных проводок.</p>}
  </section>
  {pnl.isSuccess&&<details className="rounded-2xl border bg-white p-4">
   <summary className="cursor-pointer font-semibold">Признанные продажи и FIFO — расшифровка ({pnl.data.sales.length})</summary>
   <p className="mt-2 text-xs text-slate-500">Выручка признаётся по фактическому исполнению заказа, независимо от даты оплаты. Историческая себестоимость берётся из FIFO allocations.</p>
   <div className="mt-3 overflow-x-auto"><table className="w-full min-w-[520px] text-left text-sm">
    <thead className="bg-slate-50"><tr><th className="p-3">Заказ</th><th className="p-3 text-right">Выручка</th><th className="p-3 text-right">FIFO стоимость</th><th className="p-3">Проверка</th></tr></thead>
    <tbody>{pnl.data.sales.map(sale=><tr key={sale.saleId} className="border-t">
     <td className="p-3">{hasPermission('SALES_READ')?<Link to={'/sales/'+encodeURIComponent(sale.saleId)} className="text-blue-700 underline">{sale.saleNumber}</Link>:sale.saleNumber}</td>
     <td className="p-3 text-right">{money(sale.recognizedRevenue)}</td><td className="p-3 text-right">{sale.completeCost?money(sale.fifoCost):'Недоступно'}</td>
     <td className="p-3">{sale.completeCost?'Подтверждено':'Не проверено'}</td>
    </tr>)}</tbody>
   </table>{!pnl.data.sales.length&&<p className="p-3 text-sm text-slate-500">Признанных продаж за период нет.</p>}</div>
  </details>}
  <section className="overflow-hidden rounded-2xl border bg-white">
   <div className="flex flex-wrap items-center justify-between gap-3 p-4"><div><h2 className="font-semibold">Все денежные операции</h2><p className="text-xs text-slate-500">Проводки, включая внутренние переводы; это не P&L.</p></div>
    <label className="text-sm">Счёт <select className="rounded-lg border p-2" value={account} onChange={e=>{setAccount(e.target.value);setPage(0);}}><option value="">Все</option><option value="CASH">Наличные</option><option value="BANK">Банк</option></select></label></div>
   {ledger.isPending?<LoadingState label="Загружаем журнал…"/>:ledger.isError?<ErrorState error={ledger.error} onRetry={()=>void ledger.refetch()}/>:
    <><div className="overflow-x-auto"><table aria-label="Журнал всех операций" className="w-full min-w-[620px] text-left text-sm"><thead className="bg-slate-50"><tr>{['Дата','Операция','Счёт','Источник','Сумма'].map(x=><th key={x} className="p-3">{x}</th>)}</tr></thead><tbody>{ledger.data.items.map(row=><tr key={row.id} className="border-t"><td className="p-3">{new Date(row.postedAt).toLocaleString('ru-RU',{timeZone:'Asia/Bishkek'})}</td><td className="p-3">{typeNames[row.type]||row.type.replaceAll('_',' ')}</td><td className="p-3">{row.account==='CASH'?'Наличные':'Банк'}</td><td className="p-3">{row.sourceType.replaceAll('_',' ')}</td><td className="p-3 text-right font-semibold">{money(row.amount)}</td></tr>)}</tbody></table>{ledger.data.items.length===0&&<p className="p-4 text-slate-500">Операций за период нет</p>}</div>
    <div className="flex flex-wrap items-center justify-between gap-3 border-t p-3 text-sm"><span>Всего: {ledger.data.total}</span><div className="flex gap-2"><Button variant="outline" disabled={page===0} onClick={()=>setPage(p=>p-1)}>Назад</Button><Button variant="outline" disabled={(page+1)*50>=ledger.data.total} onClick={()=>setPage(p=>p+1)}>Далее</Button></div></div></>}
  </section>
  <section className="grid gap-4 lg:grid-cols-2">
   <div className="rounded-2xl border bg-white p-4"><h2 className="font-semibold">Маржинальность кресел</h2>{margins.isSuccess?<div className="mt-3 space-y-2 text-sm">{margins.data.map(m=><div key={m.variantId} className="flex flex-wrap justify-between gap-2 border-b py-2"><span>{m.model} / {m.variation} · {m.quantity} шт.</span><strong>{money(m.grossProfit)}</strong></div>)}{margins.data.length===0&&'Продаж за период нет'}</div>:margins.isError?<ErrorState error={margins.error} onRetry={()=>void margins.refetch()}/>:<LoadingState label="Загружаем модели…"/>}</div>
   <div className="rounded-2xl border bg-white p-4"><h2 className="font-semibold">Склад и обязательства сейчас</h2>
    {valuation.isSuccess&&card('FIFO стоимость остатков',money(valuation.data.totalCost),valuation.data.quantity+' кресел')}{obligations.isSuccess&&<div className="mt-3 grid grid-cols-2 gap-3">{card('Долг поставщикам',money(obligations.data.totalOutstanding))}{card('Авансы',money(obligations.data.totalPrepaid))}</div>}
    {(valuation.isError||obligations.isError)&&<p role="alert" className="text-red-700">Не удалось загрузить текущие остатки или обязательства.</p>}
    <p className="mt-3 text-xs text-slate-500">Оценка склада и обязательства — на текущий момент, не исторический срез.</p>
   </div>
  </section>
  <section className="rounded-2xl border bg-white p-4"><h2 className="font-semibold">Помесячная динамика · {year}</h2>
   {analytics.isSuccess?<div className="mt-3 space-y-2">{analytics.data.months.map(m=><div className="grid grid-cols-[70px_1fr_1fr] items-center gap-2 text-sm" key={m.from}><span>{m.from.slice(5,7)}.{year.slice(2)}</span><div className="min-w-0 rounded-lg bg-blue-50 p-2">
      <div className="mb-1 truncate">Выручка: {money(m.netRevenue)}</div>
      <div role="img" aria-label={'Выручка '+money(m.netRevenue)} className="h-2 rounded bg-blue-100">
       <div className="h-2 rounded bg-blue-600" style={{width:(()=>{
        const amount=Math.max(0,Number(m.netRevenue));
        const highest=Math.max(1,...analytics.data.months.map(item=>Math.max(0,Number(item.netRevenue))));
        return (Number.isFinite(amount)&&Number.isFinite(highest)?Math.min(100,amount/highest*100):0)+'%';
       })()}}/>
      </div>
     </div>
     <span className="min-w-0 break-words rounded-lg bg-slate-50 p-2">Прибыль: {m.profitVerified?money(m.operatingProfit):'Недоступно'}</span></div>)}</div>:analytics.isError?<ErrorState error={analytics.error} onRetry={()=>void analytics.refetch()}/>:<LoadingState label="Загружаем динамику…"/>}
  </section>
  <p className="text-sm text-slate-600">Для подробностей используйте <Link className="text-blue-700 underline" to="/finance/expenses">Расходы</Link>, <Link className="text-blue-700 underline" to="/purchases">Закупки</Link> и <Link className="text-blue-700 underline" to="/finance/cash-flow">Cash Flow</Link>.</p>
 </div>;
}
