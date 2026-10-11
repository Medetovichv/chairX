import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useLocation, useParams } from 'react-router';
import { useAuth } from '../../auth/AuthProvider';
import { ApiClientError, userFacingApiError } from '../../../shared/api/api-error';
import { PageHeader } from '../../../shared/components/PageHeader';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState';
import { StatusBadge } from '../../../shared/components/StatusBadge';
import { ForbiddenPage } from '../../../pages/ForbiddenPage';
import { NotFoundPage } from '../../../pages/NotFoundPage';
import { Button } from '../../../shared/ui/button';
import { Input } from '../../../shared/ui/input';
import { ConfirmDialog } from '../../../shared/ui/confirm-dialog';
import { purchaseApi, purchaseKeys, supplierApi } from '../api';
import { PurchaseTabs } from '../components/PurchaseTabs';
import { PurchaseForm, moneyFromCents, purchasePreview } from '../components/PurchaseForm';
import { PurchaseStatusBadge } from '../components/PurchaseStatusBadge';
import { PurchaseReceiptsHistory } from '../components/PurchaseReceiptsHistory';
import { checkMoney, formatDate, formatMoney } from '../validation';
import type { Purchase } from '../types';

const uuid = (id: string) => /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id);
type StatusAction = 'confirm' | 'cancel';
function Detail({ purchase }: { purchase: Purchase }) {
  const { hasPermission } = useAuth();
  const canUpdate = hasPermission('PURCHASE_UPDATE') && purchase.status === 'DRAFT';
  const canAdjustCargo = hasPermission('PURCHASE_UPDATE') && !purchase.costsLockedAt &&
    ['DRAFT','CONFIRMED'].includes(purchase.status);
  const canConfirm = hasPermission('PURCHASE_CONFIRM') && purchase.status === 'DRAFT';
  const canCancel = hasPermission('PURCHASE_CANCEL') && ['DRAFT','CONFIRMED'].includes(purchase.status);
  const client = useQueryClient();
  const location = useLocation();
  const startNotice = (location.state as { notice?: unknown } | null)?.notice;
  const [notice, setNotice] = useState(typeof startNotice === 'string' ? startNotice : '');
  const [error, setError] = useState('');
  const [editing, setEditing] = useState(false);
  const [editingCargo, setEditingCargo] = useState(false);
  const [cargoValue, setCargoValue] = useState(purchase.cargoCost === null ? '' : String(purchase.cargoCost));
  const [confirming, setConfirming] = useState<StatusAction | null>(null);
  const supplier = useQuery({ queryKey: ['suppliers','detail',purchase.supplierId],
    queryFn: () => supplierApi.get(purchase.supplierId), enabled: hasPermission('SUPPLIERS_READ') });
  const supplierLabel = supplier.data?.name ?? 'Поставщик недоступен';
  async function refreshed(saved: Purchase) {
    client.setQueryData(purchaseKeys.detail(purchase.id), saved);
    await Promise.all([
      client.invalidateQueries({queryKey:purchaseKeys.overviews}),
      client.invalidateQueries({queryKey:purchaseKeys.receiving}),
      client.invalidateQueries({queryKey:purchaseKeys.receivingSummary(purchase.id)}),
      client.invalidateQueries({queryKey:purchaseKeys.payments(purchase.id)}),
    ]);
  }
  const update = useMutation({
    mutationFn: (value: Parameters<typeof purchaseApi.update>[1]) => purchaseApi.update(purchase.id,value),
    onSuccess: async saved => { await refreshed(saved);setEditing(false);setError('');setNotice('Черновик обновлён.'); },
  });
  const status = useMutation({
    mutationFn: (action: StatusAction) => purchaseApi.status(purchase.id,action),
    onSuccess: async (saved,action) => { await refreshed(saved);setConfirming(null);setError('');
      setNotice(action==='confirm'?'Закупка подтверждена.':'Закупка отменена.'); },
    onError: cause => {setConfirming(null);setError(userFacingApiError(cause));setNotice('');},
  });
  const cargo = useMutation({
    mutationFn: (value:string) => purchaseApi.cargo(purchase.id,value),
    onSuccess: async saved => {await refreshed(saved);setEditingCargo(false);
      setNotice('Стоимость карго обновлена.');setError('');},
    onError: cause => {setError(userFacingApiError(cause));setNotice('');},
  });
  function saveCargo() {
    const invalid = checkMoney(cargoValue);
    if (invalid) {setError(invalid);return;}
    cargo.mutate(cargoValue.trim().replace(',','.'));
  }
  return <div className="space-y-6">
    <Link className="inline-flex min-h-11 items-center font-semibold text-indigo-700" to="/purchases">
      ← Все закупки</Link>
    <PageHeader title="Карточка закупки"
      description={'Поставщик: ' + supplierLabel + ' · Создана ' + formatDate(purchase.createdAt)}
      action={<div className="flex flex-wrap gap-2">
        {canUpdate && <Button variant="outline" disabled={editing || status.isPending}
          onClick={() => setEditing(true)}>Редактировать</Button>}
        {canConfirm && <Button disabled={status.isPending || editing}
          onClick={() => setConfirming('confirm')}>Подтвердить</Button>}
        {canCancel && <Button variant="outline" disabled={status.isPending || editing}
          onClick={() => setConfirming('cancel')}>Отменить закупку</Button>}
      </div>} />
    <PurchaseTabs />
    {notice && <p role="status" className="rounded-xl bg-emerald-50 p-3 text-sm text-emerald-800">{notice}</p>}
    {error && <p role="alert" className="rounded-xl bg-red-50 p-3 text-sm text-red-700">{error}</p>}
    {editing && canUpdate && <PurchaseForm key={purchase.updatedAt} initial={purchase}
      onSave={async input => {await update.mutateAsync(input);}} onCancel={() => setEditing(false)} />}
    <section aria-label="Сведения о закупке"
      className="grid gap-4 rounded-2xl border border-slate-200 bg-white p-5 text-sm sm:grid-cols-2">
      <div><p className="text-slate-500">Поставщик</p><p className="font-semibold break-all">{supplierLabel}</p></div>
      <div><p className="text-slate-500">Статус</p><PurchaseStatusBadge status={purchase.status} /></div>
      <div><p className="text-slate-500">Создана</p><p className="font-semibold">{formatDate(purchase.createdAt)}</p></div>
      <div><p className="text-slate-500">Подтверждена</p><p className="font-semibold">{formatDate(purchase.confirmedAt)}</p></div>
      <div><p className="text-slate-500">Себестоимость зафиксирована</p>
        <p className="font-semibold">{formatDate(purchase.costsLockedAt)}</p></div>
      <div><p className="text-slate-500">Карго</p><p className="font-semibold">{formatMoney(purchase.cargoCost)}</p></div>
      <div className="sm:col-span-2"><p className="text-slate-500">Комментарий</p>
        <p className="whitespace-pre-wrap break-words">{purchase.comment || '—'}</p></div>
    </section>
    {canAdjustCargo && <section aria-label="Изменить карго"
      className="space-y-3 rounded-2xl border border-slate-200 bg-white p-4">
      <div className="flex flex-wrap justify-between gap-2">
        <h3 className="font-bold">Корректировка карго</h3>
        {!editingCargo && <Button variant="outline" onClick={() => {setCargoValue(purchase.cargoCost === null ? '' : String(purchase.cargoCost));setEditingCargo(true);}}>
          Изменить карго</Button>}
      </div>
      {editingCargo && <div className="flex flex-wrap items-end gap-2">
        <label className="space-y-1 text-sm font-semibold">Новая стоимость карго, сом
          <Input aria-label="Новая стоимость карго" inputMode="decimal"
            disabled={cargo.isPending} value={cargoValue} onChange={e=>setCargoValue(e.target.value)} />
        </label>
        <Button disabled={cargo.isPending} onClick={saveCargo}>Сохранить карго</Button>
        <Button variant="outline" disabled={cargo.isPending}
          onClick={() => setEditingCargo(false)}>Закрыть</Button>
      </div>}
      <p className="text-xs text-slate-500">После первой приёмки изменение карго запрещается.
        Стоимость не может быть ниже уже выплаченных средств за карго.</p>
    </section>}
    {purchase.costsLockedAt && <StatusBadge tone="warning">Себестоимость заблокирована поступлением</StatusBadge>}
    <section aria-label="Позиции закупки" className="space-y-3">
      <h3 className="text-xl font-bold">Позиции закупки</h3>
      <div className="divide-y divide-slate-100 rounded-2xl border border-slate-200 bg-white">
        {purchase.items.map(line => {
          const gross = purchasePreview([{
            quantity: String(line.orderedQuantity), unitCost: String(line.purchaseUnitCost),
          }], '0').goods;
          return <article key={line.id} aria-label={'Закупочная позиция '+line.lineNumber}
            className="space-y-3 p-4 text-sm">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div className="min-w-0">
                <h4 className="font-bold text-slate-900">
                  {line.modelName ?? 'Кресло — название недоступно'}
                  {line.variantName ? ' · ' + line.variantName : ''}
                </h4>
                {line.sku && <p className="mt-1 text-xs text-slate-500">Артикул: {line.sku}</p>}
                {line.productId && hasPermission('CATALOG_READ') && <Link
                  to={'/catalog/'+encodeURIComponent(line.productId)}
                  className="mt-2 inline-flex text-sm font-semibold text-blue-700 hover:underline">
                  Открыть в каталоге</Link>}
              </div>
              <div className="text-right">
                <span className="text-xs text-slate-500">Закупочная позиция</span>
                <p className="font-semibold">№{line.lineNumber}</p>
              </div>
            </div>
            <div className="grid gap-2 rounded-xl bg-slate-50 p-3 text-sm sm:grid-cols-2">
              <p>Заказано: <strong>{line.orderedQuantity} шт.</strong></p>
              <p>Принято: <strong>{line.receivedQuantity} шт.</strong></p>
              <p>Осталось: <strong>{line.orderedQuantity - line.receivedQuantity} шт.</strong></p>
              <p>Цена за единицу: <strong>{formatMoney(line.purchaseUnitCost)}</strong></p>
              <p>Стоимость позиции: <strong>{gross === null ? 'Не рассчитана' : moneyFromCents(gross)}</strong></p>
              <p>Распределённое карго: {formatMoney(line.allocatedCargoCost)}</p>
              <p className="sm:col-span-2">Итоговая себестоимость единицы: {formatMoney(line.finalUnitCost)}</p>
            </div>
          </article>;
        })}
      </div>
    </section>
    <PurchaseReceiptsHistory purchase={purchase}/>
    <div className="flex flex-wrap gap-2">
      {hasPermission('INVENTORY_RECEIVE') &&
        ['CONFIRMED','PARTIALLY_RECEIVED'].includes(purchase.status) && <Button asChild>
          <Link to={'/purchases/receipts?purchaseId='+encodeURIComponent(purchase.id)}>
            Перейти к поступлениям</Link></Button>}
      {hasPermission('PURCHASE_PAYMENTS_READ') && <Button asChild variant="outline">
        <Link to={'/purchases/payments?purchaseId='+encodeURIComponent(purchase.id)}>Просмотр оплат</Link></Button>}
    </div>
    <ConfirmDialog open={!!confirming} busy={status.isPending} destructive={confirming==='cancel'}
      title={confirming==='confirm'?'Подтвердить закупку?':'Отменить закупку?'}
      description={confirming==='confirm' ?
        ('Поставщик: '+supplierLabel+'. Позиции: '+purchase.items.map(x=>x.orderedQuantity+
          ' шт. по '+formatMoney(x.purchaseUnitCost)).join('; ')+
          '. Карго: '+formatMoney(purchase.cargoCost)+'. Подтверждение изменит статус закупки.') :
        'Отмена допускается только без поступлений и проведённых выплат. Закупка останется в истории.'}
      onOpenChange={open=>{if(!open)setConfirming(null);}}
      confirmLabel={confirming==='confirm'?'Подтвердить':'Отменить закупку'}
      onConfirm={()=>{if(confirming&&!status.isPending)status.mutate(confirming);}} />
  </div>;
}
export function PurchaseDetailPage() {
  const { hasPermission } = useAuth();
  const { purchaseId = '' } = useParams();
  const allowed = hasPermission('PURCHASE_READ');
  const valid = uuid(purchaseId);
  const detail = useQuery({
    queryKey: purchaseKeys.detail(purchaseId), queryFn: () => purchaseApi.get(purchaseId),
    enabled: allowed && valid,
  });
  if (!allowed) return <ForbiddenPage />;
  if (!valid) return <NotFoundPage />;
  if (detail.isPending) return <LoadingState label="Загружаем закупку…" />;
  if (detail.isError) {
    if (detail.error instanceof ApiClientError && detail.error.status===404) return <NotFoundPage />;
    if (detail.error instanceof ApiClientError && detail.error.status===403) return <ForbiddenPage />;
    return <ErrorState error={detail.error} onRetry={() => void detail.refetch()} />;
  }
  return detail.data ? <Detail key={purchaseId} purchase={detail.data} /> : <NotFoundPage />;
}
