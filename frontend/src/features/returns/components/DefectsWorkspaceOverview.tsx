import { ClipboardCheck, Clock3, PackageX, Wrench } from 'lucide-react';
import type { Defect, DefectStatus } from '../types';

const statuses: { status: DefectStatus; label: string; icon: typeof Clock3 }[] = [
  { status: 'OPEN', label: 'Открыты', icon: Wrench },
  { status: 'WAITING_PARTS', label: 'Ожидают запчасти', icon: Clock3 },
  { status: 'RESOLVED', label: 'Исправлены', icon: ClipboardCheck },
  { status: 'WRITTEN_OFF', label: 'Списаны', icon: PackageX },
];

/** No global status totals: backend returns only a paginated, optionally status-filtered list. */
export function DefectsWorkspaceOverview({ rows, total, page, status, onStatus }: {
  rows: Defect[];
  total: number;
  page: number;
  status: DefectStatus | '';
  onStatus: (value: DefectStatus | '') => void;
}) {
  return <section aria-label="Обзор брака" className="space-y-3">
    <div className="flex flex-wrap items-center justify-between gap-2">
      <div>
        <h2 className="text-lg font-bold tracking-tight text-[#182d38]">Контроль брака</h2>
        <p className="mt-1 text-sm text-[#516977]">Статусы только текущей страницы. Нажмите карточку для фильтрации всего списка.</p>
      </div>
      <span className="rounded-lg bg-[#e6efed] px-3 py-2 text-xs font-semibold tabular-nums text-[#285f58]">
        Страница {page + 1} · {rows.length} из {total.toLocaleString('ru-RU')}
      </span>
    </div>
    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
      {statuses.map(({status: itemStatus,label,icon:Icon}) => {
        const count=rows.filter(row=>row.status===itemStatus).length;
        const selected=status===itemStatus;
        return <button key={itemStatus} type="button" aria-pressed={selected}
          aria-label={label + ': ' + count + '. Фильтровать по статусу'}
          onClick={()=>onStatus(selected?'':itemStatus)}
          className={'flex min-h-32 flex-col justify-between rounded-2xl border p-4 text-left transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#267f79] ' +
          (selected?'border-[#267f79] bg-[#e9f3ef]':'border-[#dfe7eb] bg-white hover:border-[#8ab9ad] hover:bg-[#f5faf8]')}>
          <div className="flex items-center justify-between gap-2">
            <p className="text-sm font-semibold text-[#405b65]">{label}</p>
            <Icon className="size-5 text-[#286e66]" aria-hidden="true"/>
          </div>
          <div>
            <strong className="text-3xl font-bold tabular-nums text-[#182d38]">{count.toLocaleString('ru-RU')}</strong>
            <p className="mt-1 text-xs text-[#516977]">Позиций на странице</p>
          </div>
        </button>;
      })}
    </div>
  </section>;
}
