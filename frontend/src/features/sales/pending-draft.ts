import type { CreateDraftInput } from './types';
import { checkDraft, uuidValid } from './validation';

const prefix = 'chairx:sales:draft-pending:v1:';
function key(userId:string) {return prefix + encodeURIComponent(userId);}
function valid(input: unknown): input is CreateDraftInput {
  if (!input || typeof input !== 'object' || Array.isArray(input)) return false;
  const x = input as Record<string, unknown>;
  if (typeof x.idempotencyKey !== 'string' || !uuidValid(x.idempotencyKey) ||
      (x.customerId !== null && typeof x.customerId !== 'string') ||
      (x.fulfillmentType !== null && !['SELF_PICKUP','CITY_DELIVERY','REGION_DELIVERY'].includes(String(x.fulfillmentType))) ||
      !Array.isArray(x.items) || x.items.length > 1000 ||
      (x.comment !== null && typeof x.comment !== 'string')) return false;
  return x.items.every((row: unknown) => {
    if (typeof row !== 'object' || row === null) return false;
    const item = row as Record<string, unknown>;
    return typeof item.productVariantId === 'string' && typeof item.warehouseId === 'string' &&
      typeof item.quantity === 'number' && typeof item.unitSalePrice === 'string';
  }) && checkDraft(x as unknown as CreateDraftInput) === null;
}
export type PendingDraft = {kind:'none'} | {kind:'pending'; payload:CreateDraftInput} | {kind:'invalid'};
export function readPendingDraft(userId:string | undefined): PendingDraft {
  if (!userId) return {kind:'invalid'};
  try {
    const value = sessionStorage.getItem(key(userId));
    if (value === null) return {kind:'none'};
    if (value.length > 100_000) return {kind:'invalid'};
    const parsed:unknown = JSON.parse(value);
    return valid(parsed) ? {kind:'pending',payload:parsed} : {kind:'invalid'};
  } catch {return {kind:'invalid'};}
}
export function savePendingDraft(userId:string,payload:CreateDraftInput) {
  if (!valid(payload)) return false;
  const previous = readPendingDraft(userId);
  if (previous.kind === 'invalid') return false;
  if (previous.kind === 'pending') return JSON.stringify(previous.payload) === JSON.stringify(payload);
  try {
    const raw=JSON.stringify(payload);
    sessionStorage.setItem(key(userId),raw);
    return sessionStorage.getItem(key(userId)) === raw;
  } catch {return false;}
}
export function clearPendingDraft(userId:string) {
  try {sessionStorage.removeItem(key(userId));} catch { /* retain operation until manually reconciled */ }
}
