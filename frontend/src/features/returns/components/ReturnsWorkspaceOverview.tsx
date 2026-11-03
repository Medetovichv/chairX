import { ClipboardList, PackageOpen, SearchCheck } from 'lucide-react';
import type { ReturnSummary } from '../types';

/** Only page-level facts from the existing paginated response, never inferred refund totals. */
export function ReturnsWorkspaceOverview({ rows, total, page, filtered }: {
  rows: ReturnSummary[];
  total: number;
  page: number;
  filtered: boolean;
}) {
  const linked = rows.filter(row => Boolean(row.saleNumber?.trim())).length;
  const figures = [
    { label: 'Показано возвратов', value: rows.length, note: 'Записей на этой странице', icon: PackageOpen },
    { label: 'С номером продажи', value: linked, note: 'Есть номер исходного заказа', icon: ClipboardList },
  ];
  return <section aria-label="Обзор возвратов" className="space-y-3">
    <div className="flex flex-wrap items-center justify-between gap-2">
      <div>
        <h2 className="text-lg font-bold tracking-tight text-[#182d38]">Обзор возвратов</h2>
        <p className="mt-1 text-sm text-[#516977]">Показатели только для текущей страницы. Суммы возмещений здесь не рассчитываются.</p>
      </div>
      <span className="inline-flex items-center gap-2 rounded-lg bg-[#e6efed] px-3 py-2 text-xs font-semibold tabular-nums text-[#285f58]">
        <SearchCheck className="size-4" aria-hidden="true"/>
        {filtered ? 'Поиск · ' : ''}Страница {page + 1} · {rows.length} из {total.toLocaleString('ru-RU')}
      </span>
    </div>
    <div className="grid gap-3 sm:grid-cols-2">
      {figures.map(({label,value,note,icon:Icon})=>
        <div key={label} className="flex min-h-32 flex-col justify-between rounded-2xl border border-[#dfe7eb] bg-white p-5">
          <div className="flex items-center justify-between">
            <p className="text-sm font-semibold text-[#405b65]">{label}</p>
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
