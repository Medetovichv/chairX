import { ArrowUpRight, CheckCircle2, Clock3, PackageCheck, Truck } from 'lucide-react';
import type { DeliveryStatus, DeliverySummary } from '../types';

type Status = Extract<DeliveryStatus, 'READY' | 'IN_TRANSIT' | 'DELIVERED' | 'FAILED'>;

const states: { status: Status; title: string; detail: string; icon: typeof Truck }[] = [
  { status: 'READY', title: 'К отправке', detail: 'Готовые доставки', icon: PackageCheck },
  { status: 'IN_TRANSIT', title: 'В пути', detail: 'Переданы на доставку', icon: Truck },
  { status: 'DELIVERED', title: 'Доставлены', detail: 'Подтверждено получение', icon: CheckCircle2 },
  { status: 'FAILED', title: 'Нужны действия', detail: 'Не удалось доставить', icon: Clock3 },
];

/** Contextual summary of ONLY the current server-paginated page, never all orders. */
export function DeliveryWorkspaceOverview({ rows, total, page, onStatus }: {
  rows: DeliverySummary[];
  total: number;
  page: number;
  onStatus: (status: Status) => void;
}) {
  const counts = { READY: 0, IN_TRANSIT: 0, DELIVERED: 0, FAILED: 0 };
  for (const row of rows) {
    if (row.status in counts) counts[row.status as Status]++;
  }
  return <section aria-label="Обзор доставок" className="space-y-3">
    <div className="flex flex-wrap items-center justify-between gap-2">
      <div>
        <h2 className="text-lg font-bold tracking-tight text-[#182d38]">Оперативный обзор</h2>
        <p className="mt-1 text-sm text-[#516977]">Статусы показаны только для записей на текущей странице.</p>
      </div>
      <p className="rounded-lg bg-[#e6efed] px-3 py-2 text-xs font-semibold tabular-nums text-[#285f58]">
        Страница {page + 1} · {rows.length} из {total.toLocaleString('ru-RU')}
      </p>
    </div>
    <div className="grid gap-3 @min-[520px]/workspace:grid-cols-2 @min-[1000px]/workspace:grid-cols-4">
      {states.map(({ status, title, detail, icon: Icon }) =>
        <button key={status} type="button" onClick={() => onStatus(status)}
          aria-label={title + ': ' + counts[status] + '. Показать все с этим статусом'}
          className="group flex min-h-36 flex-col justify-between rounded-2xl border border-[#dfe7eb] bg-white p-4 text-left transition-[border-color,background-color] hover:border-[#8ab9ad] hover:bg-[#f5faf8] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#267f79]">
          <div className="flex items-center justify-between">
            <span className="flex size-10 items-center justify-center rounded-xl bg-[#e9f3ef] text-[#286e66]">
              <Icon className="size-5" aria-hidden="true"/>
            </span>
            <ArrowUpRight className="size-4 text-[#516977]" aria-hidden="true"/>
          </div>
          <div className="mt-4 flex items-end justify-between gap-3">
            <div>
              <p className="font-semibold text-[#263e49]">{title}</p>
              <p className="mt-1 text-xs text-[#516977]">{detail}</p>
            </div>
            <strong className="text-3xl font-bold tabular-nums text-[#182d38]">{counts[status]}</strong>
          </div>
        </button>)}
    </div>
  </section>;
}
