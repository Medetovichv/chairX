import {useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {useAuth} from '../../auth/AuthProvider';
import {reportingApi} from '../reporting-api';
import {periodMonth,som} from '../validation';
import {FinanceTabs} from '../components/FinanceTabs';
import {PageHeader} from '../../../shared/components/PageHeader';
import {LoadingState} from '../../../shared/components/LoadingState';
import {ErrorState} from '../../../shared/components/ErrorState';
import {ForbiddenPage} from '../../../pages/ForbiddenPage';
import {Button} from '../../../shared/ui/button';
const labels:Record<string,string>={SALE_PAYMENT:'Оплата продажи',PURCHASE_PAYMENT:'Оплата закупки',EXPENSE:'Расход',CUSTOMER_REFUND:'Возврат клиенту',PAYMENT_REVERSAL:'Отмена платежа',TRANSFER:'Перевод между счетами'};
export function FinanceOperationsPage(){
 const {hasPermission}=useAuth(),allowed=hasPermission('FINANCE_READ');
 const [period,setPeriod]=useState(periodMonth),[page,setPage]=useState(0),[account,setAccount]=useState('');
 const [selected,setSelected]=useState<string|null>(null);
 const report=useQuery({queryKey:['f15','ledger',period.from,period.to,page,account],
 queryFn:()=>reportingApi.ledger(period.from,period.to,page,account),enabled:allowed});
 if(!allowed)return <ForbiddenPage/>;
 const line=report.data?.items.find(item=>item.id===selected);
 return <div className="space-y-5 pb-10">
  <PageHeader title="Все операции" description="Реальные денежные проводки; внутренние переводы не считаются выручкой"/>
  <FinanceTabs/>
  <div className="flex flex-wrap items-end gap-3 rounded-2xl border bg-white p-4">
   <label className="text-sm">С <input aria-label="Операции с" className="block rounded-lg border p-2" type="date" value={period.from} onChange={e=>{setPeriod(p=>({...p,from:e.target.value}));setPage(0);}}/></label>
   <label className="text-sm">По (исключительно) <input aria-label="Операции по" className="block rounded-lg border p-2" type="date" value={period.to} onChange={e=>{setPeriod(p=>({...p,to:e.target.value}));setPage(0);}}/></label>
   <label className="text-sm">Счёт <select aria-label="Счёт операций" className="block rounded-lg border p-2" value={account} onChange={e=>{setAccount(e.target.value);setPage(0);}}><option value="">Все</option><option value="CASH">Наличные</option><option value="BANK">Банк</option></select></label>
  </div>
  {report.isPending?<LoadingState label="Загружаем проводки…"/>:report.isError?<ErrorState error={report.error} onRetry={()=>void report.refetch()}/>:
  <section aria-label="Журнал денежных операций" className="overflow-hidden rounded-2xl border bg-white">
   <div className="hidden overflow-x-auto md:block"><table className="w-full text-left text-sm"><thead className="bg-slate-50"><tr>{['Дата','Тип','Счёт','Источник','Сумма'].map(k=><th className="p-3" key={k}>{k}</th>)}</tr></thead>
   <tbody>{report.data.items.map(x=><tr key={x.id} className="cursor-pointer border-t hover:bg-blue-50" onClick={()=>setSelected(x.id)}>
    <td className="p-3">{new Date(x.postedAt).toLocaleString('ru-RU',{timeZone:'Asia/Bishkek'})}</td>
    <td className="p-3">{labels[x.type]||x.type.replaceAll('_',' ')}</td>
    <td className="p-3">{x.account==='CASH'?'Наличные':'Банк'}</td><td className="p-3">{x.sourceType.replaceAll('_',' ')}</td>
    <td className="p-3 text-right font-semibold">{som(x.amount)}</td>
   </tr>)}</tbody></table></div>
   <div className="divide-y md:hidden">{report.data.items.map(x=><button key={x.id} onClick={()=>setSelected(x.id)} className="flex w-full items-center justify-between gap-3 p-4 text-left">
    <span className="min-w-0"><strong className="block">{labels[x.type]||x.type.replaceAll('_',' ')}</strong><span className="text-xs text-slate-500">{new Date(x.postedAt).toLocaleString('ru-RU',{timeZone:'Asia/Bishkek'})} · {x.account==='CASH'?'Наличные':'Банк'}</span></span>
    <strong className="shrink-0">{som(x.amount)}</strong></button>)}</div>
   {!report.data.items.length&&<p className="p-5 text-sm text-slate-500">Нет проводок за период.</p>}
   <div className="flex items-center justify-between gap-3 border-t p-3 text-sm"><span>Всего: {report.data.total}</span>
    <div className="flex gap-2"><Button variant="outline" disabled={page===0} onClick={()=>setPage(p=>p-1)}>Назад</Button><Button variant="outline" disabled={(page+1)*50>=report.data.total} onClick={()=>setPage(p=>p+1)}>Далее</Button></div></div>
  </section>}
  {line&&<section role="dialog" aria-label="Детали финансовой операции" className="rounded-2xl border bg-white p-5 shadow-md">
   <div className="flex justify-between gap-3"><h2 className="font-semibold">Детали операции</h2><Button variant="outline" onClick={()=>setSelected(null)}>Закрыть</Button></div>
   <dl className="mt-3 space-y-2 text-sm">
    <div><dt className="text-slate-500">Операция</dt><dd>{labels[line.type]||line.type}</dd></div>
    <div><dt className="text-slate-500">Время</dt><dd>{new Date(line.postedAt).toLocaleString('ru-RU',{timeZone:'Asia/Bishkek'})}</dd></div>
    <div><dt className="text-slate-500">Счёт</dt><dd>{line.account}</dd></div>
    <div><dt className="text-slate-500">Источник</dt><dd>{line.sourceType}</dd></div>
    <div><dt className="text-slate-500">Сумма</dt><dd className="font-bold">{som(line.amount)}</dd></div>
   </dl>
   <p className="mt-3 text-xs text-slate-500">Переход к первичному документу доступен только после проверки типа источника и разрешений.</p>
  </section>}
 </div>;
}