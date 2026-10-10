import type { CreatePurchasePayment } from './types';
import { isValidPaymentAmount } from './payment-validation';

const PREFIX = 'chairx:purchase-payment:pending:v1:';

function storageKey(userId: string, purchaseId: string): string {
  return PREFIX + encodeURIComponent(userId) + ':' + encodeURIComponent(purchaseId);
}

function validPayload(value: unknown): value is CreatePurchasePayment {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  const p = value as Record<string, unknown>;
  return typeof p.idempotencyKey === 'string' &&
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(p.idempotencyKey) &&
    (p.paymentKind === 'SUPPLIER' || p.paymentKind === 'CARGO') &&
    (p.account === 'CASH' || p.account === 'BANK') &&
    typeof p.amount === 'string' && isValidPaymentAmount(p.amount) &&
    (p.reference === null || typeof p.reference === 'string' && p.reference.length <= 200) &&
    (p.comment === null || typeof p.comment === 'string' && p.comment.length <= 1000);
}

/** Session storage is per-tab and user-scoped. It is not a finance ledger. */
export type PendingPaymentRead =
  | { kind: 'missing' }
  | { kind: 'pending'; payload: CreatePurchasePayment }
  | { kind: 'invalid' };

export function readPendingPayment(userId: string | undefined, purchaseId: string): PendingPaymentRead {
  if (!userId || !purchaseId) return { kind: 'invalid' };
  try {
    const raw = sessionStorage.getItem(storageKey(userId, purchaseId));
    if (raw === null) return { kind: 'missing' };
    if (raw.length > 4096) return { kind: 'invalid' };
    const value: unknown = JSON.parse(raw);
    return validPayload(value) ? { kind: 'pending', payload: value } : { kind: 'invalid' };
  } catch {
    return { kind: 'invalid' };
  }
}

/**
 * Never overwrite a different pending payment. An identical payload may be
 * saved again when retrying the original POST with its existing UUID.
 */
export function savePendingPayment(
  userId: string, purchaseId: string, payload: CreatePurchasePayment,
): boolean {
  if (!validPayload(payload)) return false;
  const existing = readPendingPayment(userId, purchaseId);
  if (existing.kind === 'invalid') return false;
  if (existing.kind === 'pending') return JSON.stringify(existing.payload) === JSON.stringify(payload);
  try {
    const key = storageKey(userId, purchaseId);
    const raw = JSON.stringify(payload);
    sessionStorage.setItem(key, raw);
    return sessionStorage.getItem(key) === raw;
  } catch {
    return false;
  }
}

/** Clear only after server-confirmed success or definite first-request failure. */
export function clearPendingPayment(userId: string, purchaseId: string): void {
  try {
    sessionStorage.removeItem(storageKey(userId, purchaseId));
  } catch {
    // Fail closed: the old key remains for reconciliation after reload.
  }
}
