import {useState} from 'react';
import {Link} from 'react-router';
import {useQuery} from '@tanstack/react-query';
import {useAuth} from '../../auth/AuthProvider';
import {reportingApi} from '../reporting-api';
import {som} from '../validation';
import {useFinancePeriod} from '../useFinancePeriod';
import {FinanceTabs} from '../components/FinanceTabs';
import {PageHeader} from '../../../shared/components/PageHeader';
import {LoadingState} from '../../../shared/components/LoadingState';
import {ErrorState} from '../../../shared/components/ErrorState';
import {ForbiddenPage} from '../../../pages/ForbiddenPage';
import {Button} from '../../../shared/ui/button';
import {FinanceExportButton} from '../components/FinanceExportButton';
const labels:Record<string,string>={SALE_PAYMENT:'Оплата продажи',PURCHASE_PAYMENT:'Оплата закупки',EXPENSE:'Расход',CUSTOMER_REFUND:'Возврат клиенту',PAYMENT_REVERSAL:'Отмена платежа',TRANSFER:'Перевод между счетами'};
// Section fallback is deliberately not described as a document deep link.
function sourceLink(kind:string|null,id:string|null,hasPermission:(permission:string)=>boolean){
 if(!id || !kind || !['EXPENSE','TRANSFER','ADMIN_ADJUSTMENT','OPENING_BALANCE'].includes(kind))return null;
 if(kind==='EXPENSE'&&!hasPermission('EXPENSES_READ'))return null;
 return '/finance/documents/'+encodeURIComponent(kind)+'/'+encodeURIComponent(id);
}
function relatedSection(line: {type:string;sourceType:string},hasPermission:(permission:string)=>boolean){
 if(line.type==='EXPENSE' && hasPermission('EXPENSES_READ'))return {to:'/finance/expenses',label:'Перейти к расходам'};
 if(line.type==='TRANSFER' && hasPermission('FINANCE_READ'))return {to:'/finance/transfers',label:'Перейти к переводам'};
 if(line.type==='CUSTOMER_REFUND' && hasPermission('RETURNS_READ'))return {to:'/returns',label:'Перейти к возвратам'};
 return null;
}
export function FinanceOperationsPage(){
 const {hasPermission}=useAuth(),allowed=hasPermission('FINANCE_READ');
 const [period,setPeriod]=useFinancePeriod();
 const [page,setPage]=useState(0),[account,setAccount]=useState(''),[movementType,setMovementType]=useState('');
 const [search,setSearch]=useState(''),[minAmount,setMinAmount]=useState(''),[maxAmount,setMaxAmount]=useState(''),[sort,setSort]=useState('newest');
 const [selected,setSelected]=useState<string|null>(null);
 const report=useQuery({queryKey:['f15','ledger',period.from,period.to,page,account,movementType,search,minAmount,maxAmount,sort],
 queryFn:()=>reportingApi.ledger(period.from,period.to,page,account,movementType,search,minAmount,maxAmount,sort),enabled:allowed});
 if(!allowed)return <ForbiddenPage/>;
 const line=report.data?.items.find(item=>item.id===selected);
 return <div className="w-full min-w-0 max-w-full space-y-5 overflow-x-hidden pb-10">
  <PageHeader title="Все операции" description="Реальные денежные проводки; внутренние переводы не считаются выручкой"/>
  <FinanceTabs/>
  <div className="flex flex-wrap items-end gap-3 rounded-2xl border bg-white p-4">
   <label className="text-sm">С <input aria-label="Операции с" className="block rounded-lg border p-2" type="date" value={period.from} onChange={e=>{setPeriod({...period,from:e.target.value});setPage(0);}}/></label>
   <label className="text-sm">По (исключительно) <input aria-label="Операции по" className="block rounded-lg border p-2" type="date" value={period.to} onChange={e=>{setPeriod({...period,to:e.target.value});setPage(0);}}/></label>
   <label className="text-sm">Счёт <select aria-label="Счёт операций" className="block rounded-lg border p-2" value={account} onChange={e=>{setAccount(e.target.value);setPage(0);}}><option value="">Все</option><option value="CASH">Наличные</option><option value="BANK">Банк</option></select></label>
   <label className="text-sm">Тип операции
    <select aria-label="Тип финансовой операции" className="block rounded-lg border p-2" value={movementType} onChange={e=>{setMovementType(e.target.value);setPage(0);}}>
     <option value="">Все типы</option>
     <option value="SALE_PAYMENT">Оплата продажи</option>
     <option value="PURCHASE_PAYMENT">Оплата закупки</option>
     <option value="EXPENSE">Расход</option>
     <option value="CUSTOMER_REFUND">Возврат клиенту</option>
     <option value="TRANSFER">Внутренний перевод</option>
    </select></label>
   <label className="text-sm">Поиск
    <input aria-label="Поиск финансовых операций" maxLength={100} className="block min-h-10 w-full rounded-lg border p-2" placeholder="Тип, сотрудник, источник" value={search} onChange={e=>{setSearch(e.target.value);setPage(0);}}/>
   </label>
   <label className="text-sm">Сумма от
    <input aria-label="Минимальная сумма" type="number" step="0.01" className="block w-32 rounded-lg border p-2" value={minAmount} onChange={e=>{setMinAmount(e.target.value);setPage(0);}}/>
   </label>
   <label className="text-sm">Сумма до
    <input aria-label="Максимальная сумма" type="number" step="0.01" className="block w-32 rounded-lg border p-2" value={maxAmount} onChange={e=>{setMaxAmount(e.target.value);setPage(0);}}/>
   </label>
   <label className="text-sm">Сортировать
    <select aria-label="Сортировка операций" className="block rounded-lg border p-2" value={sort} onChange={e=>{setSort(e.target.value);setPage(0);}}>
     <option value="newest">Сначала новые</option><option value="oldest">Сначала старые</option><option value="amountAsc">По сумме ↑</option><option value="amountDesc">По сумме ↓</option>
    </select>
   </label>
   <FinanceExportButton from={period.from} to={period.to} account={account} movementType={movementType} search={search} minAmount={minAmount} maxAmount={maxAmount} sort={sort}/>
  </div>
  {report.isPending?<LoadingState label="Загружаем проводки…"/>:report.isError?<ErrorState error={report.error} onRetry={()=>void report.refetch()}/>:
  <section aria-label="Журнал денежных операций" className="overflow-hidden rounded-2xl border bg-white">
   <div className="hidden overflow-x-auto md:block"><table className="w-full text-left text-sm"><thead className="bg-slate-50"><tr>{['Дата','Тип','Счёт','Источник','Сумма'].map(k=><th className="p-3" key={k}>{k}</th>)}</tr></thead>
   <tbody>{report.data.items.map(x=><tr key={x.id} className="cursor-pointer border-t hover:bg-blue-50" onClick={()=>setSelected(x.id)}>
    <td className="p-3">{new Date(x.postedAt).toLocaleString('ru-RU',{timeZone:'Asia/Bishkek'})}</td>
    <td className="p-3">{labels[x.type]||x.type.replaceAll('_',' ')}</td>
    <td className="p-3">{x.account==='CASH'?'Наличные':'Банк'}</td><td className="p-3">{x.documentLabel||x.sourceType.replaceAll('_',' ')}{x.documentKind==='SALE'&&x.documentId&&hasPermission('SALES_READ')&&
     <Link to={'/sales/'+encodeURIComponent(x.documentId)} onClick={event=>event.stopPropagation()} className="ml-2 text-blue-700 underline">Открыть заказ</Link>}
    {x.documentKind==='RETURN'&&x.documentId&&hasPermission('RETURNS_READ')&&
     <Link to={'/returns/'+encodeURIComponent(x.documentId)} onClick={event=>event.stopPropagation()} className="ml-2 text-blue-700 underline">Открыть возврат</Link>}
    {x.documentKind==='PURCHASE'&&x.documentId&&hasPermission('PURCHASE_READ')&&
     <Link to={'/purchases/'+encodeURIComponent(x.documentId)} onClick={event=>event.stopPropagation()} className="ml-2 text-blue-700 underline">Открыть закупку</Link>}</td>
    {sourceLink(x.documentKind,x.documentId,hasPermission)&&<Link to={sourceLink(x.documentKind,x.documentId,hasPermission)!} onClick={event=>event.stopPropagation()} className="ml-2 text-blue-700 underline">Открыть документ</Link>}
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
    <div><dt className="text-slate-500">Источник</dt><dd>{line.documentLabel||line.sourceType}</dd></div>
    {line.documentKind==='SALE'&&line.documentId&&hasPermission('SALES_READ')&&
     <Link to={'/sales/'+encodeURIComponent(line.documentId)} className="text-blue-700 underline">Открыть заказ →</Link>}
    {line.documentKind==='RETURN'&&line.documentId&&hasPermission('RETURNS_READ')&&
     <Link to={'/returns/'+encodeURIComponent(line.documentId)} className="text-blue-700 underline">Открыть возврат →</Link>}
    {line.documentKind==='PURCHASE'&&line.documentId&&hasPermission('PURCHASE_READ')&&
     <Link to={'/purchases/'+encodeURIComponent(line.documentId)} className="text-blue-700 underline">Открыть закупку →</Link>}
    {sourceLink(line.documentKind,line.documentId,hasPermission)&&
     <Link to={sourceLink(line.documentKind,line.documentId,hasPermission)!} className="text-blue-700 underline">Открыть первичный документ →</Link>}
    {!line.documentId&&relatedSection(line,hasPermission)&&<Link to={relatedSection(line,hasPermission)!.to} className="block text-blue-700 underline">{relatedSection(line,hasPermission)!.label} →</Link>}
    <div><dt className="text-slate-500">Сумма</dt><dd className="font-bold">{som(line.amount)}</dd></div>
   </dl>
   <p className="mt-3 text-xs text-slate-500">Переход к документу проверяет права доступа. Если первичный документ отсутствует, доступна только навигация к соответствующему разделу.</p>
  </section>}
 </div>;
}