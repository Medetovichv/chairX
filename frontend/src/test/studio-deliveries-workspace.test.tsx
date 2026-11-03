import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DeliveryWorkspaceOverview } from '../features/deliveries/components/DeliveryWorkspaceOverview';
import type { DeliverySummary } from '../features/deliveries/types';

const base: DeliverySummary = {
  id: '11111111-1111-4111-8111-111111111111',
  saleId: '22222222-2222-4222-8222-222222222222',
  saleNumber: 'CHX-2026-0101',
  status: 'READY',
  recipientName: 'Айбек',
  recipientPhone: '+996700123456',
  cityRegion: 'Бишкек',
  address: 'Улица Тестовая 10',
  createdAt: '2026-10-10T10:30:00Z',
  plannedDeliveryDate: '2026-10-16',
  fulfillmentType: 'CITY_DELIVERY',
  deliveredAt: null,
};

describe('Studio Analytics delivery workspace', () => {
  it('uses only current-page records and labels page scope clearly', () => {
    render(<DeliveryWorkspaceOverview rows={[
      base, { ...base, id: 'two', status: 'IN_TRANSIT' },
      { ...base, id: 'three', status: 'DELIVERED' },
      { ...base, id: 'four', status: 'FAILED' },
    ]} page={1} total={47} onStatus={vi.fn()}/>);
    expect(screen.getByText('Страница 2 · 4 из 47')).toBeInTheDocument();
    expect(screen.getByText(/только для записей на текущей странице/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /К отправке: 1/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /В пути: 1/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Доставлены: 1/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Нужны действия: 1/ })).toBeInTheDocument();
  });

  it('opens status filters without changing or inventing delivery data', async () => {
    const onStatus = vi.fn();
    const user = userEvent.setup();
    render(<DeliveryWorkspaceOverview rows={[base]} page={0} total={120} onStatus={onStatus}/>);
    await user.click(screen.getByRole('button', { name: /В пути: 0/ }));
    await user.click(screen.getByRole('button', { name: /Нужны действия: 0/ }));
    expect(onStatus.mock.calls).toEqual([['IN_TRANSIT'], ['FAILED']]);
    expect(base.status).toBe('READY');
  });
});
