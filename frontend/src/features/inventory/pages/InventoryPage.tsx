import { useEffect, useState } from 'react';
import { ArrowLeftRight, Search, PackageCheck } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { useAuth } from '../../../features/auth/AuthProvider';
import { PageHeader } from '../../../shared/components/PageHeader';
import { EmptyState } from '../../../shared/components/EmptyState';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState';
import { Button } from '../../../shared/ui/button';
import { Input } from '../../../shared/ui/input';
import { inventoryApi, inventoryKeys } from '../api';
import type { StockLine, Warehouse } from '../types';
import { pages, warehouseStock } from '../lib';
import { InventoryPager } from '../components/InventoryPager';
import { MovementHistory } from '../components/MovementHistory';
import { TransferHistory } from '../components/TransferHistory';
import { TransferForm } from '../components/TransferForm';
import { WarehouseOverviewCards } from '../components/WarehouseOverviewCards';

type AvailabilityFilter = 'all' | 'available' | 'recorded';
const panelClass = 'overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-xs';

function StockSummary({ line, warehouses, selectedWarehouseId }: {
  line: StockLine; warehouses: Warehouse[]; selectedWarehouseId: string | null;
}) {
  const entries = selectedWarehouseId
    ? warehouses.filter((w) => w.id === selectedWarehouseId)
    : warehouses;
  return <div className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-3 xl:grid-cols-4">
    {entries.map((w) => {
      const stock = warehouseStock(line, w.id);
      return <div key={w.id} className="rounded-xl border border-blue-100 bg-blue-50/50 p-3">
        <p className="font-semibold text-slate-700">{w.name}</p>
        <p className="mt-2 text-xs text-slate-500">Физический {stock?.onHand ?? 0} · Резерв {stock?.reserved ?? 0}</p>
        <p className="text-xs text-slate-500">Блокировано {stock?.blocked ?? 0}</p>
        <p className="mt-1 text-base font-bold text-blue-700">Доступно: {stock?.available ?? 0}</p>
      </div>;
    })}
    <div className="rounded-xl border border-slate-200 p-3">
      <p className="font-semibold">{selectedWarehouseId ? 'Выбранный склад' : 'Все склады'}</p>
      <p className="mt-2 text-xs">Физический: {line.onHand} · Резерв: {line.reserved}</p>
      <p className="text-xs">Блокировано: {line.blocked}</p>
      <p className="mt-1 text-base font-bold">Доступно: {line.available}</p>
    </div>
  </div>;
}

function StockTable({ lines, warehouses, selectedWarehouseId, canTransfer, transferOpen, onSelect, onTransfer }: {
  lines: StockLine[]; warehouses: Warehouse[]; selectedWarehouseId: string | null;
  canTransfer: boolean; transferOpen: boolean;
  onSelect: (line: StockLine) => void; onTransfer: (line: StockLine) => void;
}) {
  const tableWarehouses = selectedWarehouseId ? [] : warehouses;
  return <div className={panelClass}>
    <div className="hidden overflow-x-auto md:block">
      <table className="w-full min-w-220 text-left text-sm">
        <thead className="bg-slate-50"><tr>
          <th className="px-4 py-3">Модель</th><th className="px-4 py-3">Вариация</th>
          {tableWarehouses.map((w) => <th className="px-4 py-3" key={w.id}>{w.name}</th>)}
          <th className="px-4 py-3">{selectedWarehouseId ? 'На складе' : 'Всего'}</th>
          <th className="px-4 py-3">Резерв</th><th className="px-4 py-3">Блокировано</th>
          <th className="px-4 py-3">Доступно</th><th className="px-4 py-3">Действия</th>
        </tr></thead>
        <tbody className="divide-y divide-slate-100">
          {lines.map((line) => <tr key={line.productVariantId} className="entity-clickable-row transition-colors hover:bg-blue-50">
            <td className="px-4 py-4 font-semibold"><button type="button" disabled={transferOpen} className="entity-stretched-link font-semibold text-blue-700 hover:underline disabled:opacity-60" onClick={() => onSelect(line)}>{line.model}</button></td>
            <td className="px-4 py-4">{line.variation}</td>
            {tableWarehouses.map((w) =>
              <td className="px-4 py-4" key={w.id}>{warehouseStock(line, w.id)?.onHand ?? 0}</td>)}
            <td className="px-4 py-4 font-semibold">{line.onHand}</td>
            <td className="px-4 py-4">{line.reserved}</td>
            <td className="px-4 py-4">{line.blocked}</td>
            <td className="px-4 py-4 font-bold text-indigo-700">{line.available}</td>
            <td className="px-4 py-4">
              <div className="flex flex-wrap gap-2">
                {canTransfer && <Button className="entity-row-action" size="sm" variant="outline" disabled={transferOpen} onClick={() => onTransfer(line)}><ArrowLeftRight className="size-4" aria-hidden="true" />Переместить</Button>}
              </div>
            </td>
          </tr>)}
        </tbody>
      </table>
    </div>
    <div className="divide-y divide-slate-100 md:hidden">
      {lines.map((line) => <article key={line.productVariantId} className="entity-clickable-row space-y-3 p-4 transition-colors hover:bg-blue-50">
        <div><h4 className="font-bold text-slate-900"><button type="button" disabled={transferOpen} className="entity-stretched-link text-blue-700 hover:underline" onClick={() => onSelect(line)}>{line.model}</button></h4>
          <p className="text-sm text-slate-600">{line.variation}</p></div>
        <div className="grid grid-cols-2 gap-2 text-sm">
          <span>На складе: <strong>{line.onHand}</strong></span>
          <span>Резерв: <strong>{line.reserved}</strong></span>
          <span>Блокировано: <strong>{line.blocked}</strong></span>
          <span>Доступно: <strong className="text-indigo-700">{line.available}</strong></span>
        </div>
        {!selectedWarehouseId && <div className="flex flex-wrap gap-x-3 gap-y-1 text-xs text-slate-500">
          {warehouses.map((w) => <span key={w.id}>{w.name}: {warehouseStock(line, w.id)?.onHand ?? 0}</span>)}
        </div>}
        <div className="flex gap-2">
          {canTransfer && <Button className="entity-row-action" disabled={transferOpen} onClick={() => onTransfer(line)}><ArrowLeftRight className="size-4" aria-hidden="true" />Переместить</Button>}
        </div>
      </article>)}
    </div>
  </div>;
}

