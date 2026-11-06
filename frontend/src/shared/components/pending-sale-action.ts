import { ApiClientError } from '../api/api-error';

/** No authoritative mutation outcome is known for timeout, conflict or transport errors. */
export function isUnknownSaleMutationResult(error: unknown): boolean {
  return !(error instanceof ApiClientError) || error.kind !== 'http' ||
    error.status === undefined || error.status === 408 ||
    error.status === 409 || error.status >= 500;
}

/** A pending irreversible sale action must never be replayed without server reconciliation. */
export type SaleAction = 'confirm' | 'fulfill' | 'cancel';
export type PendingSaleAction = { kind: 'missing' } | { kind: 'pending'; action: SaleAction } | { kind: 'invalid' };
const key = (userId: string, saleId: string) => 'chairx:sale:quick-pending:v1:' + encodeURIComponent(userId) + ':' + encodeURIComponent(saleId);
const operationKey = (userId: string, saleId: string) => key(userId, saleId) + ':operation-id';
const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

/** Legacy pending records intentionally have no operation id and stay fail-closed. */
export function readSaleOperationId(userId: string | undefined, saleId: string): string | null {
  if (!userId || readSaleAction(userId, saleId).kind !== 'pending') return null;
  try {
    const value = sessionStorage.getItem(operationKey(userId, saleId));
    return value && uuidPattern.test(value) ? value : null;
  } catch { return null; }
}

function newOperationId(): string {
  // CSPRNG only: never use Math.random for a business mutation identity.
  const bytes = new Uint8Array(16);
  globalThis.crypto.getRandomValues(bytes);
  bytes[6] = ((bytes[6] ?? 0) & 0x0f) | 0x40;
  bytes[8] = ((bytes[8] ?? 0) & 0x3f) | 0x80;
  const hex = Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('');
  return [hex.slice(0,8),hex.slice(8,12),hex.slice(12,16),hex.slice(16,20),hex.slice(20)].join('-');
}
function validAction(value: unknown): value is SaleAction {
  return value === 'confirm' || value === 'fulfill' || value === 'cancel';
}
export function readSaleAction(userId: string | undefined, saleId: string): PendingSaleAction {
  if (!userId || !saleId) return { kind: 'invalid' };
  try {
    const value = sessionStorage.getItem(key(userId, saleId));
    if (value === null) return { kind: 'missing' };
    if (value.length > 256) return { kind: 'invalid' };
    const data: unknown = JSON.parse(value);
    return validAction(data) ? { kind: 'pending', action: data } : { kind: 'invalid' };
  } catch { return { kind: 'invalid' }; }
}
export function saveSaleAction(userId: string, saleId: string, action: SaleAction): boolean {
  if (!validAction(action) || readSaleAction(userId, saleId).kind !== 'missing') return false;
  try {
    const value = JSON.stringify(action);
    sessionStorage.setItem(key(userId, saleId), value);
    const operationId = newOperationId();
    sessionStorage.setItem(operationKey(userId, saleId), operationId);
    return sessionStorage.getItem(key(userId, saleId)) === value &&
      readSaleOperationId(userId, saleId) === operationId;
  } catch { return false; }
}
export function clearSaleAction(userId: string, saleId: string): boolean {
  try {
    sessionStorage.removeItem(key(userId, saleId));
    sessionStorage.removeItem(operationKey(userId, saleId));
    return sessionStorage.getItem(key(userId, saleId)) === null &&
      sessionStorage.getItem(operationKey(userId, saleId)) === null;
  } catch { return false; }
}
