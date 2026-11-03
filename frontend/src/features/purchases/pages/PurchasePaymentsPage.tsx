import { useState } from 'react';
import { Banknote, ArrowUpRight, ClipboardList } from 'lucide-react';
import { PurchasePaymentOverview } from '../components/PurchasePaymentOverview';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router';
import { useAuth } from '../../auth/AuthProvider';
import { ApiClientError } from '../../../shared/api/api-error';
import { PageHeader } from '../../../shared/components/PageHeader';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState';
import { ForbiddenPage } from '../../../pages/ForbiddenPage';
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
  return <div className="space-y-5 text-[#182d38] sm:space-y-6">
    <PageHeader title="Оплаты" description="Просмотр задолженности и проведение выплат поставщику/за карго по конкретной закупке." />
    <PurchaseTabs />
    {notice && <p role="status" className="rounded-xl bg-emerald-50 p-3 text-sm text-emerald-800">{notice}</p>}
    {!purchaseId && canReadPurchases && <section aria-label="Выбор закупки для оплаты"
      className="space-y-3 rounded-2xl border border-[#dfe7eb] bg-white p-4 sm:p-6">
      <h2 className="flex items-center gap-2 text-base font-bold text-[#182d38]"><ClipboardList className="size-4 text-[#286e66]" aria-hidden="true"/>Выберите закупку</h2>
      <p className="text-sm text-[#516977]">Показаны последние закупки. Остальные можно найти в общем списке.</p>
      {purchaseList.isPending ? <LoadingState label="Загружаем закупки…" /> :
        purchaseList.isError ? <ErrorState error={purchaseList.error} onRetry={() => void purchaseList.refetch()} /> :
        !purchaseList.data?.items.length ? <p className="text-sm text-[#516977]">Закупки не найдены.</p> :
        <div className="divide-y divide-[#e3e9ee]">
          {purchaseList.data.items.map(p => <button key={p.id} type="button"
            className="flex min-h-12 w-full flex-wrap items-center justify-between gap-2 rounded-lg px-3 py-3 text-left hover:bg-[#f2f7f6] focus-visible:outline-2 focus-visible:outline-[#267f79]"
            onClick={() => { setParams({ purchaseId:p.id }); setNotice(''); }}>
            <span className="font-semibold text-[#182d38]">{p.supplierName} · {formatDate(p.createdAt)}</span>
            <span className="inline-flex items-center gap-2 text-sm font-bold tabular-nums text-[#286e66]">{formatMoney(p.totalCost)}<ArrowUpRight className="size-4" aria-hidden="true"/></span>
          </button>)}
        </div>}
      <Link to="/purchases" className="inline-flex min-h-11 items-center font-semibold text-[#236e66] underline-offset-4 hover:underline">Открыть все закупки</Link>
    </section>}
    {!purchaseId ? <p className="text-sm text-slate-600">
      {canReadPurchases ? 'Выберите закупку из списка выше.' : 'Откройте оплаты по ссылке из доступной вам карточки закупки.'}
    </p> : summary.isPending ? <LoadingState label="Загружаем оплаты закупки…" /> :
      summary.isError ? <ErrorState error={summary.error} onRetry={() => void summary.refetch()} /> :
      summary.data ? <section aria-label="Оплаты выбранной закупки" className="space-y-4">
        <PurchasePaymentOverview summary={summary.data}/>
        {canCreate && <PurchasePaymentForm key={purchaseId} purchaseId={purchaseId}
          summary={summary.data} onPosted={payment => { void paymentPosted(payment); }} />}
        {!canCreate && <p className="text-sm text-slate-600">
          Просмотр доступен. Для проведения оплаты требуется разрешение PURCHASE_PAYMENTS_CREATE.
        </p>}
        <h2 className="flex items-center gap-2 text-base font-bold text-[#182d38]"><Banknote className="size-4 text-[#286e66]" aria-hidden="true"/>История оплат</h2>
        {!summary.data.payments.length ? <p className="rounded-xl border border-[#dfe7eb] bg-white p-4 text-sm text-[#516977]">Платежей по этой закупке пока нет.</p> :
          <div className="divide-y divide-[#eaf0f0] overflow-hidden rounded-2xl border border-[#dfe7eb] bg-white">
            {summary.data.payments.map(p=><article key={p.id} className="space-y-2 p-5 text-sm">
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
