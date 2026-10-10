import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { apiClient } from '../../../shared/api/client';
import { ApiClientError, userFacingApiError } from '../../../shared/api/api-error';
import { Button } from '../../../shared/ui/button';
import { Input } from '../../../shared/ui/input';
import { ConfirmDialog } from '../../../shared/ui/confirm-dialog';
import { purchaseApi, purchaseKeys } from '../api';
import { formatMoney } from '../validation';
import { matchesCommittedPayment, paymentRemaining, preparePayment } from '../payment-validation';
import type { CreatePurchasePayment, Money, Payment, PaymentAccount, PurchasePaymentKind, PaymentSummary } from '../types';

interface FinanceAccountResponse { code: PaymentAccount; balance: Money; initialized: boolean }

const nameForKind = (kind: PurchasePaymentKind) => kind === 'SUPPLIER' ? 'поставщику' : 'за карго';

function outcomeUncertain(cause: unknown): boolean {
  if (!(cause instanceof ApiClientError)) return true;
  return cause.kind !== 'http' || cause.status === undefined || cause.status >= 500;
}

/**
 * A payment is not a normal retryable form submission. One user decision
 * produces exactly one immutable payload and idempotencyKey, including
 * after transport errors (the backend may already have posted the money).
 */
export function PurchasePaymentForm({ purchaseId, summary, onPosted }: {
  purchaseId: string;
  summary: PaymentSummary;
  onPosted: (payment: Payment) => void;
}) {
  const { hasPermission } = useAuth();
  const client = useQueryClient();
  const canCreate = hasPermission('PURCHASE_PAYMENTS_CREATE') &&
    hasPermission('PURCHASE_PAYMENTS_READ');
  const canSeeFinance = hasPermission('FINANCE_READ');
  const finance = useQuery({
    queryKey: ['finance', 'accounts'],
    queryFn: () => apiClient.get<FinanceAccountResponse[]>('/api/finance/accounts'),
    enabled: canCreate && canSeeFinance,
  });

  const [kind, setKind] = useState<PurchasePaymentKind>('SUPPLIER');
  const [account, setAccount] = useState<PaymentAccount | ''>('');
  const [amount, setAmount] = useState('');
  const [reference, setReference] = useState('');
  const [comment, setComment] = useState('');
  const [candidate, setCandidate] = useState<CreatePurchasePayment | null>(null);
  const [uncertain, setUncertain] = useState<CreatePurchasePayment | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  const remaining = paymentRemaining(summary, kind);
  const activeAccount = finance.data?.find(value => value.code === account);
  const blockedAccount = canSeeFinance && activeAccount?.initialized === false;
  const locked = busy || !!candidate || !!uncertain;

  // A browser refresh during an unresolved payment loses the in-memory key.
  // Warn instead of silently allowing a new payment with another UUID.
  useEffect(() => {
    if (!uncertain) return;
    const warn = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [uncertain]);

  function prepare(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending.current || locked || !canCreate) return;
    if (blockedAccount) {
      setError('Финансовый счёт не инициализирован. Обратитесь к администратору.');
      return;
    }
    const { payload, error: problem } = preparePayment(
      kind, account, amount, reference, comment, summary, crypto.randomUUID(),
    );
    if (problem || !payload) {
      setError(problem || 'Некорректные данные оплаты.');
      return;
    }
    setError('');
    setCandidate(payload);
  }

  function completed(saved: Payment) {
    setUncertain(null);
    setCandidate(null);
    setError('');
    setAmount('');
    setReference('');
    setComment('');
    setAccount('');
    onPosted(saved);
  }

  async function post(payload: CreatePurchasePayment) {
    if (pending.current || !canCreate) return;
    pending.current = true;
    setBusy(true);
    setError('');
    try {
      const posted = await purchaseApi.postPayment(purchaseId, payload);
      completed(posted);
    } catch (cause) {
      if (outcomeUncertain(cause)) {
        setUncertain(payload);
        setError(userFacingApiError(cause) + ' Результат неизвестен: не создавайте платёж с новым ключом.');
      } else {
        // If an earlier POST was ambiguous, even a definitive replay error
        // cannot establish whether that first money posting committed.
        setError(userFacingApiError(cause) + (uncertain ?
          ' Первую операцию необходимо проверить по истории. Новый платёж запрещён.' : ''));
      }
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }

  async function reconcile() {
    if (pending.current || !uncertain) return;
    pending.current = true; setBusy(true); setError('');
    try {
      const fresh = await purchaseApi.payments(purchaseId);
      client.setQueryData(purchaseKeys.payments(purchaseId), fresh);
      const committed = fresh.payments.find(p => p.idempotencyKey === uncertain.idempotencyKey);
      if (committed && matchesCommittedPayment(committed, uncertain)) {
        completed(committed);
      } else if (committed) {
        setError('Платёж с таким ключом обнаружен, но данные не совпадают. Не повторяйте операцию. Обратитесь к администратору.');
      } else {
        setError('Платёж с этим ключом пока не обнаружен. Не отправляйте новый платёж: повторите только исходный запрос с тем же ключом.');
      }
    } catch (cause) {
      setError(userFacingApiError(cause) + ' Состояние исходного платежа остаётся неизвестным.');
    } finally {
      pending.current = false; setBusy(false);
    }
  }

  if (!canCreate) return null;

  return <section aria-label="Проведение оплаты закупки"
    className="space-y-5 rounded-2xl border border-indigo-200 bg-white p-4 shadow-sm sm:p-6">
    <div>
      <h3 className="text-xl font-bold text-slate-900">Провести оплату</h3>
      <p className="mt-1 text-sm text-slate-600">
        Списывается со счёта CASH/BANK в сомах. Только целые суммы; сервер проверяет остаток долга,
        доступный баланс и финансовое закрытие дня. Проведение нельзя отменить на этом экране.
      </p>
    </div>
    <form aria-label="Новая оплата закупки" onSubmit={prepare} className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2">
        <label className="space-y-2 text-sm font-semibold text-slate-700">За что платим
          <select aria-label="Категория оплаты" value={kind} disabled={locked}
            onChange={event => {setKind(event.target.value as PurchasePaymentKind);setError('');}}
            className="min-h-11 w-full rounded-xl border border-slate-200 bg-white px-3">
            <option value="SUPPLIER">Поставщику</option>
            <option value="CARGO">За карго</option>
          </select>
        </label>
        <label className="space-y-2 text-sm font-semibold text-slate-700">Счёт списания
          <select aria-label="Счёт оплаты" value={account} disabled={locked}
            onChange={event => {setAccount(event.target.value as PaymentAccount | '');setError('');}}
            className="min-h-11 w-full rounded-xl border border-slate-200 bg-white px-3">
            <option value="">Выберите CASH / BANK</option>
            <option value="CASH">CASH — наличные</option>
            <option value="BANK">BANK — банковский счёт</option>
          </select>
        </label>
      </div>
      <p className="text-sm text-slate-600">
        Остаток долга {nameForKind(kind)}: <strong>{formatMoney(remaining)}</strong>.
        {remaining === null && ' Карго ещё не определено: выплатить его сейчас нельзя.'}
      </p>
      {canSeeFinance && account && activeAccount && <p className="text-sm text-slate-600">
        Счёт {account}: {activeAccount.initialized ?
          'текущий баланс ' + formatMoney(activeAccount.balance) :
          'не инициализирован — оплата заблокирована'}.
      </p>}
      {canSeeFinance && finance.isError &&
        <p className="text-sm text-amber-800">Баланс счёта сейчас недоступен; при проведении его проверит сервер.</p>}
      <label className="block space-y-2 text-sm font-semibold">Сумма, сом *
        <Input aria-label="Сумма оплаты в сомах" inputMode="numeric" autoComplete="off"
          placeholder="Например: 15000" value={amount} disabled={locked} maxLength={17}
          onChange={event => {setAmount(event.target.value);setError('');}} />
      </label>
      <label className="block space-y-2 text-sm font-semibold">Номер перевода / reference
        <Input aria-label="Номер перевода" maxLength={200} value={reference}
          disabled={locked} onChange={event => setReference(event.target.value)} />
      </label>
      <label className="block space-y-2 text-sm font-semibold">Комментарий
        <textarea aria-label="Комментарий оплаты" rows={2} maxLength={1000}
          disabled={locked} value={comment} onChange={event => setComment(event.target.value)}
          className="w-full rounded-xl border border-slate-200 p-3 text-sm" />
      </label>
      {error && <p role="alert" className="rounded-xl bg-red-50 p-3 text-sm text-red-800">{error}</p>}
      {uncertain && <div role="alert" className="space-y-3 rounded-xl border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900">
        <p className="font-bold">Результат оплаты неизвестен. Не создавайте вторую операцию.</p>
        <p>Операция: {nameForKind(uncertain.paymentKind)} · {uncertain.account} · {uncertain.amount} сом.</p>
        <p className="break-all text-xs">Ключ повторного запроса: {uncertain.idempotencyKey}</p>
        <p>Если платёж уже был проведён, повтор с тем же ключом не должен списать деньги второй раз.</p>
        <div className="flex flex-wrap gap-2">
          <Button type="button" variant="outline" disabled={busy} onClick={() => void reconcile()}>
            Проверить по истории
          </Button>
          <Button type="button" disabled={busy} onClick={() => void post(uncertain)}>
            Повторить исходную операцию
          </Button>
        </div>
      </div>}
      <div className="flex flex-wrap gap-3">
        <Button type="submit" disabled={locked || blockedAccount || remaining === null}>
          {busy ? 'Проводим…' : 'Проверить и подтвердить оплату'}
        </Button>
      </div>
    </form>
    <ConfirmDialog open={!!candidate}
      onOpenChange={open => {if (!open) setCandidate(null);}}
      title="Подтвердить списание денег?"
      description={candidate ?
        'Закупка: ' + purchaseId + '. Оплата ' + nameForKind(candidate.paymentKind) +
        ': ' + candidate.amount + ' сом со счёта ' + candidate.account +
        '. Остаток задолженности до операции: ' +
        formatMoney(paymentRemaining(summary, candidate.paymentKind)) +
        '. После подтверждения сервер выполнит финансовую проводку.' : ''}
      confirmLabel="Провести оплату" busy={busy}
      onConfirm={() => {
        const request = candidate;
        setCandidate(null);
        if (request) void post(request);
      }} />
  </section>;
}
