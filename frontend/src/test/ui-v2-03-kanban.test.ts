import { describe, expect, it } from 'vitest';
import { allowedKanbanTransitions, transitionForTarget } from '../features/sales/kanbanTransitions';
import type { SaleSummary } from '../features/sales/types';

const sale: SaleSummary = {
  id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  saleNumber: 'CHX-2026-0042', createdAt: '2026-10-11T10:00:00Z',
  customerId: null, customerName: null, total: '8500', quantity: 1,
  products: 'ChairX Pro / Чёрный', paymentStatus: 'UNPAID',
  deliveryStatus: null, plannedDeliveryDate: null,
  status: 'DRAFT', fulfillmentType: 'SELF_PICKUP',
};
const allow = (...grants: string[]) => (permission: string) => grants.includes(permission);

describe('UI-V2-03 safe Kanban movement intents', () => {
  it('requests a server-confirmed draft transition, never mutates the DTO', () => {
    const actual = transitionForTarget(sale, 'CONFIRMED', allow('SALES_DRAFT_MANAGE'));
    expect(actual).toEqual({ target: 'CONFIRMED', action: 'confirm', label: 'Подтвердить заказ' });
    expect(sale.status).toBe('DRAFT');
    expect(transitionForTarget(sale, 'FULFILLED', allow('SALES_DRAFT_MANAGE', 'SALES_UPDATE'))).toBeNull();
    expect(transitionForTarget(sale, 'CANCELLED', allow('SALES_CANCEL'))?.action).toBe('cancel');
  });
  it('cannot confirm a draft without items, fulfillment or permission', () => {
    expect(allowedKanbanTransitions({ ...sale, quantity: 0 }, allow('SALES_DRAFT_MANAGE'))).toEqual([]);
    expect(allowedKanbanTransitions({ ...sale, fulfillmentType: null }, allow('SALES_DRAFT_MANAGE'))).toEqual([]);
    expect(allowedKanbanTransitions(sale, allow('SALES_READ'))).toEqual([]);
  });
  it('allows fulfillment only for confirmed pickup, never bypasses the delivery workflow', () => {
    expect(transitionForTarget({ ...sale, status: 'CONFIRMED' }, 'FULFILLED', allow('SALES_UPDATE'))?.action)
      .toBe('fulfill');
    for (const delivery of ['CITY_DELIVERY', 'REGION_DELIVERY'] as const) {
      expect(allowedKanbanTransitions({ ...sale, status: 'CONFIRMED', fulfillmentType: delivery },
        allow('SALES_UPDATE', 'SALES_CANCEL'))).toEqual([]);
    }
    expect(allowedKanbanTransitions({ ...sale, status: 'FULFILLED' }, allow('SALES_UPDATE'))).toEqual([]);
    expect(allowedKanbanTransitions({ ...sale, status: 'CANCELLED' }, allow('SALES_CANCEL'))).toEqual([]);
  });
});
