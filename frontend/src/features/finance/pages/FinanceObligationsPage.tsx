import {useQuery} from '@tanstack/react-query';
import {Link} from 'react-router';
import {useAuth} from '../../auth/AuthProvider';
import {reportingApi} from '../reporting-api';
import {som} from '../validation';
import {FinanceTabs} from '../components/FinanceTabs';
import {PageHeader} from '../../../shared/components/PageHeader';
import {LoadingState} from '../../../shared/components/LoadingState';
import {ErrorState} from '../../../shared/components/ErrorState';
import {ForbiddenPage} from '../../../pages/ForbiddenPage';
export function FinanceObligationsPage(){
 const {hasPermission}=useAuth(),allowed=hasPermission('FINANCE_READ');
 const q=useQuery({queryKey:['f15','obligations'],queryFn:reportingApi.obligations,enabled:allowed});
 const stock=useQuery({queryKey:['f15','stock'],queryFn:reportingApi.valuation,enabled:allowed});
 if(!allowed)return <ForbiddenPage/>;
 return <div className="min-w-0 max-w-full space-y-5 overflow-x-hidden pb-10">
  <PageHeader title="Закупки и обязательства" description="Долги и авансы поставщикам отдельно от текущей стоимости запасов"/>
  <FinanceTabs/>
  {q.isPending?<LoadingState label="Загружаем обязательства…"/>:q.isError?<ErrorState error={q.error} onRetry={()=>void q.refetch()}/>:
  <><div className="grid grid-cols-2 gap-3 lg:grid-cols-3">
   {([['Задолженность',q.data.totalOutstanding],['Авансы',q.data.totalPrepaid]] as const).map(([name,value])=><article key={name} className="rounded-2xl border bg-white p-4"><p className="text-sm text-slate-500">{name}</p><strong className="mt-2 block break-words text-xl">{som(value)}</strong></article>)}
   {stock.isSuccess&&<article className="rounded-2xl border bg-white p-4"><p className="text-sm text-slate-500">Запасы FIFO сейчас</p><strong className="mt-2 block break-words text-xl">{som(stock.data.totalCost)}</strong><p className="text-xs text-slate-500">{stock.data.quantity} шт.</p></article>}
  </div>
  <section className="min-w-0 max-w-full overflow-hidden rounded-2xl border bg-white"><h2 className="p-4 font-semibold">Обязательства по закупкам</h2>
   <div className="w-full min-w-0 max-w-full overflow-x-auto"><table className="w-full min-w-[630px] text-left text-sm"><thead className="bg-slate-50"><tr>{['Закупка / поставщик','Статус','Стоимость поставки','Карго','Оплачено поставщику','Оплачено карго','Долг','Аванс'].map(k=><th key={k} className="p-3">{k}</th>)}</tr></thead>
   <tbody>{q.data.purchases.map(x=><tr key={x.purchaseId} className="border-t">
    <td className="p-3"><span className="block font-semibold">{x.supplierName}</span><span className="block text-xs text-slate-500">{new Date(x.createdAt).toLocaleDateString('ru-RU',{timeZone:'Asia/Bishkek'})}</span>{hasPermission('PURCHASE_READ')&&<Link to={'/purchases/'+encodeURIComponent(x.purchaseId)} className="text-xs text-blue-700 underline">Открыть закупку →</Link>}</td><td className="p-3">{x.status}</td><td className="p-3">{som(x.supplierTotal)}</td><td className="p-3">{som(x.cargoTotal)}</td><td className="p-3">{som(x.supplierPaid)}</td>
    <td className="p-3">{som(x.cargoPaid)}</td><td className="p-3 font-semibold">{som(x.outstanding)}</td><td className="p-3">{som(x.prepaid)}</td>
   </tr>)}</tbody></table></div>{!q.data.purchases.length&&<p className="p-4 text-slate-500">Обязательств нет</p>}
  </section></>}
  <p className="text-xs text-slate-600">Показатели текущие, не архивные остатки на выбранную дату. Покупка товара не равна себестоимости реализованного товара.</p>
 </div>;
}