import { useState, type DragEvent } from 'react';
import { useNavigate, Link } from 'react-router';
import { useInfiniteQuery } from '@tanstack/react-query';
import { CircleCheck, CircleDashed, GripVertical, Package, XCircle } from 'lucide-react';
import { useAuth } from '../../auth/AuthProvider';
import { salesApi, salesKeys } from '../api';
import { saleStatuses, saleStatusNames, saleDate, money, fulfillmentNames } from '../validation';
import { allowedKanbanTransitions, transitionForTarget } from '../kanbanTransitions';
import { Button } from '../../../shared/ui/button';
import { displaySaleNumber } from '../../../shared/lib/format';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState';
import type { SaleFilters, SaleStatus, SaleSummary } from '../types';

const appearances = {
  DRAFT: { icon: CircleDashed, color: 'slate', dot: 'bg-slate-400',
    title: 'text-slate-800', surface: 'border-slate-200 bg-slate-100/80' },
  CONFIRMED: { icon: CircleCheck, color: 'blue', dot: 'bg-blue-500',
    title: 'text-blue-700', surface: 'border-blue-100 bg-blue-50/70' },
  FULFILLED: { icon: CircleCheck, color: 'emerald', dot: 'bg-emerald-500',
    title: 'text-emerald-700', surface: 'border-emerald-100 bg-emerald-50/70' },
  CANCELLED: { icon: XCircle, color: 'rose', dot: 'bg-rose-500',
    title: 'text-rose-700', surface: 'border-rose-100 bg-rose-50/70' },
} as const;

function PaymentLabel({ value }: { value: string | null }) {
  const settled = value === 'PAID';
  return <span className={'inline-flex max-w-full items-center rounded-lg px-2 py-1 text-xs font-semibold ' +
    (settled ? 'bg-emerald-50 text-emerald-700' :
      value === 'PARTIAL' ? 'bg-amber-50 text-amber-800' : 'bg-slate-100 text-slate-600')}>
    {settled ? 'Оплачен' : value === 'PARTIAL' ? 'Частично оплачен' : value === 'UNPAID' ? 'Не оплачен' : 'Оплата не указана'}
  </span>;
}

