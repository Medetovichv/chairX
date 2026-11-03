import { ArrowUpRight, CheckCircle2, Clock3, ShoppingCart } from 'lucide-react';
import { Link } from 'react-router';

type PipelineItem = {
  status: 'DRAFT' | 'CONFIRMED' | 'FULFILLED';
  label: string;
  description: string;
  total?: number;
  failed?: boolean;
};

/** Views use the same three TanStack Query results as SalesPage; no extra API requests. */
export function SalesPipelineOverview({ drafts, confirmed, completed, draftsFailed, confirmedFailed, completedFailed }: {
  drafts?: number; confirmed?: number; completed?: number;
  draftsFailed?: boolean; confirmedFailed?: boolean; completedFailed?: boolean;
}) {
  const entries: PipelineItem[] = [
    { status:'DRAFT', label:'Черновики в работе',
      description:'Оформление ещё не завершено', total:drafts, failed:draftsFailed },
    { status:'CONFIRMED', label:'Ожидают выполнения',
      description:'Подтверждены, нужны действия', total:confirmed, failed:confirmedFailed },
    { status:'FULFILLED', label:'Выданы сегодня или ещё не закрыты',
      description:'Проверяйте оплату и получение', total:completed, failed:completedFailed },
  ];
  const pending=entries[1]!;
  return <section aria-label="Текущие показатели продаж"
    className="grid min-w-0 gap-3 lg:grid-cols-[minmax(0,1.15fr)_minmax(0,.85fr)]">
    <Link to={'/sales?status='+pending.status+'&operational=true'}
      className="group relative isolate flex min-h-50 flex-col justify-between overflow-hidden rounded-[24px] bg-[#172f3c] p-6 text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#267f79] sm:p-7">
      <div aria-hidden="true" className="pointer-events-none absolute -right-12 -top-22 size-64 rounded-full border-[38px] border-[#32636a]/35" />
      <div className="relative flex items-start justify-between">
        <span className="inline-flex size-11 items-center justify-center rounded-2xl bg-[#a7e2ce] text-[#173b3a]">
          <Clock3 className="size-5" aria-hidden="true" />
        </span>
        <ArrowUpRight className="size-5 text-[#b6ded4] transition-transform group-hover:-translate-y-1 group-hover:translate-x-1 motion-reduce:transition-none" aria-hidden="true" />
      </div>
      <div className="relative mt-5">
        <div className="flex flex-wrap items-end gap-3">
          <strong className="text-5xl font-bold leading-none tracking-[-0.045em] tabular-nums">
            {pending.failed ? '!' : pending.total === undefined ? '…' : pending.total.toLocaleString('ru-RU')}
          </strong>
          <span className="pb-1 text-sm font-medium text-[#d0e4e6]">{pending.label}</span>
        </div>
        <p className="mt-3 text-sm text-[#c4d9dc]" role={pending.failed ? 'alert' : undefined}>{pending.failed ? 'Не удалось загрузить показатель. Откройте список для проверки.' : pending.description}</p>
      </div>
    </Link>
    <div className="grid min-w-0 gap-3 sm:grid-cols-2 lg:grid-cols-1 xl:grid-cols-2">
      {entries.filter(item => item.status !== pending.status).map(item => {
        const Icon = item.status === 'DRAFT' ? ShoppingCart : CheckCircle2;
        return <Link key={item.status} to={'/sales?status='+item.status+'&operational=true'}
          className="group flex min-h-36 flex-col justify-between rounded-[22px] border border-[#e0e8eb] bg-white p-5 transition-[border-color,box-shadow] hover:border-[#a8c9c3] hover:shadow-[0_10px_24px_rgba(23,47,58,0.06)] focus-visible:outline-2 focus-visible:outline-[#267f79]">
          <div className="flex items-start justify-between">
            <span className="flex size-10 items-center justify-center rounded-xl bg-[#e7f4ee] text-[#27756d]"><Icon className="size-5" aria-hidden="true" /></span>
            <ArrowUpRight className="size-4 text-[#607783]" aria-hidden="true" />
          </div>
          <div className="mt-4">
            <strong className="text-3xl font-bold tracking-tight tabular-nums text-[#182d38]">{item.failed ? '!' : item.total === undefined ? '…' : item.total.toLocaleString('ru-RU')}</strong>
            <p className="mt-1 text-xs font-bold text-[#27434b]">{item.label}</p>
            <p className="mt-1 text-xs text-[#516977]" role={item.failed ? 'alert' : undefined}>{item.failed ? 'Не удалось загрузить показатель' : item.description}</p>
          </div>
        </Link>;
      })}
    </div>
  </section>;
}