export function InventoryPage() {
  const { hasPermission } = useAuth();
  const canTransfer = hasPermission('INVENTORY_READ') && hasPermission('INVENTORY_TRANSFER');
  const warehouses = useQuery({ queryKey: inventoryKeys.warehouses, queryFn: inventoryApi.warehouses });
  const summaries = useQuery({ queryKey: inventoryKeys.warehouseSummary,
    queryFn: inventoryApi.warehouseSummary, enabled: hasPermission('INVENTORY_READ'), staleTime: 30_000 });
  const [selectedWarehouseId, setSelectedWarehouseId] = useState<string | null>(null);
  const [modelInput, setModelInput] = useState('');
  const [variationInput, setVariationInput] = useState('');
  const [modelQuery, setModelQuery] = useState('');
  const [variationQuery, setVariationQuery] = useState('');
  const [availability, setAvailability] = useState<AvailabilityFilter>('all');
  const [page, setPage] = useState(0);
  const [selectedLine, setSelectedLine] = useState<StockLine | null>(null);
  const [transferLine, setTransferLine] = useState<StockLine | null>(null);
  const [notice, setNotice] = useState('');
  useEffect(() => {
    const timer = setTimeout(() => { setModelQuery(modelInput.trim()); setVariationQuery(variationInput.trim()); }, 350);
    return () => clearTimeout(timer);
  }, [modelInput, variationInput]);

  const filters = { warehouseId: selectedWarehouseId, model: modelQuery, variation: variationQuery,
    includeZero: availability !== 'recorded', onlyAvailable: availability === 'available', page, size: 20 };
  const overview = useQuery({
    queryKey: inventoryKeys.overview(filters),
    queryFn: () => inventoryApi.overview(filters),
    enabled: warehouses.isSuccess && (warehouses.data?.length ?? 0) > 0,
  });
  const dataset = overview.data;
  useEffect(() => {
    if (dataset && page > 0 && page >= pages(dataset.total, dataset.size)) {
      setPage(Math.max(0, pages(dataset.total, dataset.size) - 1));
    }
  }, [dataset, page]);

  function newFilter() {
    setPage(0); setSelectedLine(null);
  }
  function showDetail(line: StockLine) {
    if (transferLine) return;
    setSelectedLine(line);
    setTransferLine(null);
  }
  function startTransfer(line: StockLine) {
    if (transferLine) return;
    setTransferLine(line); setSelectedLine(line); setNotice('');
  }
  const namedWarehouses = warehouses.data ?? [];
  return <div className="space-y-6">
    <PageHeader title="Склады" description="Реальные складские остатки, резервы и перемещения офисных кресел." />
    {notice && <p role="status" className="rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">{notice}</p>}
    {warehouses.isPending ? <LoadingState label="Загружаем список складов…" />
      : warehouses.isError ? <ErrorState title="Не удалось получить склады" error={warehouses.error}
        onRetry={() => void warehouses.refetch()} />
        : namedWarehouses.length === 0 ? <EmptyState title="Склады не найдены" description="Сначала создайте склады через администратора системы." />
          : <>
            <WarehouseOverviewCards warehouses={namedWarehouses} summaries={summaries.data}
              loading={summaries.isPending} failed={summaries.isError}
              selected={selectedWarehouseId} disabled={Boolean(transferLine)}
              onSelect={(id) => { setSelectedWarehouseId(id); newFilter(); }} />
            <section aria-label="Фильтры складской сводки" className="space-y-4 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                <label className="space-y-2 text-sm font-semibold">Склад
                  <select aria-label="Выбор склада" className="min-h-11 w-full rounded-xl border border-slate-200 px-3"
                    value={selectedWarehouseId ?? ''} disabled={Boolean(transferLine)} onChange={(event) => {
                      setSelectedWarehouseId(event.target.value || null); newFilter();
                    }}>
                    <option value="">Все склады</option>
                    {namedWarehouses.map((w) => <option key={w.id} value={w.id}>
                      {w.name}{w.active ? '' : ' (неактивен)'}
                    </option>)}
                  </select>
                </label>
                <label className="space-y-2 text-sm font-semibold"><span className="inline-flex items-center gap-1"><Search className="size-4 text-blue-600" aria-hidden="true" />Поиск модели</span>
                  <Input aria-label="Поиск модели" type="search" value={modelInput} maxLength={200} disabled={Boolean(transferLine)}
                    onChange={(event) => { setModelInput(event.target.value); newFilter(); }}
                    placeholder="Например, Ergo" />
                </label>
                <label className="space-y-2 text-sm font-semibold">Поиск вариации
                  <Input aria-label="Поиск вариации" type="search" value={variationInput} maxLength={200} disabled={Boolean(transferLine)}
                    onChange={(event) => { setVariationInput(event.target.value); newFilter(); }}
                    placeholder="Например, Чёрный" />
                </label>
              </div>
              <div role="group" aria-label="Фильтр наличия" className="flex flex-wrap gap-2">
                {([
                  ['all', 'Все товары'], ['available', 'Только доступные'], ['recorded', 'С остатком или резервом'],
                ] as const).map(([value, label]) => <Button key={value} variant={availability === value ? 'default' : 'outline'}
                  aria-pressed={availability === value} disabled={Boolean(transferLine)} onClick={() => { setAvailability(value); newFilter(); }}>{label}</Button>)}
              </div>
            </section>
            <div className="flex flex-wrap items-center justify-between gap-2"><h3 className="flex items-center gap-2 text-lg font-bold text-slate-900"><PackageCheck className="size-5 text-blue-600" aria-hidden="true" />Остатки товаров</h3><p className="text-xs text-slate-500">Нажмите на строку для подробностей; фильтр «Все товары» включает нулевые остатки.</p></div>
            {overview.isPending ? <LoadingState label="Загружаем складские остатки…" />
              : overview.isError ? <ErrorState title="Не удалось загрузить остатки" error={overview.error}
                onRetry={() => void overview.refetch()} />
                : dataset && dataset.total === 0 ? <EmptyState title="Товары не найдены"
                  description="Измените склад, условия поиска или фильтры наличия." />
                  : dataset ? <div className="space-y-1">
                    <StockTable lines={dataset.items} warehouses={namedWarehouses}
                      selectedWarehouseId={selectedWarehouseId} canTransfer={canTransfer}
                      transferOpen={Boolean(transferLine)} onSelect={showDetail} onTransfer={startTransfer} />
                    <div className={panelClass}><InventoryPager page={dataset.page} total={dataset.total}
                      size={dataset.size} onPage={(p) => { if (transferLine) return; setPage(p); setSelectedLine(null); }} /></div>
                  </div> : null}
            {selectedLine && <>
              <section aria-label="Карточка складской позиции" className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div><h3 className="text-lg font-bold">{selectedLine.model} / {selectedLine.variation}</h3>
                    <p className="text-xs text-slate-500">Физические остатки, резерв, блокировка и история движения</p></div>
                  <div className="flex gap-2">
                    {hasPermission('CATALOG_READ') && <Button variant="outline" asChild>
                      <Link to="/catalog">Каталог товаров</Link></Button>}
                    <Button variant="outline" disabled={Boolean(transferLine)} onClick={() => { setSelectedLine(null); setTransferLine(null); }}>Скрыть</Button>
                  </div>
                </div>
                <StockSummary line={selectedLine} warehouses={namedWarehouses} selectedWarehouseId={selectedWarehouseId} />
              </section>
              {transferLine && canTransfer && <TransferForm key={transferLine.productVariantId + ':' + (selectedWarehouseId ?? '')}
                line={transferLine} warehouses={namedWarehouses} preferredWarehouseId={selectedWarehouseId}
                onClose={() => setTransferLine(null)}
                onSuccess={(message) => { setTransferLine(null); setSelectedLine(null); setNotice(message);
                  void summaries.refetch(); }} />}
              <MovementHistory key={selectedLine.productVariantId + ':' + (selectedWarehouseId ?? '')}
                line={selectedLine} warehouses={namedWarehouses} preferredWarehouseId={selectedWarehouseId} />
            </>}
            <TransferHistory warehouses={namedWarehouses} selectedWarehouseId={selectedWarehouseId} selectedLine={selectedLine} />
          </>}
  </div>;
}
