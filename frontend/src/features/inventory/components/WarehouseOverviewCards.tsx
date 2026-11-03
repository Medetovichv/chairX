import { Archive, ArrowUpRight, Boxes, House, Warehouse as WarehouseIcon } from 'lucide-react';
import type { Warehouse, WarehouseStockSummary } from '../types';

const cardBase = 'group min-w-0 rounded-xl border p-4 text-left transition-colors duration-150 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#267f79] disabled:cursor-not-allowed disabled:opacity-60';
const cardState = (active: boolean) => active
  ? 'border-[#267f79] bg-[#eaf4f1]'
  : 'border-[#dfe7eb] bg-white hover:border-[#92bdb4] hover:bg-[#f5f9f8]';
const iconBase = 'flex size-10 shrink-0 items-center justify-center rounded-lg bg-[#e6efed] text-[#286e66]';
const number = 'mt-3 block text-2xl font-bold tabular-nums tracking-tight text-[#182d38]';

function StockFacts({ summary }: { summary: Pick<WarehouseStockSummary, 'onHand'|'reserved'|'blocked'> | undefined }) {
  return <span className="mt-3 block border-t border-[#dfe7eb] pt-3 text-xs leading-5 text-[#516977]">
    Физически: {summary?.onHand?.toLocaleString('ru-RU') ?? '—'} · Резерв: {summary?.reserved?.toLocaleString('ru-RU') ?? '—'}
    <span className="block">Блокировано: {summary?.blocked?.toLocaleString('ru-RU') ?? '—'}</span>
  </span>;
}

function WarehouseCard({ warehouse, summary, current, disabled, onSelect }: {
  warehouse: Warehouse; summary: WarehouseStockSummary | undefined;
  current: boolean; disabled: boolean; onSelect: () => void;
}) {
  const Icon = warehouse.code === 'HOME' ? House : warehouse.code === 'OFFICE' ? WarehouseIcon : Boxes;
  return <button type="button" disabled={disabled} onClick={onSelect}
    aria-pressed={current} aria-label={'Показать склад '+warehouse.name}
    className={cardBase+' '+cardState(current)}>
    <span className="flex min-w-0 items-center gap-3">
      <span className={iconBase}><Icon className="size-5" aria-hidden="true"/></span>
      <span className="min-w-0 flex-1">
        <span className="block break-words text-sm font-bold text-[#182d38]">{warehouse.name}</span>
        <span className="mt-0.5 block text-xs text-[#516977]">
          {warehouse.active ? 'Склад' : 'Неактивен'}
        </span>
      </span>
      {warehouse.active ? <ArrowUpRight className="size-4 shrink-0 text-[#517a78]" aria-hidden="true"/> :
        <Archive className="size-4 shrink-0 text-[#9a682f]" aria-hidden="true"/>}
    </span>
    <span className={number}>
      {summary ? summary.available.toLocaleString('ru-RU') : '—'}
      <span className="ml-2 text-xs font-medium tracking-normal text-[#516977]">доступно</span>
    </span>
    <StockFacts summary={summary}/>
    <span className="mt-1 block text-xs text-[#516977]">Мало товара: {summary?.lowStockVariants?.toLocaleString('ru-RU') ?? '—'} вариаций</span>
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
    <div className="flex flex-wrap items-baseline justify-between gap-2">
      <h3 className="text-lg font-bold tracking-tight text-[#182d38]">Склады</h3>
      <p className="text-xs text-[#516977]">Сводка по всем товарам, не только текущей странице</p>
    </div>
    {loading && <p role="status" className="text-sm text-[#516977]">Загружаем сводку всех складов…</p>}
    {failed && <p role="alert" className="text-sm text-amber-800">Сводка временно недоступна. Таблица остатков продолжает работать.</p>}
    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
      <button type="button" disabled={disabled} onClick={() => onSelect(null)}
        aria-pressed={selected === null} aria-label="Показать все склады"
        className={cardBase+' '+cardState(selected === null)}>
        <span className="flex items-center gap-3">
          <span className={iconBase}><Boxes className="size-5" aria-hidden="true"/></span>
          <span className="min-w-0 flex-1 text-sm font-bold text-[#182d38]">Все склады</span>
          <ArrowUpRight className="size-4 shrink-0 text-[#517a78]" aria-hidden="true"/>
        </span>
        <span className={number}>{combined ? combined.available.toLocaleString('ru-RU') : '—'}
          <span className="ml-2 text-xs font-medium tracking-normal text-[#516977]">доступно</span>
        </span>
        <StockFacts summary={combined}/>
      </button>
      {warehouses.map(w => <WarehouseCard key={w.id} warehouse={w}
        summary={byId.get(w.id)} current={selected === w.id} disabled={disabled}
        onSelect={() => onSelect(w.id)} />)}
    </div>
  </section>;
}
