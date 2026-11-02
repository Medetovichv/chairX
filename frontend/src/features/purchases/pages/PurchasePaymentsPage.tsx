import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router';
import { useAuth } from '../../auth/AuthProvider';
import { ApiClientError } from '../../../shared/api/api-error';
import { PageHeader } from '../../../shared/components/PageHeader';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState';
import { ForbiddenPage } from '../../../pages/ForbiddenPage';
import { Button } from '../../../shared/ui/button';
import { purchaseApi, purchaseKeys } from '../api';
import { formatDate, formatMoney } from '../validation';
import { PurchaseTabs } from '../components/PurchaseTabs';
import { PurchasePaymentForm } from '../components/PurchasePaymentForm';
import type { Payment } from '../types';

const validId = (value:string) => /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
export function PurchasePaymentsPage() {
  const { hasPermission } = useAuth();
  const allowed = hasPermission('PURCHASE_PAYMENTS_READ');
  const canCreate = hasPermission('PURCHASE_PAYMENTS_CREATE');
  const client = useQueryClient();
  const [notice, setNotice] = useState('');
  const [params, setParams] = useSearchParams();
  const query = params.get('purchaseId') ?? '';
  const canReadPurchases = hasPermission('PURCHASE_READ');
  const purchaseList = useQuery({
    queryKey: [...purchaseKeys.overview({ status:'', supplierId:'', from:'', to:'', number:'', page:0 }), 'payment-picker'],
    queryFn: () => purchaseApi.overview({ status:'', supplierId:'', from:'', to:'', number:'', page:0 }, true),
    enabled: allowed && canReadPurchases && !validId(query),
  });
  const purchaseId = validId(query) ? query : '';
  const summary = useQuery({ queryKey: purchaseKeys.payments(purchaseId),
    queryFn: () => purchaseApi.payments(purchaseId), enabled: allowed && !!purchaseId });
  async function paymentPosted(payment: Payment) {
    setNotice('Оплата проведена: ' + payment.amount + ' сом, операция ' + payment.id + '.');
    await Promise.all([
      client.invalidateQueries({ queryKey: purchaseKeys.payments(payment.purchaseId) }),
      client.invalidateQueries({ queryKey: purchaseKeys.overviews }),
      client.invalidateQueries({ queryKey: ['finance'] }),
      client.invalidateQueries({ queryKey: ['daily-closing'] }),
    ]);
  }
  if (!allowed || summary.error instanceof ApiClientError && summary.error.status===403) return <ForbiddenPage />;
  return <div className="space-y-6 rounded-[24px] bg-[#e9eef0] p-3 text-[#182d38] sm:p-6">
    <PageHeader title="Оплаты" description="Просмотр задолженности и проведение выплат поставщику/за карго по конкретной закупке." />
    <PurchaseTabs />
    {notice && <p role="status" className="rounded-xl bg-emerald-50 p-3 text-sm text-emerald-800">{notice}</p>}
    {!purchaseId && canReadPurchases && <section aria-label="Выбор закупки для оплаты"
      className="space-y-3 rounded-[20px] border border-[#e3e9ee] bg-white p-4 sm:p-6">
      <h3 className="text-base font-semibold">Выберите закупку</h3>
      <p className="text-sm text-[#516977]">Показаны последние закупки. Остальные можно найти в общем списке.</p>
      {purchaseList.isPending ? <LoadingState label="Загружаем закупки…" /> :
        purchaseList.isError ? <ErrorState error={purchaseList.error} onRetry={() => void purchaseList.refetch()} /> :
        !purchaseList.data?.items.length ? <p className="text-sm text-[#516977]">Закупки не найдены.</p> :
        <div className="divide-y divide-[#e3e9ee]">
          {purchaseList.data.items.map(p => <button key={p.id} type="button"
            className="flex min-h-11 w-full flex-wrap items-center justify-between gap-2 px-2 py-3 text-left hover:bg-[#edf3f6] focus-visible:outline-2 focus-visible:outline-[#416c82]"
            onClick={() => { setParams({ purchaseId:p.id }); setNotice(''); }}>
            <span className="font-semibold text-[#182d38]">{p.supplierName} · {formatDate(p.createdAt)}</span>
            <span className="text-sm tabular-nums text-[#416c82]">{formatMoney(p.totalCost)}</span>
          </button>)}
        </div>}
      <Link to="/purchases" className="inline-flex min-h-11 items-center font-semibold text-[#416c82] underline-offset-4 hover:underline">Открыть все закупки</Link>
    </section>}
    {!purchaseId ? <p className="text-sm text-slate-600">
      {canReadPurchases ? 'Выберите закупку из списка выше.' : 'Откройте оплаты по ссылке из доступной вам карточки закупки.'}
    </p> : summary.isPending ? <LoadingState label="Загружаем оплаты закупки…" /> :
      summary.isError ? <ErrorState error={summary.error} onRetry={() => void summary.refetch()} /> :
      summary.data ? <section aria-label="Оплаты выбранной закупки" className="space-y-4">
        {canCreate && <PurchasePaymentForm key={purchaseId} purchaseId={purchaseId}
          summary={summary.data} onPosted={payment => { void paymentPosted(payment); }} />}
        {!canCreate && <p className="text-sm text-slate-600">
          Просмотр доступен. Для проведения оплаты требуется разрешение PURCHASE_PAYMENTS_CREATE.
        </p>}
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
