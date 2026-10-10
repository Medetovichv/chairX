import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { EmptyState } from '../../../shared/components/EmptyState';
import { ErrorState } from '../../../shared/components/ErrorState';
import { LoadingState } from '../../../shared/components/LoadingState';
import { customerKeys, customersApi } from '../api';
import { customerDate, saleAmount } from '../validation';
import { CustomerPager } from './CustomerPager';
import type { SaleSummary } from '../types';

function SaleLines({ lines }: { lines: SaleSummary[] }) {
  return <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
    <div className="hidden overflow-x-auto lg:block">
      <table className="w-full min-w-240 text-left text-sm">
        <thead className="bg-slate-50"><tr>
          {['Номер', 'Дата', 'Товары', 'Количество', 'Сумма', 'Продажа', 'Оплата', 'Доставка'].map((name) =>
            <th key={name} scope="col" className="px-4 py-3 font-semibold text-slate-600">{name}</th>)}
        </tr></thead>
        <tbody className="divide-y divide-slate-100">
          {lines.map((sale) => <tr key={sale.id}>
            <td className="px-4 py-4 font-semibold">{sale.saleNumber}</td>
            <td className="px-4 py-4">{customerDate(sale.createdAt)}</td>
            <td className="px-4 py-4">{sale.products || '—'}</td>
            <td className="px-4 py-4">{sale.quantity}</td>
            <td className="px-4 py-4">{saleAmount(sale.total)}</td>
            <td className="px-4 py-4">{sale.status}</td>
            <td className="px-4 py-4">{sale.paymentStatus || '—'}</td>
            <td className="px-4 py-4">{sale.deliveryStatus || '—'}</td>
          </tr>)}
        </tbody>
      </table>
    </div>
    <div className="divide-y divide-slate-100 lg:hidden">
      {lines.map((sale) => <article key={sale.id} className="space-y-2 p-4 text-sm">
        <div className="flex items-center justify-between gap-3">
          <strong className="break-all">{sale.saleNumber}</strong>
          <span className="text-slate-500">{customerDate(sale.createdAt)}</span>
        </div>
        <p>{sale.products || 'Товары не указаны'} · {sale.quantity} шт.</p>
        <p className="font-bold text-slate-900">{saleAmount(sale.total)}</p>
        <p className="break-words text-slate-500">Продажа: {sale.status} · Оплата: {sale.paymentStatus || '—'}</p>
        <p className="text-slate-500">Доставка: {sale.deliveryStatus || '—'}</p>
      </article>)}
    </div>
  </div>;
}

export function CustomerOrderHistory({ customerId }: { customerId: string }) {
  const { hasPermission } = useAuth();
  const allowed = hasPermission('CUSTOMERS_READ') && hasPermission('SALES_READ');
  const [page, setPage] = useState(0);
  const history = useQuery({
    queryKey: customerKeys.history(customerId, page),
    queryFn: () => customersApi.history(customerId, page),
    enabled: allowed,
  });
  return <section aria-label="История заказов" className="space-y-3">
    <h3 className="text-xl font-bold text-slate-900">История заказов</h3>
    {!allowed ? <div className="rounded-2xl border border-slate-200 bg-slate-50 p-5 text-sm text-slate-600">
      История заказов недоступна для вашей роли.
    </div> : history.isPending ? <LoadingState label="Загружаем историю заказов…" />
      : history.isError ? <ErrorState error={history.error} onRetry={() => void history.refetch()} />
        : history.data && history.data.total === 0 ?
          <EmptyState title="История заказов пуста" description="У клиента пока нет оформленных заказов." />
          : history.data ? <div>
            <SaleLines lines={history.data.items} />
            <CustomerPager page={history.data.page} total={history.data.total}
              size={history.data.size} onPage={setPage} label="заказов" />
          </div> : null}
  </section>;
}
