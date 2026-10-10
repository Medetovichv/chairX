import type { CreatePurchasePayment, Money, PaymentAccount, PurchasePaymentKind, PaymentSummary } from './types';

/** Procurement payments are positive integer soms, max 17 digits (backend @Digits(fraction=0)).
 * Do not use Number() or floating-point arithmetic for debt comparisons.
 */
export function isValidPaymentAmount(value: string): boolean {
  return /^[1-9][0-9]{0,16}$/.test(value.trim());
}

function exactCents(value: Money | null): bigint | null {
  if (value === null || value === undefined) return null;
  if (typeof value === 'number' && !Number.isSafeInteger(value)) return null;
  const raw = String(value);
  if (!/^[0-9]+(?:\.[0-9]{1,2})?$/.test(raw)) return null;
  const [whole, frac = ''] = raw.split('.');
  return BigInt(whole!) * 100n + BigInt(frac.padEnd(2, '0') || '0');
}

export function paymentRemaining(summary: PaymentSummary, kind: PurchasePaymentKind): Money | null {
  return kind === 'CARGO' ? summary.remainingCargo : summary.remainingSupplier;
}

export function validatePaymentAmount(value: string, remaining: Money | null): string | null {
  if (!isValidPaymentAmount(value)) return 'Укажите положительное целое число сомов, максимум 17 цифр. Копейки в оплатах закупок не поддерживаются.';
  const remainingCents = exactCents(remaining);
  if (remainingCents === null) return 'Невозможно точно проверить остаток. Проверьте задолженность в системе.';
  if (BigInt(value.trim()) * 100n > remainingCents) {
    return 'Сумма превышает остаток задолженности. Уменьшите сумму оплаты.';
  }
  return null;
}

export function preparePayment(
  kind: PurchasePaymentKind, account: PaymentAccount | '',
  amount: string, reference: string, comment: string,
  summary: PaymentSummary, idempotencyKey: string,
): { payload: CreatePurchasePayment | null; error: string | null } {
  if (account !== 'CASH' && account !== 'BANK') {
    return { payload: null, error: 'Выберите счёт списания: CASH или BANK.' };
  }
  const issue = validatePaymentAmount(amount, paymentRemaining(summary, kind));
  if (issue) return { payload: null, error: issue };
  if (reference.trim().length > 200) return { payload: null, error: 'Номер или ссылка на платёж: максимум 200 символов.' };
  if (comment.trim().length > 1000) return { payload: null, error: 'Комментарий: максимум 1000 символов.' };
  return { error: null, payload: {
    idempotencyKey,
    paymentKind: kind,
    account,
    amount: amount.trim(),
    reference: reference.trim() || null,
    comment: comment.trim() || null,
  } };
}

export function matchesCommittedPayment(
  payment: { idempotencyKey: string; paymentKind: PurchasePaymentKind; account: PaymentAccount; amount: Money },
  request: CreatePurchasePayment,
): boolean {
  return payment.idempotencyKey === request.idempotencyKey &&
    payment.paymentKind === request.paymentKind &&
    payment.account === request.account &&
    exactCents(payment.amount) === BigInt(request.amount) * 100n;
}
