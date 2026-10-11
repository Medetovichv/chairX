import { Archive, ArrowRight, Boxes, House, Warehouse as WarehouseIcon } from 'lucide-react';
import type { Warehouse, WarehouseStockSummary } from '../types';

function WarehouseCard({ warehouse, summary, current, disabled, onSelect }: {
  warehouse: Warehouse; summary: WarehouseStockSummary | undefined;
  current: boolean; disabled: boolean; onSelect: () => void;
}) {
  const Icon = warehouse.code === 'HOME' ? House : warehouse.code === 'OFFICE' ? WarehouseIcon : Boxes;
  return <button type="button" disabled={disabled} onClick={onSelect}
    aria-pressed={current} aria-label={'Показать склад '+warehouse.name}
    className={'w-full rounded-2xl border p-4 text-left shadow-sm transition focus-visible:outline-2 focus-visible:outline-blue-600 sm:p-5 '+
      (current ? 'border-blue-500 bg-blue-50 ring-1 ring-blue-400' :
        'border-slate-200 bg-white hover:border-blue-300 hover:shadow-md') +
      ' disabled:cursor-not-allowed disabled:opacity-60'}>
    <span className="flex items-center justify-between gap-3">
      <span className="flex size-11 items-center justify-center rounded-xl bg-blue-50 text-blue-600">
        <Icon className="size-6" aria-hidden="true" />
      </span>
      <ArrowRight className="size-4 text-slate-400" aria-hidden="true" />
    </span>
    <span className="mt-3 block truncate text-sm font-bold text-slate-900">{warehouse.name}</span>
    {!warehouse.active && <span className="mt-1 inline-flex items-center gap-1 text-xs text-amber-700">
      <Archive className="size-3" aria-hidden="true" />Неактивен</span>}
    <span className="mt-1 block text-2xl font-extrabold text-slate-950">
      {summary ? summary.available.toLocaleString('ru-RU') : '—'}
      <span className="ml-1 text-xs font-normal text-slate-500">доступно</span>
    </span>
    <span className="mt-3 block text-xs text-slate-500">
      Физически: {summary?.onHand ?? '—'} · Резерв: {summary?.reserved ?? '—'}
    </span>
    <span className="mt-1 block text-xs text-slate-500">
      Блокировано: {summary?.blocked ?? '—'} · Мало: {summary?.lowStockVariants ?? '—'} вариаций
    </span>
  </button>;
}

export function WarehouseOverviewCards({ warehouses, summaries, loading, failed, selected, disabled, onSelect }: {
  warehouses: Warehouse[]; summaries: WarehouseStockSummary[] | undefined;
  loading: boolean; failed: boolean; selected: string | null;
  disabled: boolean; onSelect: (id: string | null) => void;
}) {
  const byId = new Map((summaries ?? []).map(s => [s.warehouseId, s]));
  const combined = summaries?.reduce((sum, s) => ({
    available: sum.available + s.available,
    onHand: sum.onHand + s.onHand,
    reserved: sum.reserved + s.reserved,
    blocked: sum.blocked + s.blocked,
  }), {available:0,onHand:0,reserved:0,blocked:0});
  return <section aria-label="Сводка складов" className="space-y-3">
    <div className="flex flex-wrap items-center justify-between gap-3">
      <h3 className="text-lg font-bold text-slate-900">Склады</h3>
      <p className="text-xs text-slate-500">Суммы по всем товарам, без ограничения первой страницей</p>
    </div>
    {loading && <p role="status" className="text-sm text-slate-500">Загружаем сводку всех складов…</p>}
    {failed && <p role="alert" className="text-sm text-amber-800">Сводка временно недоступна. Таблица остатков продолжает работать.</p>}
    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
      <button type="button" disabled={disabled} onClick={() => onSelect(null)}
        aria-pressed={selected === null} aria-label="Показать все склады"
        className={'rounded-2xl border p-4 text-left shadow-sm transition focus-visible:outline-2 focus-visible:outline-blue-600 sm:p-5 ' +
          (selected === null ? 'border-blue-500 bg-blue-50 ring-1 ring-blue-400' :
            'border-slate-200 bg-white hover:border-blue-300') +
          ' disabled:cursor-not-allowed disabled:opacity-60'}>
        <span className="flex size-11 items-center justify-center rounded-xl bg-blue-100 text-blue-700">
          <Boxes className="size-6" aria-hidden="true" />
        </span>
        <span className="mt-3 block text-sm font-bold text-slate-900">Все склады</span>
        <span className="mt-1 block text-2xl font-extrabold text-slate-950">
          {combined ? combined.available.toLocaleString('ru-RU') : '—'}
          <span className="ml-1 text-xs font-normal text-slate-500">доступно</span>
        </span>
        <span className="mt-3 block text-xs text-slate-500">
          Физически: {combined?.onHand ?? '—'} · Резерв: {combined?.reserved ?? '—'}
        </span>
        <span className="mt-1 block text-xs text-slate-500">Блокировано: {combined?.blocked ?? '—'}</span>
      </button>
      {warehouses.map(w => <WarehouseCard key={w.id} warehouse={w}
        summary={byId.get(w.id)} current={selected === w.id} disabled={disabled}
        onSelect={() => onSelect(w.id)} />)}
    </div>
  </section>;
}
