import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ErrorState } from '../../../shared/components/ErrorState';
import { EmptyState } from '../../../shared/components/EmptyState';
import { LoadingState } from '../../../shared/components/LoadingState';
import { inventoryApi, inventoryKeys } from '../api';
import { displayTimestamp, movementNames, warehouseLabel } from '../lib';
import type { StockLine, Warehouse } from '../types';
import { InventoryPager } from './InventoryPager';

export function MovementHistory({ line, warehouses, preferredWarehouseId }: {
  line: StockLine; warehouses: Warehouse[]; preferredWarehouseId: string | null;
}) {
  const [warehouseId, setWarehouseId] = useState(preferredWarehouseId ?? '');
  const [page, setPage] = useState(0);
  useEffect(() => { setWarehouseId(preferredWarehouseId ?? ''); setPage(0); }, [preferredWarehouseId, line.productVariantId]);
  const balance = useQuery({
    queryKey: inventoryKeys.balance(warehouseId, line.productVariantId),
    queryFn: () => inventoryApi.balance(warehouseId, line.productVariantId),
    enabled: Boolean(warehouseId),
    retry: false,
  });
  const movements = useQuery({
    queryKey: inventoryKeys.movements(warehouseId, line.productVariantId, page),
    queryFn: () => inventoryApi.movements(warehouseId, line.productVariantId, page),
    enabled: Boolean(warehouseId),
  });
  const data = movements.data;
  useEffect(() => {
    if (data && page > 0 && data.totalElements <= page * 20) setPage(Math.max(0, Math.ceil(data.totalElements / 20) - 1));
  }, [data, page]);
  return <section aria-label="История движений" className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5">
    <h4 className="text-base font-bold">История движений: {line.model} / {line.variation}</h4>
    <label className="block space-y-2 text-sm font-semibold">Склад для истории
      <select aria-label="Склад для истории" value={warehouseId}
        className="min-h-11 w-full rounded-xl border border-slate-200 px-3 sm:max-w-xs"
        onChange={(event) => { setWarehouseId(event.target.value); setPage(0); }}>
        <option value="">Выберите склад</option>
        {warehouses.map((warehouse) =>
          <option key={warehouse.id} value={warehouse.id}>{warehouse.name}{warehouse.active ? '' : ' (неактивен)'}</option>)}
      </select>
    </label>
    {!warehouseId ? <p className="text-sm text-slate-500">Выберите склад для просмотра точного остатка и истории движений.</p>
      : <div className="space-y-4">
        {balance.isPending ? <LoadingState label="Загружаем точный остаток…" />
          : balance.isError ? <ErrorState title="Остаток склада недоступен" error={balance.error} onRetry={() => void balance.refetch()} />
          : balance.data ? <div aria-label="Остаток выбранного склада" className="grid grid-cols-2 gap-2 rounded-xl bg-slate-50 p-4 text-sm sm:grid-cols-4">
            <div>На складе <strong className="block text-lg">{balance.data.onHand}</strong></div>
            <div>Резерв <strong className="block text-lg">{balance.data.reserved}</strong></div>
            <div>Блокировано <strong className="block text-lg">{balance.data.blocked}</strong></div>
            <div>Доступно <strong className="block text-lg">{balance.data.available}</strong></div>
          </div> : null}
        <p className="text-xs text-slate-500">{warehouseLabel(warehouseId, warehouses)} · движения только для выбранной вариации.</p>
        {movements.isPending ? <LoadingState label="Загружаем движения…" />
          : movements.isError ? <ErrorState title="История движений недоступна" error={movements.error} onRetry={() => void movements.refetch()} />
          : data?.totalElements === 0 ? <EmptyState title="Движений пока нет" />
          : data ? <div className="overflow-x-auto rounded-xl border border-slate-200">
            <table className="w-full min-w-155 text-left text-sm">
              <thead className="bg-slate-50"><tr>
                {['Дата', 'Операция', 'Количество', 'Инициатор', 'Основание'].map((c) =>
                  <th key={c} className="px-4 py-3">{c}</th>)}
              </tr></thead>
              <tbody className="divide-y divide-slate-100">
                {data.items.map((entry) => <tr key={entry.id}>
                  <td className="whitespace-nowrap px-4 py-3">{displayTimestamp(entry.occurredAt)}</td>
                  <td className="px-4 py-3">{movementNames[entry.type] ?? entry.type}</td>
                  <td className="px-4 py-3">{entry.quantity}</td>
                  <td className="px-4 py-3">{entry.actor ?? '—'}</td>
                  <td className="px-4 py-3">{entry.sourceType}{entry.sourceId ? ' · ' + entry.sourceId : ''}</td>
                </tr>)}
              </tbody>
            </table>
            <InventoryPager page={data.page} total={data.totalElements} size={data.size} onPage={setPage} />
          </div> : null}
      </div>}
  </section>;
}
