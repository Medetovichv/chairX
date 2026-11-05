import { describe, expect, it } from 'vitest';
import { financeMovementLabel } from './ledger-labels';

describe('finance movement labels shared by overview and operations', () => {
  it.each([
    ['TRANSFER','Перевод между счетами'],
    ['OPENING_BALANCE','Начальный остаток'],
    ['SALE_PAYMENT','Оплата продажи'],
    ['CUSTOMER_REFUND','Возврат клиенту'],
    ['PAYMENT_REVERSAL','Отмена платежа'],
    ['EXPENSE','Расход'],
  ])('renders %s in Russian', (code,label) => {
    expect(financeMovementLabel(code)).toBe(label);
  });
  it('keeps an unknown backend movement readable without inventing its meaning', () => {
    expect(financeMovementLabel('FUTURE_CUSTOM_ENTRY')).toBe('FUTURE CUSTOM ENTRY');
  });
});