/** Each column is server-paginated; never render just the first 20 as "all". */
function KanbanColumn({ status, filters, dragged, onStart, onEnd, onDropMove, onRequestMove }: {
  status: SaleStatus;
  filters: SaleFilters;
  dragged: SaleSummary | null;
  onStart: (sale: SaleSummary) => void;
  onEnd: () => void;
  onDropMove: (target: SaleStatus) => void;
  onRequestMove: (sale: SaleSummary, target: SaleStatus) => void;
}) {
  const { hasPermission } = useAuth();
  const q = useInfiniteQuery({
    queryKey: [...salesKeys.kanbanColumn(status), filters.from, filters.to, filters.number],
    initialPageParam: 0,
    queryFn: ({ pageParam }) => salesApi.list({ ...filters, status, page: pageParam }),
    getNextPageParam: last => last.size > 0 && (last.page + 1) * last.size < last.total
      ? last.page + 1 : undefined,
  });
  const rows = q.data?.pages.flatMap(p => p.items) ?? [];
  const total = q.data?.pages[0]?.total ?? 0;
  const style = appearances[status];
  const Icon = style.icon;
  const canDrop = dragged !== null && transitionForTarget(dragged, status, hasPermission) !== null;
  const handleDrop = (event: DragEvent<HTMLElement>) => {
    if (!dragged) return;
    event.preventDefault();
    onDropMove(status);
  };

  return <section aria-label={'Канбан: ' + saleStatusNames[status]}
    className={'flex min-w-0 flex-col rounded-2xl border p-3 shadow-sm xl:min-w-[235px] ' +
      style.surface + (canDrop ? ' ring-2 ring-blue-300' : '')}
    onDragOver={event => { if (canDrop) { event.preventDefault(); event.dataTransfer.dropEffect = 'move'; } }}
    onDrop={handleDrop}>
    <header className="mb-3 flex items-center gap-2 rounded-xl px-1 py-1">
      <Icon aria-hidden="true" className={'size-5 shrink-0 '+style.title} />
      <h3 className={'min-w-0 flex-1 truncate text-sm font-extrabold '+style.title}>{saleStatusNames[status]}</h3>
      <span aria-label={'Всего заказов: '+total} className="shrink-0 rounded-full bg-white px-2.5 py-1 text-xs font-bold text-slate-700">
        {total}
      </span>
    </header>
    {q.isPending ? <LoadingState label="Загружаем заказы…" /> :
      q.isError ? <ErrorState error={q.error} onRetry={() => void q.refetch()} /> :
      rows.length === 0 ? <div className="rounded-xl border border-dashed border-slate-200 bg-white/70 p-7 text-center text-sm text-slate-500">
        {canDrop ? 'Переместить сюда' : 'Нет заказов'}
      </div> :
      <div className="flex flex-col gap-3">{rows.map(s => {
        const possible = allowedKanbanTransitions(s, hasPermission);
        return <article key={s.id}
          className="entity-clickable-row group space-y-3 rounded-xl border border-slate-200 bg-white p-3.5 text-sm shadow-[0_2px_8px_rgba(15,23,42,0.04)] transition hover:border-blue-200 hover:shadow-md"
          draggable={possible.length > 0}
          onDragStart={event => {
            if (possible.length === 0) { event.preventDefault(); return; }
            event.dataTransfer.effectAllowed = 'move';
            event.dataTransfer.setData('text/plain', s.saleNumber);
            onStart(s);
          }}
          onDragEnd={onEnd}>
          <div className="flex items-center justify-between gap-2">
            <Link draggable={false} className="entity-stretched-link font-bold text-blue-700 hover:underline"
              to={'/sales/' + encodeURIComponent(s.id)} state={{ summary: s }}
              aria-label={'Открыть продажу '+s.saleNumber} title={s.saleNumber}>
              {displaySaleNumber(s.saleNumber)}
            </Link>
            <span className="text-xs text-slate-500">{saleDate(s.createdAt)}</span>
          </div>
          <p className="line-clamp-2 font-medium text-slate-800">{s.customerName || 'Без клиента'}</p>
          <div className="flex items-start gap-3">
            <span aria-hidden="true" className="flex size-13 shrink-0 items-center justify-center rounded-xl bg-slate-100 text-slate-500">
              <Package className="size-7" />
            </span>
            <div className="min-w-0 flex-1">
              <p className="line-clamp-2 text-xs text-slate-600" title={s.products || undefined}>
                {s.products || 'Товары не указаны'}
              </p>
              <div className="mt-1 flex flex-wrap justify-between gap-2">
                <strong className="text-slate-800">{s.quantity} шт.</strong>
                <strong className="text-slate-950">{money(s.total)}</strong>
              </div>
            </div>
          </div>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <PaymentLabel value={s.paymentStatus} />
            <span className="max-w-full truncate text-xs text-slate-600">
              {s.deliveryStatus || (s.fulfillmentType ? fulfillmentNames[s.fulfillmentType] : 'Способ получения не выбран')}
            </span>
          </div>
          {s.plannedDeliveryDate && <p className="text-xs text-slate-500">План доставки: {s.plannedDeliveryDate}</p>}
          {possible.length > 0 && <div className="entity-row-action border-t border-slate-100 pt-2">
            <label className="flex items-center gap-2 text-xs font-medium text-slate-500">
              <GripVertical className="size-4 shrink-0" aria-hidden="true" />
              <span className="sr-only">Доступные переходы заказа</span>
              <select aria-label={'Изменить статус заказа '+s.saleNumber}
                className="min-h-9 min-w-0 flex-1 cursor-pointer rounded-lg border border-slate-200 bg-white px-2 text-xs text-blue-700"
                value="" onChange={e => {
                  if (e.target.value) onRequestMove(s, e.target.value as SaleStatus);
                }}>
                <option value="">Переместить / действия</option>
                {possible.map(t => <option key={t.target} value={t.target}>{t.label}</option>)}
              </select>
            </label>
          </div>}
        </article>;
      })}</div>}
    {q.hasNextPage && <Button className="mt-3 w-full" variant="outline" disabled={q.isFetchingNextPage}
      onClick={() => void q.fetchNextPage()}>{q.isFetchingNextPage ? 'Загружаем…' : 'Показать ещё'}</Button>}
    {total > rows.length && <p className="mt-2 text-xs text-slate-600">
      Показано {rows.length}/{total}. Остальные доступны через «Показать ещё».
    </p>}
  </section>;
}

export function SalesKanban({ filters }: { filters: SaleFilters }) {
  const { hasPermission } = useAuth();
  const navigate = useNavigate();
  const [dragged, setDragged] = useState<SaleSummary | null>(null);
  const [warning, setWarning] = useState('');
  const request = (sale: SaleSummary, target: SaleStatus) => {
    const transition = transitionForTarget(sale, target, hasPermission);
    setDragged(null);
    if (!transition) {
      setWarning('Этот переход запрещён. Для доставок используйте процесс доставки, для остальных операций — карточку заказа.');
      return;
    }
    setWarning('');
    navigate('/sales/' + encodeURIComponent(sale.id), {
      state: { summary: sale, kanbanAction: transition.action },
    });
  };
  const drop = (target: SaleStatus) => {
    if (dragged) request(dragged, target);
  };
  return <div className="space-y-4">
    <p className="rounded-xl border border-blue-100 bg-blue-50 px-4 py-3 text-sm text-blue-900">
      Перетащите заказ в разрешённую колонку или выберите действие в карточке.
      Статус изменится только после проверки серверных данных и вашего подтверждения.
      Заказы с доставкой завершаются через раздел «Доставки».
    </p>
    {warning && <p role="alert" className="rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">{warning}</p>}
    <div aria-label="Доска продаж" className="grid items-start gap-3 sm:grid-cols-2 2xl:grid-cols-4">
      {saleStatuses.map(status => <KanbanColumn key={status}
        status={status} filters={filters} dragged={dragged}
        onStart={s => { setWarning(''); setDragged(s); }}
        onEnd={() => setDragged(null)} onDropMove={drop} onRequestMove={request}/>)}
    </div>
  </div>;
}
