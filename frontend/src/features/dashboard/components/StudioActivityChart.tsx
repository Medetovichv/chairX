import { useState } from 'react';
import { Activity } from 'lucide-react';
import type { DashboardOverview } from '../types';

function dayLabel(day: string) {
  return new Intl.DateTimeFormat('ru-RU', {
    day: 'numeric', month: 'short', timeZone: 'Asia/Bishkek',
  }).format(new Date(day + 'T12:00:00+06:00'));
}

export function StudioActivityChart({ days }: { days: DashboardOverview['ordersByDay'] }) {
  const [selected, setSelected] = useState<string | null>(null);
  const total = days.reduce((sum, item) => sum + item.orders, 0);
  const max = Math.max(1, ...days.map(item => item.orders));
  const active = days.find(day => day.day === selected);
  return <section aria-label="Заказы за 7 дней"
    className="min-w-0 rounded-[24px] border border-[#e0e8eb] bg-white p-5 sm:p-7">
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div>
        <p className="mb-2 flex items-center gap-2 text-xs font-bold text-[#27756d]">
          <Activity className="size-4" aria-hidden="true" /> Динамика
        </p>
        <h3 className="text-xl font-bold tracking-tight text-[#182d38]">Заказы за 7 дней</h3>
        <p className="mt-1 text-sm text-[#516977]">По дате создания · время Бишкек</p>
      </div>
      <span className="rounded-xl bg-[#edf5f3] px-3 py-2 text-xs font-semibold text-[#256e65]">7 дней</span>
    </div>
    <div className="mt-7 flex flex-wrap items-baseline gap-3">
      <strong className="text-4xl font-bold tracking-[-0.04em] tabular-nums text-[#182d38]">
        {(active?.orders ?? total).toLocaleString('ru-RU')}
      </strong>
      <span className="text-sm text-[#516977]">
        {active ? 'заказов · ' + dayLabel(active.day) : 'заказов за неделю'}
      </span>
    </div>
    {days.length === 0 ? <p className="mt-8 text-sm text-[#516977]">Данных за выбранный период пока нет.</p> :
      <div className="mt-7 grid h-57 min-w-0 grid-flow-col auto-cols-fr items-end gap-2 sm:gap-4"
        role="group" aria-label="Выберите день для детализации">
        {days.map(day => {
          const isActive = day.day === selected;
          const height = Math.max(day.orders ? 9 : 3, day.orders / max * 100);
          return <button key={day.day} type="button"
            aria-pressed={isActive} aria-label={dayLabel(day.day) + ': ' + day.orders + ' заказов'}
            onMouseEnter={() => setSelected(day.day)}
            onMouseLeave={() => setSelected(null)}
            onFocus={() => setSelected(day.day)}
            onBlur={() => setSelected(null)}
            onClick={() => setSelected(isActive ? null : day.day)}
            className="group flex h-full min-w-0 flex-col items-center justify-end gap-3 rounded-lg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#267f79]">
            <span aria-hidden="true"
              className="flex min-h-0 w-full flex-1 items-end rounded-lg bg-[repeating-linear-gradient(to_top,transparent_0,transparent_calc(25%-1px),#eef2f2_calc(25%-1px),#eef2f2_25%)]">
              <span className={'block w-full rounded-t-[9px] transition-[height,background-color] duration-200 motion-reduce:transition-none ' +
                (isActive ? 'bg-[#195a56]' : 'bg-[#aad6cb] group-hover:bg-[#399287]')}
                style={{ height: String(height) + '%' }} />
            </span>
            <span className={'text-[11px] font-semibold sm:text-xs ' +
              (isActive ? 'text-[#195a56]' : 'text-[#607783]')}>{dayLabel(day.day)}</span>
          </button>;
        })}
      </div>}
    <div className="mt-6 flex items-center justify-between border-t border-[#edf1f2] pt-4 text-xs text-[#516977]">
      <span className="flex items-center gap-2"><span aria-hidden="true" className="size-2 rounded-full bg-[#399287]" />Количество заказов</span>
      <span>Из CRM · реальные данные</span>
    </div>
  </section>;
}
