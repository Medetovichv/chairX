import { useEffect, useState, type FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router';
import { useAuth } from '../../auth/AuthProvider';
import { ApiClientError } from '../../../shared/api/api-error';
import { PageHeader } from '../../../shared/components/PageHeader';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState';
import { ForbiddenPage } from '../../../pages/ForbiddenPage';
import { Input } from '../../../shared/ui/input';
import { Button } from '../../../shared/ui/button';
import { purchaseApi, purchaseKeys } from '../api';
import { formatDate, formatMoney } from '../validation';
import { PurchaseTabs } from '../components/PurchaseTabs';

const validId = (value:string) => /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
export function PurchasePaymentsPage() {
  const { hasPermission } = useAuth();
  const allowed = hasPermission('PURCHASE_PAYMENTS_READ');
  const [params, setParams] = useSearchParams();
  const query = params.get('purchaseId') ?? '';
  const [input, setInput] = useState(query);
  useEffect(() => setInput(query), [query]);
  const purchaseId = validId(query) ? query : '';
  const summary = useQuery({ queryKey: purchaseKeys.payments(purchaseId),
    queryFn: () => purchaseApi.payments(purchaseId), enabled: allowed && !!purchaseId });
  function submit(e: FormEvent) {
    e.preventDefault();
    if (validId(input.trim())) setParams({ purchaseId: input.trim() });
  }
  if (!allowed || summary.error instanceof ApiClientError && summary.error.status===403) return <ForbiddenPage />;
  return <div className="space-y-6">
    <PageHeader title="Оплаты" description="Только чтение: выберите конкретную закупку по UUID. Глобального списка платежей в API нет." />
    <PurchaseTabs />
    <form onSubmit={submit} className="rounded-2xl border border-slate-200 bg-white p-4">
      <label className="block space-y-2 text-sm font-semibold">UUID закупки
        <Input value={input} aria-label="UUID закупки для оплат" onChange={e=>setInput(e.target.value)}
          placeholder="Вставьте UUID закупки" />
      </label>
      <Button type="submit" disabled={!validId(input.trim())} className="mt-3">Показать оплаты</Button>
      {hasPermission('PURCHASE_READ') && <p className="mt-2 text-sm">
        <Link to="/purchases" className="font-semibold text-indigo-700">Найти закупку в списке</Link>
      </p>}
    </form>
    {!purchaseId ? <p className="text-sm text-slate-600">
      Введите UUID конкретной закупки. Создание выплат относится к F06.1.
    </p> : summary.isPending ? <LoadingState label="Загружаем оплаты закупки…" /> :
      summary.isError ? <ErrorState error={summary.error} onRetry={() => void summary.refetch()} /> :
      summary.data ? <section aria-label="Оплаты выбранной закупки" className="space-y-4">
        <div className="grid gap-3 rounded-2xl border border-slate-200 bg-white p-5 sm:grid-cols-2 xl:grid-cols-3">
          {([
            ['Обязательства перед поставщиком',summary.data.supplierTotal],
            ['Обязательства по карго',summary.data.cargoTotal],
            ['Оплачено поставщику',summary.data.paidSupplier],
            ['Оплачено карго',summary.data.paidCargo],
            ['Осталось поставщику',summary.data.remainingSupplier],
            ['Осталось по карго',summary.data.remainingCargo],
          ] as const).map(([label,sum])=><div key={label}>
            <p className="text-xs text-slate-600">{label}</p><p className="mt-1 text-lg font-bold">{formatMoney(sum)}</p>
          </div>)}
        </div>
        {!summary.data.payments.length ? <p className="text-sm text-slate-600">Платежей по этой закупке пока нет.</p> :
          <div className="divide-y divide-slate-100 rounded-2xl border border-slate-200 bg-white">
            {summary.data.payments.map(p=><article key={p.id} className="space-y-1 p-4 text-sm">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <strong>{p.paymentKind==='CARGO'?'Карго':'Поставщику'} · {p.account}</strong>
                <strong>{formatMoney(p.amount)}</strong>
              </div>
              <p className="text-slate-500">Дата: {formatDate(p.createdAt)} · Reference: {p.reference || '—'}</p>
              {p.comment && <p className="whitespace-pre-wrap text-slate-600">{p.comment}</p>}
            </article>)}
          </div>}
      </section> : null}
  </div>;
}
