import { useRef, useState, type FormEvent } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { inventoryApi, inventoryKeys } from '../../inventory/api';
import { ApiClientError, userFacingApiError } from '../../../shared/api/api-error';
import { Button } from '../../../shared/ui/button';
import { Input } from '../../../shared/ui/input';
import { EmptyState } from '../../../shared/components/EmptyState';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState';
import { purchaseApi, purchaseKeys } from '../api';
import { integerQuantity } from '../validation';
import type { ReceiptInput } from '../types';

export function ReceivingForm({ purchaseId, onClose, onPosted }: {
  purchaseId: string; onClose: () => void; onPosted: () => void;
}) {
  const { hasPermission } = useAuth();
  const client = useQueryClient();
  const allowed = hasPermission('INVENTORY_RECEIVE');
  const canSeeWarehouses = hasPermission('INVENTORY_READ');
  const summary = useQuery({
    queryKey: purchaseKeys.receivingSummary(purchaseId),
    queryFn: () => purchaseApi.receivingSummary(purchaseId), enabled: allowed,
  });
  const warehouses = useQuery({
    queryKey: inventoryKeys.warehouses, queryFn: inventoryApi.warehouses,
    enabled: allowed && canSeeWarehouses,
  });
  const [warehouseId, setWarehouseId] = useState('');
  const [quantities, setQuantities] = useState<Record<string, string>>({});
  const [comment, setComment] = useState('');
  const [error, setError] = useState('');
  const [uncertain, setUncertain] = useState<ReceiptInput | null>(null);
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  async function post(payload: ReceiptInput) {
    if (pending.current) return;
    pending.current = true; setBusy(true); setError('');
    try {
      await purchaseApi.postReceipt(purchaseId, payload);
      setUncertain(null);
      await Promise.all([
        client.invalidateQueries({ queryKey: purchaseKeys.receiving }),
        client.invalidateQueries({ queryKey: purchaseKeys.receivingSummary(purchaseId) }),
        client.invalidateQueries({ queryKey: purchaseKeys.overviews }),
        client.invalidateQueries({ queryKey: purchaseKeys.detail(purchaseId) }),
        client.invalidateQueries({ queryKey: inventoryKeys.overviewPrefix }),
        client.invalidateQueries({ queryKey: inventoryKeys.balancePrefix }),
        client.invalidateQueries({ queryKey: inventoryKeys.movementsPrefix }),
      ]);
      onPosted();
    } catch (cause) {
      setError(userFacingApiError(cause));
      // A failed network request is ambiguous: the server might have posted it.
      // Hold the *identical* payload/key and never allow a new receipt meanwhile.
      if (!(cause instanceof ApiClientError) || cause.kind === 'network' || cause.kind === 'abort') {
        setUncertain(payload);
      } else {
        setUncertain(null);
      }
    } finally { pending.current = false; setBusy(false); }
  }
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending.current || uncertain || !summary.data || !canSeeWarehouses) return;
    const warehouse = warehouses.data?.find(w => w.id === warehouseId && w.active);
    if (!warehouse) { setError('Выберите активный склад.'); return; }
    if (!['CONFIRMED', 'PARTIALLY_RECEIVED'].includes(summary.data.status)) {
      setError('Эта закупка больше не доступна для приёмки.'); return;
    }
    const items: ReceiptInput['items'] = [];
    for (const line of summary.data.items) {
      const raw = (quantities[line.purchaseItemId] ?? '0').trim();
      if (raw === '' || raw === '0') continue;
      const quantity = integerQuantity(raw);
      if (quantity === null || quantity > line.remainingQuantity || quantity < 1) {
        setError('Количество по позиции ' + line.model + ' / ' + line.variant + ' превышает остаток или некорректно.'); return;
      }
      items.push({ purchaseItemId: line.purchaseItemId, quantity });
    }
    if (items.length < 1 || items.length > 1000) {
      setError('Выберите от 1 до 1000 позиций с положительным количеством.'); return;
    }
    if (comment.length > 4000) { setError('Комментарий: максимум 4000 символов.'); return; }
    const payload: ReceiptInput = { idempotencyKey: crypto.randomUUID(), warehouseId, items,
      comment: comment.trim() || null };
    void post(payload);
  }
  if (!allowed) return <p role="alert">Нет разрешения INVENTORY_RECEIVE.</p>;
  return <section aria-label="Форма поступления"
    className="space-y-4 rounded-2xl border border-indigo-200 bg-white p-4 shadow-sm sm:p-6">
    <h3 className="text-xl font-bold">Приёмка по закупке</h3>
    <p className="break-all text-xs text-slate-500">Закупка: {purchaseId}</p>
    <p className="text-sm text-slate-600">
      Данные себестоимости не включаются в ответ приёмки для складской роли.
    </p>
    {!canSeeWarehouses && <p role="alert" className="text-sm text-amber-800">
      Для выбора склада нужно INVENTORY_READ. Права не выдаются автоматически.
    </p>}
    {summary.isPending ? <LoadingState label="Загружаем позиции приёмки…" /> :
      summary.isError ? <ErrorState error={summary.error} onRetry={() => void summary.refetch()} /> :
      warehouses.isPending && canSeeWarehouses ? <LoadingState label="Загружаем склады…" /> :
      warehouses.isError ? <ErrorState error={warehouses.error} onRetry={() => void warehouses.refetch()} /> :
      !summary.data ? <EmptyState title="Поступление не найдено" /> :
      <form onSubmit={submit} className="space-y-4">
        <label className="block space-y-2 text-sm font-semibold">Склад назначения
          <select aria-label="Склад назначения" disabled={busy || !!uncertain || !canSeeWarehouses}
            value={warehouseId} onChange={e => setWarehouseId(e.target.value)}
            className="min-h-11 w-full rounded-xl border border-slate-200 bg-white px-3">
            <option value="">Выберите активный склад</option>
            {warehouses.data?.filter(w => w.active).map(w => <option key={w.id} value={w.id}>{w.name}</option>)}
          </select>
        </label>
        {summary.data.items.map(line => <div key={line.purchaseItemId}
          className="grid gap-2 rounded-xl bg-slate-50 p-3 sm:grid-cols-[1fr_180px] sm:items-center">
          <div>
            <p className="font-semibold">{line.model} · {line.variant}</p>
            <p className="text-xs text-slate-600">Заказано {line.orderedQuantity} · Принято {line.receivedQuantity} · Осталось {line.remainingQuantity}</p>
          </div>
          <label className="text-sm font-semibold">Получено сейчас
            <Input aria-label={'Получено: ' + line.model + ' / ' + line.variant} inputMode="numeric"
              disabled={busy || !!uncertain || line.remainingQuantity===0}
              value={quantities[line.purchaseItemId] ?? '0'}
              onChange={e => setQuantities(old => ({...old, [line.purchaseItemId]: e.target.value}))} />
          </label>
        </div>)}
        <label className="block space-y-2 text-sm font-semibold">Комментарий
          <textarea aria-label="Комментарий поступления" rows={2} maxLength={4000}
            value={comment} disabled={busy || !!uncertain}
            onChange={e => setComment(e.target.value)}
            className="w-full rounded-xl border border-slate-200 p-3 text-sm" />
        </label>
        {error && <p role="alert" className="rounded-xl bg-red-50 p-3 text-sm text-red-700">{error}</p>}
        {uncertain && <div role="alert" className="rounded-xl bg-amber-50 p-4 text-sm text-amber-900">
          Результат поступления неизвестен. Товар мог быть уже принят.
          Не создавайте новую операцию. Повторите точно тот же запрос с тем же ключом.
          <p className="mt-2 break-all text-xs">Ключ операции: {uncertain.idempotencyKey}</p>
          <Button type="button" className="mt-3" disabled={busy}
            onClick={() => void post(uncertain)}>Повторить ту же операцию</Button>
        </div>}
        <div className="flex flex-wrap gap-2">
          <Button type="submit" disabled={busy || !!uncertain || !canSeeWarehouses ||
            !['CONFIRMED','PARTIALLY_RECEIVED'].includes(summary.data.status)}>
            {busy ? 'Проводим…' : 'Провести поступление'}
          </Button>
          <Button type="button" variant="outline" disabled={busy || !!uncertain} onClick={onClose}>Закрыть</Button>
        </div>
      </form>}
  </section>;
}
