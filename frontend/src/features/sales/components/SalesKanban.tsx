import { Link } from 'react-router';
import { useInfiniteQuery } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { salesApi, salesKeys, SALES_PAGE_SIZE } from '../api';
import { saleStatuses, saleStatusNames, saleDate, money } from '../validation';
import { Button } from '../../../shared/ui/button';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState';
import type { SaleFilters, SaleStatus } from '../types';

/**
 * Every column is independently server paginated. Never present page zero as
 * an exhaustive board; users can load all remaining rows for each status.
 */
function KanbanColumn({status,filters}: {status:SaleStatus;filters:SaleFilters}) {
  const {hasPermission}=useAuth();
  const q=useInfiniteQuery({
    queryKey:[...salesKeys.kanbanColumn(status),filters.from,filters.to,filters.number],
    initialPageParam:0,
    queryFn:({pageParam})=>salesApi.list({...filters,status,page:pageParam}),
    getNextPageParam:last=>last.size>0&&(last.page+1)*last.size<last.total?last.page+1:undefined,
  });
  const pages=q.data?.pages??[];
  const total=pages[0]?.total??0;
  const count=pages.reduce((n,p)=>n+p.items.length,0);
  const rows=pages.flatMap(p=>p.items);
  return <section aria-label={'Канбан: '+saleStatusNames[status]}
    className="min-w-0 rounded-2xl border border-slate-200 bg-slate-50 p-3 lg:min-w-72 lg:flex-1">
    <div className="mb-3 flex items-center justify-between gap-2">
      <h3 className="font-bold text-slate-800">{saleStatusNames[status]}</h3>
      <span className="text-sm text-slate-600">{count}/{total}</span>
    </div>
    {q.isPending?<LoadingState label="Заказы…"/>:
      q.isError?<ErrorState error={q.error} onRetry={()=>void q.refetch()}/>:
      rows.length===0?<p className="py-5 text-center text-sm text-slate-500">Нет заказов</p>:
      <div className="space-y-3">{rows.map(s=><article key={s.id}
        className="space-y-2 rounded-xl border border-slate-200 bg-white p-3 text-sm shadow-sm">
        <Link className="font-bold text-indigo-700 hover:underline"
          state={{summary:s}} to={'/sales/'+encodeURIComponent(s.id)}>{s.saleNumber}</Link>
        <p>{s.customerName||s.customerId||'Без клиента'} · {saleDate(s.createdAt)}</p>
        <p className="break-words text-slate-600">{s.products||'Позиции не указаны'}</p>
        <p>Шт.: {s.quantity} · <strong>{money(s.total)}</strong></p>
        <p>Оплата: {s.paymentStatus||'—'}</p>
        <p>Доставка: {s.deliveryStatus||'—'}</p>
        {s.plannedDeliveryDate&&<p>План: {s.plannedDeliveryDate}</p>}
        <Link to={'/sales/'+encodeURIComponent(s.id)} state={{summary:s}}
          className="inline-flex min-h-10 items-center font-semibold text-indigo-700 underline">
          {status==='DRAFT'&&hasPermission('SALES_DRAFT_MANAGE')?'Открыть и подтвердить':
            status==='CONFIRMED'&&hasPermission('SALES_UPDATE')&&s.fulfillmentType==='SELF_PICKUP'?'Открыть и завершить':
            'Открыть заказ'}</Link>
      </article>)}</div>}
    {q.hasNextPage&&<Button className="mt-3 w-full" variant="outline" disabled={q.isFetchingNextPage}
      onClick={()=>void q.fetchNextPage()}>{q.isFetchingNextPage?'Загружаем…':'Показать ещё'}</Button>}
    {total>count&&<p className="mt-2 text-xs text-slate-500">Показано {count} из {total}; оставшиеся доступны через «Показать ещё».</p>}
  </section>;
}
export function SalesKanban({filters}:{filters:SaleFilters}) {
  return <div className="space-y-3">
    <p className="text-sm text-slate-600">
      Kanban загружает реальные заказы по статусам с отдельной серверной пагинацией.
      Изменение статуса выполняется через карточку заказа и подтверждение,
      без перетаскивания или фиктивных переходов.
    </p>
    <div aria-label="Доска продаж" className="grid gap-3 sm:grid-cols-2 lg:flex lg:items-start">
      {saleStatuses.map(status=><KanbanColumn key={status} status={status} filters={filters}/>)}
    </div>
  </div>;
}
