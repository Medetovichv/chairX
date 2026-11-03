import { Boxes, ClipboardList, PackageCheck, Truck } from 'lucide-react';
import type { PurchaseLine } from '../types';

export function PurchasesWorkspaceOverview({ lines, total, page }: {
  lines: PurchaseLine[];
  total: number;
  page: number;
}) {
  const pending = lines.filter(line => line.status === 'CONFIRMED' || line.status === 'PARTIALLY_RECEIVED').length;
  const ordered = lines.reduce((sum, line) => sum + line.orderedQuantity, 0);
  const received = lines.reduce((sum, line) => sum + line.receivedQuantity, 0);
  const remaining = lines.reduce((sum, line) => sum + line.remainingQuantity, 0);
  const metrics = [
    { label: 'Ожидают поступления', value: pending, helper: 'Закупок на странице', icon: Truck },
    { label: 'Заказано', value: ordered, helper: 'Единиц на странице', icon: Boxes },
    { label: 'Принято', value: received, helper: 'Единиц на странице', icon: PackageCheck },
    { label: 'Осталось принять', value: remaining, helper: 'Единиц на странице', icon: ClipboardList },
  ];
  return <section aria-label="Обзор закупок" className="space-y-3">
    <div className="flex flex-wrap items-center justify-between gap-2">
      <div>
        <h2 className="text-lg font-bold tracking-tight text-[#182d38]">Оперативный обзор закупок</h2>
        <p className="mt-1 text-sm text-[#516977]">Показатели только по записям на текущей странице с учётом фильтров.</p>
      </div>
      <p className="rounded-lg bg-[#e6efed] px-3 py-2 text-xs font-semibold tabular-nums text-[#285f58]">
        Страница {page + 1} · {lines.length} из {total.toLocaleString('ru-RU')}
      </p>
    </div>
    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
      {metrics.map(({ label, value, helper, icon: Icon }) =>
        <div key={label} className="flex min-h-32 flex-col justify-between rounded-2xl border border-[#dfe7eb] bg-white p-5">
          <div className="flex items-center justify-between">
            <p className="text-sm font-semibold text-[#405b65]">{label}</p>
            <Icon className="size-5 text-[#286e66]" aria-hidden="true"/>
          </div>
          <div className="mt-4">
            <strong className="text-3xl font-bold tabular-nums text-[#182d38]">{value.toLocaleString('ru-RU')}</strong>
            <p className="mt-1 text-xs text-[#516977]">{helper}</p>
          </div>
        </div>)}
    </div>
  </section>;
}
