import { beforeEach, describe, expect, it } from 'vitest';
import { clearSaleAction, readSaleAction, saveSaleAction } from './pending-sale-action';

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
