import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ErrorState } from '../../../shared/components/ErrorState';
import { EmptyState } from '../../../shared/components/EmptyState';
import { LoadingState } from '../../../shared/components/LoadingState';
import { inventoryApi, inventoryKeys } from '../api';
import { displayTimestamp, shortId, warehouseLabel } from '../lib';
import type { StockLine, Warehouse } from '../types';
import { InventoryPager } from './InventoryPager';

export function TransferHistory({ warehouses, selectedWarehouseId, selectedLine }: {
  warehouses: Warehouse[]; selectedWarehouseId: string | null;
  selectedLine: StockLine | null;
}) {
  const [page, setPage] = useState(0);
  const [warehouseId, setWarehouseId] = useState(selectedWarehouseId ?? '');
  const [variantOnly, setVariantOnly] = useState(false);
  useEffect(() => { setWarehouseId(selectedWarehouseId ?? ''); setPage(0); }, [selectedWarehouseId]);
  useEffect(() => { setVariantOnly(false); setPage(0); }, [selectedLine?.productVariantId]);
  const variantId = variantOnly ? selectedLine?.productVariantId ?? null : null;
  const filters = { warehouseId: warehouseId || null, variantId, page, size: 20 };
  const history = useQuery({
    queryKey: inventoryKeys.transfers(filters),
    queryFn: () => inventoryApi.transfers(filters),
  });
  const data = history.data;
  useEffect(() => {
    if (data && page > 0 && page >= data.totalPages) setPage(Math.max(0, data.totalPages - 1));
  }, [data, page]);
  return <section aria-label="История перемещений" className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5">
    <h3 className="text-lg font-bold">История перемещений</h3>
    <div className="flex flex-wrap items-center gap-4">
      <label className="space-y-1 text-sm font-semibold">Фильтр склада
        <select aria-label="Фильтр склада в истории перемещений" value={warehouseId}
          className="block min-h-11 min-w-45 rounded-xl border border-slate-200 px-3"
          onChange={(event) => { setWarehouseId(event.target.value); setPage(0); }}>
          <option value="">Все склады</option>
          {warehouses.map((w) => <option key={w.id} value={w.id}>{w.name}</option>)}
        </select>
      </label>
      {selectedLine && <label className="flex min-h-11 items-center gap-2 text-sm">
        <input aria-label="Только выбранная вариация" type="checkbox" checked={variantOnly}
          onChange={(event) => { setVariantOnly(event.target.checked); setPage(0); }} />
        Только {selectedLine.model} / {selectedLine.variation}
      </label>}
    </div>
    <p className="text-xs text-slate-500">Себестоимость намеренно не отображается. Названия товаров без подтверждённых данных API не угадываются.</p>
    {history.isPending ? <LoadingState label="Загружаем перемещения…" />
      : history.isError ? <ErrorState title="История перемещений недоступна" error={history.error}
        onRetry={() => void history.refetch()} />
        : data?.totalElements === 0 ? <EmptyState title="Перемещений пока нет" description="Записи появятся после подтверждённых перемещений." />
        : data ? <div className="overflow-x-auto rounded-xl border border-slate-200">
          <table className="w-full min-w-185 text-left text-sm">
            <thead className="bg-slate-50"><tr>
              {['Дата', 'Вариация', 'Со склада', 'На склад', 'Количество', 'Сотрудник'].map((head) =>
                <th key={head} className="px-4 py-3 font-semibold text-slate-600">{head}</th>)}
            </tr></thead>
            <tbody className="divide-y divide-slate-100">
              {data.items.map((item) => <tr key={item.id}>
                <td className="whitespace-nowrap px-4 py-3">{displayTimestamp(item.createdAt)}</td>
                <td className="px-4 py-3" title={item.variantId}>
                  {selectedLine?.productVariantId === item.variantId
                    ? selectedLine.model + ' / ' + selectedLine.variation
                    : <span>UUID: {shortId(item.variantId)}</span>}
                </td>
                <td className="px-4 py-3">{warehouseLabel(item.sourceWarehouseId, warehouses)}</td>
                <td className="px-4 py-3">{warehouseLabel(item.destinationWarehouseId, warehouses)}</td>
                <td className="px-4 py-3">{item.quantity}</td>
                <td className="px-4 py-3">{item.actor ?? '—'}</td>
              </tr>)}
            </tbody>
          </table>
          <InventoryPager page={data.page} total={data.totalElements} size={data.size} onPage={setPage} />
        </div> : null}
  </section>;
}
