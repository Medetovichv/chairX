import { beforeEach, describe, expect, it } from 'vitest';
import { clearSaleAction, readSaleAction, saveSaleAction, isUnknownSaleMutationResult } from './pending-sale-action';
import { ApiClientError } from '../api/api-error';

describe('pending sale action journal', () => {
  beforeEach(() => sessionStorage.clear());
  it('records an operation before sending and restores it after a remount', () => {
    expect(readSaleAction('staff', 'order')).toEqual({ kind: 'missing' });
    expect(saveSaleAction('staff', 'order', 'confirm')).toBe(true);
    expect(readSaleAction('staff', 'order')).toEqual({ kind: 'pending', action: 'confirm' });
    expect(saveSaleAction('staff', 'order', 'cancel')).toBe(false);
    expect(clearSaleAction('staff', 'order')).toBe(true);
    expect(readSaleAction('staff', 'order')).toEqual({ kind: 'missing' });
  });
  it.each([408,409,500,503])('preserves a pending action after ambiguous HTTP %i', status => {
    expect(isUnknownSaleMutationResult(new ApiClientError('http','REQUEST_FAILED',status))).toBe(true);
  });
  it.each([400,401,403,422])('clears a provisional action after definitive HTTP %i', status => {
    expect(isUnknownSaleMutationResult(new ApiClientError('http','REQUEST_REJECTED',status))).toBe(false);
  });
  it('treats lost responses as an unknown server mutation', () => {
    expect(isUnknownSaleMutationResult(new TypeError('Connection reset after POST'))).toBe(true);
  });
  it.each([408,409,500])('treats an HTTP %i response to sale draft PUT as an unverified mutation', status => {
    // SaleDetailPage uses this classifier for both irreversible actions and draft PUT.
    // The screen must keep its submitted draft available for server reconciliation.
    expect(isUnknownSaleMutationResult(new ApiClientError('http','REQUEST_FAILED',status))).toBe(true);
  });
  it('distinguishes a pre-mutation HTTP 422 validation rejection from a timed-out draft PUT', () => {
    expect(isUnknownSaleMutationResult(new ApiClientError('http','INVALID_DRAFT',422))).toBe(false);
    expect(isUnknownSaleMutationResult(new TypeError('Socket closed after draft PUT'))).toBe(true);
  });
  it('allows Quick View to observe a durable marker written by full sale detail', () => {
    expect(saveSaleAction('staff','sale-123','fulfill')).toBe(true);
    expect(readSaleAction('staff','sale-123')).toEqual({kind:'pending',action:'fulfill'});
    expect(saveSaleAction('staff','sale-123','cancel')).toBe(false);
  });
  it('isolates employees and sales', () => {
    expect(saveSaleAction('staff', 'order', 'fulfill')).toBe(true);
    expect(readSaleAction('other', 'order')).toEqual({ kind: 'missing' });
    expect(readSaleAction('staff', 'other')).toEqual({ kind: 'missing' });
  });
  it('rejects unknown or corrupt data and fails closed', () => {
    const key = 'chairx:sale:quick-pending:v1:staff:order';
    sessionStorage.setItem(key, JSON.stringify('delete'));
    expect(readSaleAction('staff', 'order')).toEqual({ kind: 'invalid' });
    expect(saveSaleAction('staff', 'order', 'confirm')).toBe(false);
    sessionStorage.setItem(key, '{bad');
    expect(readSaleAction('staff', 'order')).toEqual({ kind: 'invalid' });
  });
});
