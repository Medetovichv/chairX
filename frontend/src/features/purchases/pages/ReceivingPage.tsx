import { useState } from 'react';
import { ClipboardCheck, PackageCheck, Truck, CalendarDays } from 'lucide-react';
import { ReceivingWorkspaceOverview } from '../components/ReceivingWorkspaceOverview';
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
  return <div className="space-y-5 text-[#182d38] sm:space-y-6">
    <PageHeader title={selected?'Приёмка по закупке':'Поступления'}
      description="Складская приёмка без раскрытия закупочных цен и себестоимости." />
    {!selected&&<PurchaseTabs />}
    {!selected && list.isSuccess && <ReceivingWorkspaceOverview lines={list.data.items} page={list.data.page} total={list.data.total}/>}
    {notice && <p role="status" className="rounded-xl bg-emerald-50 p-3 text-sm text-emerald-800">{notice}</p>}
    {invalidLink&&<p role="alert" className="rounded-xl bg-amber-50 p-3 text-sm text-amber-900">
      Неверная ссылка на закупку. Выберите поступление из списка.
    </p>}
    {selected&&canReadPurchases&&<Link className="text-sm font-semibold text-[#416c82] hover:underline"
      to={'/purchases/'+encodeURIComponent(selected)}>Открыть карточку закупки</Link>}
    {selected&&<ReceivingForm key={selected} purchaseId={selected}
      onClose={closeReceiving}
      onPosted={() => { closeReceiving(); setNotice('Поступление проведено. Остатки обновлены сервером.'); }} />}
    {!selected&&(list.isPending ? <LoadingState label="Загружаем ожидаемые поступления…" /> :
      list.isError ? <ErrorState error={list.error} onRetry={() => void list.refetch()} /> :
      !list.data?.items.length ? <EmptyState title="Нет позиций для приёмки"
        description="Все ожидаемые позиции уже получены или ещё не подтверждены." /> :
      <section aria-label="Позиции к приёмке" className="overflow-hidden rounded-2xl border border-[#dfe7eb] bg-white">
        <div className="hidden overflow-x-auto md:block">
          <table className="w-full text-left text-sm">
            <thead className="bg-[#f5f9f7]"><tr>{['Закупка','Дата','Модель','Вариация','Статус','Заказано','Принято','Осталось','Действие'].map(s =>
              <th scope="col" key={s} className="px-4 py-4 text-xs font-bold text-[#516977]">{s}</th>)}</tr></thead>
            <tbody className="divide-y divide-[#eaf0f0]">{list.data.items.map(line =>
              <tr key={line.purchaseItemId}>
                <td className="px-4 py-4">{canReadPurchases?
                  <Link className="font-semibold text-[#236e66] hover:underline"
                    to={'/purchases/'+encodeURIComponent(line.purchaseId)}>Открыть закупку</Link>:
                  <span>Закупка</span>}</td>
                <td className="px-4 py-4">{formatDate(line.createdAt)}</td>
                <td className="max-w-52 break-words px-4 py-4 font-semibold text-[#263e49]">{line.model}</td><td className="px-4 py-4">{line.variant}</td>
                <td className="px-4 py-4"><PurchaseStatusBadge status={line.status} /></td>
                <td className="px-4 py-4">{line.orderedQuantity}</td><td className="px-4 py-4">{line.receivedQuantity}</td>
                <td className="px-4 py-4 font-bold tabular-nums text-[#182d38]">{line.remainingQuantity}</td>
                <td className="px-4 py-4"><Button type="button" variant="outline" disabled={!!selected}
                  onClick={() => openReceiving(line.purchaseId)}>Принять</Button></td>
              </tr>)}</tbody>
          </table>
        </div>
        <div className="divide-y divide-[#eaf0f0] md:hidden">{list.data.items.map(line =>
          <article key={line.purchaseItemId} className="space-y-4 p-4 text-sm sm:p-5">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div className="min-w-0">
                <h3 className="break-words font-bold text-[#182d38]">{line.model}</h3>
                <p className="mt-1 break-words text-[#516977]">{line.variant}</p>
              </div>
              <PurchaseStatusBadge status={line.status}/>
            </div>
            <div className="flex flex-wrap items-center gap-3 text-xs text-[#516977]">
              <span className="inline-flex items-center gap-1">
                <CalendarDays className="size-4" aria-hidden="true"/>{formatDate(line.createdAt)}
              </span>
              {canReadPurchases && <Link className="font-semibold text-[#236e66] underline-offset-2 hover:underline"
                to={'/purchases/'+encodeURIComponent(line.purchaseId)}>Открыть закупку</Link>}
            </div>
            <div className="grid grid-cols-2 gap-3 rounded-xl bg-[#f2f7f6] p-4">
              <div className="col-span-2">
                <p className="flex items-center gap-2 text-xs text-[#516977]">
                  <Truck className="size-4" aria-hidden="true"/>Осталось принять
                </p>
                <strong className="mt-1 block text-2xl font-bold tabular-nums text-[#182d38]">
                  {line.remainingQuantity} шт.
                </strong>
              </div>
              <div>
                <p className="flex items-center gap-1 text-xs text-[#516977]">
                  <ClipboardCheck className="size-4" aria-hidden="true"/>Заказано
                </p>
                <strong className="mt-1 block font-semibold tabular-nums text-[#263e49]">{line.orderedQuantity} шт.</strong>
              </div>
              <div>
                <p className="flex items-center gap-1 text-xs text-[#516977]">
                  <PackageCheck className="size-4" aria-hidden="true"/>Принято
                </p>
                <strong className="mt-1 block font-semibold tabular-nums text-[#263e49]">{line.receivedQuantity} шт.</strong>
              </div>
            </div>
            <Button className="min-h-11 w-full rounded-xl" type="button" variant="outline" disabled={!!selected}
              onClick={() => openReceiving(line.purchaseId)}>Принять</Button>
          </article>)}</div>
        <PurchasePager page={list.data.page} total={list.data.total} size={list.data.size}
          label="позиций" onPage={setPage} />
      </section>)}
  </div>;
}
