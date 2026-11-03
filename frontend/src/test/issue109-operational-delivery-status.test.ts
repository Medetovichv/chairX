import { describe, expect, it } from 'vitest';
import { operationalDeliveryLabel } from '../features/daily-closing/operational-delivery-label';

describe('Issue #109: paid regional delivery operational status', () => {
  it('keeps READY distinct from physical handover', () => {
    expect(operationalDeliveryLabel('READY', 'REGION_DELIVERY')).toBe('Готовится к отправке');
    expect(operationalDeliveryLabel('READY', 'CITY_DELIVERY')).toBe('Готовится к отправке');
  });
  it('reports transit, actual delivery and pickup precisely', () => {
    expect(operationalDeliveryLabel('IN_TRANSIT', 'REGION_DELIVERY')).toBe('В пути');
    expect(operationalDeliveryLabel('DELIVERED', 'REGION_DELIVERY')).toBe('Доставлено');
    expect(operationalDeliveryLabel(null, 'SELF_PICKUP')).toBe('Выдано');
    expect(operationalDeliveryLabel(null, 'REGION_DELIVERY')).toBe('Статус доставки не указан');
  });
  it('does not misrepresent a failed or cancelled delivery as handed over', () => {
    expect(operationalDeliveryLabel('FAILED', 'REGION_DELIVERY')).toBe('Доставка не выполнена');
    expect(operationalDeliveryLabel('CANCELLED', 'REGION_DELIVERY')).toBe('Доставка отменена');
  });
});
