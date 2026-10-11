import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { ApiClientError } from '../../../shared/api/api-error';
import { PageHeader } from '../../../shared/components/PageHeader';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState';
import { EmptyState } from '../../../shared/components/EmptyState';
import { ForbiddenPage } from '../../../pages/ForbiddenPage';
import { Button } from '../../../shared/ui/button';
import { purchaseApi, purchaseKeys } from '../api';
import { PurchaseTabs } from '../components/PurchaseTabs';
import { PurchasePager } from '../components/PurchasePager';
import { ReceivingForm } from '../components/ReceivingForm';
import { PurchaseStatusBadge } from '../components/PurchaseStatusBadge';
import { formatDate } from '../validation';
const validUUID=(value:string)=>/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);

export function ReceivingPage() {
  const { hasPermission } = useAuth();
  const canReceive = hasPermission('INVENTORY_RECEIVE');
  const [page, setPage] = useState(0);
  const [params,setParams]=useSearchParams();
  const linkId=params.get('purchaseId');
  const selected=linkId&&validUUID(linkId)?linkId:null;
  const invalidLink=linkId!==null&&!selected;
  const canReadPurchases=hasPermission('PURCHASE_READ');
  function openReceiving(id:string){
    setParams(prev=>{const next=new URLSearchParams(prev);next.set('purchaseId',id);return next;});
    setNotice('');
  }
  function closeReceiving(){
    setParams(prev=>{const next=new URLSearchParams(prev);next.delete('purchaseId');return next;});
  }
  const [notice, setNotice] = useState('');
  const list = useQuery({
    queryKey: purchaseKeys.receivingPage(page), queryFn: () => purchaseApi.receiving(page),
    enabled: canReceive && !selected,
  });
  if (!canReceive || list.error instanceof ApiClientError && list.error.status===403) return <ForbiddenPage />;
  return <div className="space-y-6">
    <PageHeader title={selected?'Приёмка по закупке':'Поступления'}
      description="Складская приёмка без раскрытия закупочных цен и себестоимости." />
    {!selected&&<PurchaseTabs />}
    {notice && <p role="status" className="rounded-xl bg-emerald-50 p-3 text-sm text-emerald-800">{notice}</p>}
    {invalidLink&&<p role="alert" className="rounded-xl bg-amber-50 p-3 text-sm text-amber-900">
      Неверная ссылка на закупку. Выберите поступление из списка.
    </p>}
    {selected&&canReadPurchases&&<Link className="text-sm font-semibold text-blue-700 hover:underline"
      to={'/purchases/'+encodeURIComponent(selected)}>Открыть карточку закупки</Link>}
    {selected&&<ReceivingForm key={selected} purchaseId={selected}
      onClose={closeReceiving}
      onPosted={() => { closeReceiving(); setNotice('Поступление проведено. Остатки обновлены сервером.'); }} />}
    {!selected&&(list.isPending ? <LoadingState label="Загружаем ожидаемые поступления…" /> :
      list.isError ? <ErrorState error={list.error} onRetry={() => void list.refetch()} /> :
      !list.data?.items.length ? <EmptyState title="Нет позиций для приёмки"
        description="Все ожидаемые позиции уже получены или ещё не подтверждены." /> :
      <section aria-label="Позиции к приёмке" className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-xs">
        <div className="hidden overflow-x-auto md:block">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50"><tr>{['Закупка','Дата','Модель','Вариация','Статус','Заказано','Принято','Осталось','Действие'].map(s =>
              <th scope="col" key={s} className="px-4 py-3 font-semibold">{s}</th>)}</tr></thead>
            <tbody className="divide-y divide-slate-100">{list.data.items.map(line =>
              <tr key={line.purchaseItemId}>
                <td className="px-4 py-4">{canReadPurchases?
                  <Link className="font-semibold text-blue-700 hover:underline"
                    to={'/purchases/'+encodeURIComponent(line.purchaseId)}>Открыть закупку</Link>:
                  <span>Закупка</span>}</td>
                <td className="px-4 py-4">{formatDate(line.createdAt)}</td>
                <td className="px-4 py-4 font-semibold">{line.model}</td><td className="px-4 py-4">{line.variant}</td>
                <td className="px-4 py-4"><PurchaseStatusBadge status={line.status} /></td>
                <td className="px-4 py-4">{line.orderedQuantity}</td><td className="px-4 py-4">{line.receivedQuantity}</td>
                <td className="px-4 py-4 font-bold">{line.remainingQuantity}</td>
                <td className="px-4 py-4"><Button type="button" variant="outline" disabled={!!selected}
                  onClick={() => openReceiving(line.purchaseId)}>Принять</Button></td>
              </tr>)}</tbody>
          </table>
        </div>
        <div className="divide-y divide-slate-100 md:hidden">{list.data.items.map(line =>
          <article key={line.purchaseItemId} className="space-y-2 p-4 text-sm">
            <p className="font-bold">{line.model} · {line.variant}</p>
            <p className="text-xs text-slate-600">
              {canReadPurchases?
                <Link className="font-semibold text-blue-700 hover:underline"
                  to={'/purchases/'+encodeURIComponent(line.purchaseId)}>Карточка закупки</Link>:
                'Закупка'} · {formatDate(line.createdAt)}
            </p>
            <PurchaseStatusBadge status={line.status} />
            <p>Заказано: {line.orderedQuantity} · Принято: {line.receivedQuantity} · Осталось: {line.remainingQuantity}</p>
            <Button type="button" variant="outline" disabled={!!selected}
              onClick={() => openReceiving(line.purchaseId)}>Принять</Button>
          </article>)}</div>
        <PurchasePager page={list.data.page} total={list.data.total} size={list.data.size}
          label="позиций" onPage={setPage} />
      </section>)}
  </div>;
}
