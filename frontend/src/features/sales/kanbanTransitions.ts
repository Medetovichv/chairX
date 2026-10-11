import type { SaleStatus, SaleSummary } from './types';

/**
 * The board never posts a status mutation. A drop only requests a visit to
 * SaleDetail's EXISTING fresh-read and explicit-confirmation workflow.
 *
 * Transitions intentionally mirror SaleDetailPage's buttons. Delivery sales
 * are completed/cancelled through DELIVERY workflows, not direct sale writes.
 */
export type KanbanAction = 'confirm' | 'fulfill' | 'cancel';

export interface KanbanTransition {
  target: SaleStatus;
  action: KanbanAction;
  label: string;
}

export function allowedKanbanTransitions(
  sale: SaleSummary,
  hasPermission: (permission: string) => boolean,
): KanbanTransition[] {
  if (sale.status === 'DRAFT') {
    return [
      ...(hasPermission('SALES_DRAFT_MANAGE') && sale.fulfillmentType !== null && sale.quantity > 0
        ? [{ target: 'CONFIRMED' as const, action: 'confirm' as const, label: 'Подтвердить заказ' }] : []),
      ...(hasPermission('SALES_CANCEL')
        ? [{ target: 'CANCELLED' as const, action: 'cancel' as const, label: 'Отменить черновик' }] : []),
    ];
  }
  if (sale.status === 'CONFIRMED' && sale.fulfillmentType === 'SELF_PICKUP') {
    return [
      ...(hasPermission('SALES_UPDATE')
        ? [{ target: 'FULFILLED' as const, action: 'fulfill' as const, label: 'Завершить самовывоз' }] : []),
      ...(hasPermission('SALES_CANCEL')
        ? [{ target: 'CANCELLED' as const, action: 'cancel' as const, label: 'Отменить заказ' }] : []),
    ];
  }
  return [];
}

export function transitionForTarget(
  sale: SaleSummary, target: SaleStatus,
  hasPermission: (permission: string) => boolean,
): KanbanTransition | null {
  return allowedKanbanTransitions(sale, hasPermission).find(t => t.target === target) ?? null;
}
