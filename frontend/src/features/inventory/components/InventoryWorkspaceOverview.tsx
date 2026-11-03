import { Boxes, CircleAlert, PackageCheck, PackageX } from 'lucide-react';
import type { StockLine } from '../types';

export function InventoryWorkspaceOverview({ lines, total, page }: {
  lines: StockLine[];
  total: number;
  page: number;
}) {
  const available = lines.reduce((sum, line) => sum + line.available, 0);
  const reserved = lines.reduce((sum, line) => sum + line.reserved, 0);
  const unavailable = lines.filter(line => line.available <= 0).length;
  const indicators = [
    { title: 'Доступно', value: available, note: 'Единиц для продажи', icon: PackageCheck },
    { title: 'В резерве', value: reserved, note: 'Единиц зарезервировано', icon: Boxes },
    { title: 'Без доступного остатка', value: unavailable, note: 'Вариаций без наличия', icon: PackageX },
  ];
  return <section aria-label="Обзор складских остатков" className="space-y-3">
    <div className="flex flex-wrap items-center justify-between gap-2">
      <div>
        <h2 className="text-lg font-bold tracking-tight text-[#182d38]">Обзор остатков</h2>
        <p className="mt-1 text-sm text-[#516977]">Показатели только по вариациям на текущей странице с учётом выбранного склада и фильтров.</p>
      </div>
      <span className="inline-flex items-center gap-2 rounded-lg bg-[#e6efed] px-3 py-2 text-xs font-semibold tabular-nums text-[#285f58]">
        <CircleAlert className="size-4" aria-hidden="true"/>
        Страница {page + 1} · {lines.length} из {total.toLocaleString('ru-RU')}
      </span>
    </div>
    <div className="grid gap-3 sm:grid-cols-3">
      {indicators.map(({ title, value, note, icon: Icon }) =>
        <div key={title} className="flex min-h-32 flex-col justify-between rounded-2xl border border-[#dfe7eb] bg-white p-5">
          <div className="flex items-center justify-between gap-2">
            <p className="text-sm font-semibold text-[#405b65]">{title}</p>
            <Icon className="size-5 text-[#286e66]" aria-hidden="true"/>
          </div>
          <div className="mt-4">
            <strong className="text-3xl font-bold tabular-nums text-[#182d38]">{value.toLocaleString('ru-RU')}</strong>
            <p className="mt-1 text-xs text-[#516977]">{note}</p>
          </div>
        </div>)}
    </div>
  </section>;
}
