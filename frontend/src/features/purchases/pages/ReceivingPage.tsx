import { useState } from 'react';
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

export function ReceivingPage() {
  const { hasPermission } = useAuth();
  const canReceive = hasPermission('INVENTORY_RECEIVE');
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<string | null>(null);
  const [notice, setNotice] = useState('');
  const list = useQuery({
    queryKey: purchaseKeys.receivingPage(page), queryFn: () => purchaseApi.receiving(page),
    enabled: canReceive,
  });
  if (!canReceive || list.error instanceof ApiClientError && list.error.status===403) return <ForbiddenPage />;
  return <div className="space-y-6">
    <PageHeader title="Поступления" description="Рабочие позиции закупок для кладовщика. Стоимость здесь не показывается." />
    <PurchaseTabs />
    {notice && <p role="status" className="rounded-xl bg-emerald-50 p-3 text-sm text-emerald-800">{notice}</p>}
    {selected && <ReceivingForm key={selected} purchaseId={selected}
      onClose={() => setSelected(null)}
      onPosted={() => { setSelected(null); setNotice('Поступление проведено. Остатки обновлены сервером.'); }} />}
    {list.isPending ? <LoadingState label="Загружаем ожидаемые поступления…" /> :
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
                <td className="px-4 py-4 break-all">{line.purchaseId.slice(0,8)}…</td>
                <td className="px-4 py-4">{formatDate(line.createdAt)}</td>
                <td className="px-4 py-4 font-semibold">{line.model}</td><td className="px-4 py-4">{line.variant}</td>
                <td className="px-4 py-4"><PurchaseStatusBadge status={line.status} /></td>
                <td className="px-4 py-4">{line.orderedQuantity}</td><td className="px-4 py-4">{line.receivedQuantity}</td>
                <td className="px-4 py-4 font-bold">{line.remainingQuantity}</td>
                <td className="px-4 py-4"><Button type="button" variant="outline" disabled={!!selected}
                  onClick={() => { setSelected(line.purchaseId); setNotice(''); }}>Принять</Button></td>
              </tr>)}</tbody>
          </table>
        </div>
        <div className="divide-y divide-slate-100 md:hidden">{list.data.items.map(line =>
          <article key={line.purchaseItemId} className="space-y-2 p-4 text-sm">
            <p className="font-bold">{line.model} · {line.variant}</p>
            <p className="break-all text-xs">Закупка: {line.purchaseId}</p>
            <PurchaseStatusBadge status={line.status} />
            <p>Заказано: {line.orderedQuantity} · Принято: {line.receivedQuantity} · Осталось: {line.remainingQuantity}</p>
            <Button type="button" variant="outline" disabled={!!selected}
              onClick={() => {setSelected(line.purchaseId);setNotice('');}}>Принять</Button>
          </article>)}</div>
        {selected ? <p className="p-4 text-sm text-amber-800">
          Для перехода к другой странице сначала завершите или закройте форму приёмки.
        </p> : <PurchasePager page={list.data.page} total={list.data.total} size={list.data.size}
          label="позиций" onPage={setPage} />}
      </section>}
  </div>;
}
