import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { displaySaleNumber } from '../shared/lib/format';
import { SalesTable } from '../features/sales/components/SalesTable';
import { CustomerTable } from '../features/customers/components/CustomerTable';
import { DeliveriesTable } from '../features/deliveries/components/DeliveriesTable';
import type { SaleSummary } from '../features/sales/types';
import type { Customer } from '../features/customers/types';
import type { DeliverySummary } from '../features/deliveries/types';

const SALE_ID = 'd6283d68-ea4e-4bbd-88bf-2e9e10c03681';
const CUSTOMER_ID = '315802dd-d333-4b8e-a895-819bb70bd2f6';
const DELIVERY_ID = '351853d3-4ea6-464d-a56f-dd1f4d80a70d';

describe('UI-V2-01 semantically navigable entities', () => {
  it('shows a short human sale number without changing the server identifier', () => {
    expect(displaySaleNumber('SALE-2026-000123')).toBe('#123');
    expect(displaySaleNumber('SALE-2025-000123')).toBe('#123');
    expect(displaySaleNumber('CHX-2026-0042')).toBe('CHX-2026-0042');
    expect(displaySaleNumber(SALE_ID)).toBe(SALE_ID); // legacy invalid input never gets cut into a fake number
  });

  it('shows sale links on desktop/mobile rows, with real href but no customer UUID', () => {
    const sale: SaleSummary = {
      id: SALE_ID, saleNumber: 'SALE-2026-000123',
      createdAt: '2026-10-10T10:00:00Z', customerId: CUSTOMER_ID,
      customerName: null, total: '8500', status: 'CONFIRMED',
      fulfillmentType: 'SELF_PICKUP', quantity: 1, products: 'ChairX Pro',
      paymentStatus: 'UNPAID', deliveryStatus: null, plannedDeliveryDate: null,
    };
    render(<MemoryRouter><SalesTable rows={[sale]} /></MemoryRouter>);
    const rows = screen.getAllByRole('link', { name: 'Открыть продажу SALE-2026-000123' });
    expect(rows).toHaveLength(2);
    rows.forEach(link => {
      expect(link).toHaveAttribute('href', '/sales/' + SALE_ID);
      expect(link).toHaveClass('entity-stretched-link');
      expect(link).toHaveTextContent('#123');
    });
    const table = screen.getByRole('table');
    expect(within(table).queryByRole('columnheader', {name: 'Действие'})).not.toBeInTheDocument();
    expect(screen.getAllByText(/Без клиента/)).toHaveLength(2);
    expect(screen.queryByText(CUSTOMER_ID)).not.toBeInTheDocument();
  });

  it('keeps customer and delivery cards as native keyboard-accessible anchors', () => {
    const customer: Customer = {
      id: CUSTOMER_ID, fullName: 'Тестовый клиент', phone: '+996555000000',
      whatsappPhone: null, secondaryPhone: null, instagramUsername: null,
      address: null, cityRegion: null, comment: null,
      active: true, createdAt: '2026-10-10T10:00:00Z', updatedAt: '2026-10-10T10:00:00Z',
    };
    const delivery: DeliverySummary = {
      id: DELIVERY_ID, saleId: SALE_ID, status: 'READY',
      recipientName: 'Получатель', cityRegion: 'Бишкек',
      createdAt: '2026-10-10T10:00:00Z',
      plannedDeliveryDate: '2026-10-12',
      saleNumber: 'SALE-2026-000123',
      recipientPhone: '+996555000000', address: 'Бишкек, улица тестовая',
      fulfillmentType: 'CITY_DELIVERY', deliveredAt: null,
    };
    render(<MemoryRouter><CustomerTable items={[customer]} withSales={false} /><DeliveriesTable rows={[delivery]} /></MemoryRouter>);
    const customerLinks = screen.getAllByRole('link', { name: 'Тестовый клиент' });
    expect(customerLinks).toHaveLength(2);
    customerLinks.forEach(link => expect(link).toHaveAttribute('href', '/customers/' + CUSTOMER_ID));
    const deliveryLinks = screen.getAllByRole('link', { name: 'Открыть доставку SALE-2026-000123' });
    expect(deliveryLinks).toHaveLength(2);
    deliveryLinks.forEach(link => expect(link).toHaveAttribute('href', '/deliveries/' + DELIVERY_ID));
    expect(screen.queryByText(SALE_ID)).not.toBeInTheDocument();
  });
});
