import { Boxes, ClipboardCheck, PackageCheck } from 'lucide-react';
import type { ReceivingLine } from '../types';

/** Aggregates only visible receiving lines; warehouse cost data is never requested. */
export function ReceivingWorkspaceOverview({ lines, total, page }: {
  lines: ReceivingLine[];
  total: number;
  page: number;
}) {
  const remaining = lines.reduce((sum, line) => sum + line.remainingQuantity, 0);
  const received = lines.reduce((sum, line) => sum + line.receivedQuantity, 0);
  const count = lines.filter(line => line.remainingQuantity > 0).length;
  const figures = [
    { label: 'К приёмке', value: count, detail: 'Позиций из списка', icon: Boxes },
    { label: 'Осталось принять', value: remaining, detail: 'Штук из списка', icon: ClipboardCheck },
    { label: 'Уже принято', value: received, detail: 'Штук по показанным позициям', icon: PackageCheck },
  ];
  return <section aria-label="Обзор поступлений" className="space-y-3">
    <div className="flex flex-wrap items-center justify-between gap-2">
      <div>
        <h2 className="text-lg font-bold tracking-tight text-[#182d38]">Ожидаемые поступления</h2>
        <p className="mt-1 text-sm text-[#516977]">Показатели только по позициям на текущей странице. Себестоимость не отображается.</p>
      </div>
      <span className="rounded-lg bg-[#e6efed] px-3 py-2 text-xs font-semibold tabular-nums text-[#285f58]">
        Страница {page + 1} · {lines.length} из {total.toLocaleString('ru-RU')}
      </span>
    </div>
    <div className="grid gap-3 sm:grid-cols-3">
      {figures.map(({ label, value, detail, icon: Icon }) =>
        <div key={label} className="flex min-h-32 flex-col justify-between rounded-2xl border border-[#dfe7eb] bg-white p-5">
          <div className="flex items-center justify-between">
            <span className="text-sm font-semibold text-[#405b65]">{label}</span>
            <Icon className="size-5 text-[#286e66]" aria-hidden="true"/>
          </div>
          <div className="mt-4">
            <strong className="text-3xl font-bold tabular-nums text-[#182d38]">{value.toLocaleString('ru-RU')}</strong>
            <p className="mt-1 text-xs text-[#516977]">{detail}</p>
          </div>
        </div>)}
    </div>
  </section>;
}
