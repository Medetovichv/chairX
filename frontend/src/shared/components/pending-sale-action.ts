/** A pending irreversible sale action must never be replayed without server reconciliation. */
export type SaleAction = 'confirm' | 'fulfill' | 'cancel';
export type PendingSaleAction = { kind: 'missing' } | { kind: 'pending'; action: SaleAction } | { kind: 'invalid' };
const key = (userId: string, saleId: string) => 'chairx:sale:quick-pending:v1:' + encodeURIComponent(userId) + ':' + encodeURIComponent(saleId);
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
    return sessionStorage.getItem(key(userId, saleId)) === value;
  } catch { return false; }
}
export function clearSaleAction(userId: string, saleId: string): boolean {
  try {
    sessionStorage.removeItem(key(userId, saleId));
    return sessionStorage.getItem(key(userId, saleId)) === null;
  } catch { return false; }
}
