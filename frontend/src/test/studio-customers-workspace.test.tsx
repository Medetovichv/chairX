import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { CustomerWorkspaceOverview } from '../features/customers/components/CustomerWorkspaceOverview';
import { CustomerTable } from '../features/customers/components/CustomerTable';
import { MemoryRouter } from 'react-router';
import type { Customer, CustomerOverviewLine } from '../features/customers/types';

const base: Customer = {
  id: '11111111-1111-4111-8111-111111111111',
  fullName: 'Айбек', phone: '+996700123456', secondaryPhone: null,
  whatsappPhone: null, instagramUsername: null, address: null, cityRegion: 'Бишкек',
  comment: null, active: true, createdAt: '2026-10-10T09:00:00Z', updatedAt: '2026-10-10T09:00:00Z',
};

describe('Studio customer directory', () => {
  it('never displays sales indicators without SALES_READ and never invents a total', () => {
    render(<CustomerWorkspaceOverview items={[base, {...base, id:'two', phone: null}]}
      total={null} page={0} limitedSearch withSales={false}/>);
    expect(screen.getByText('Поиск ограничен 20 совпадениями')).toBeInTheDocument();
    expect(screen.getByText('С контактами')).toBeInTheDocument();
    expect(screen.queryByText('С заказами')).not.toBeInTheDocument();
    expect(screen.queryByText(/всего \d+/)).not.toBeInTheDocument();
  });

  it('shows only page-scoped customer purchase presence, not sums of unseen purchases', () => {
    const rows: CustomerOverviewLine[] = [
      { id:'one', fullName:'Айбек', phone:'+9961', whatsappPhone:null, cityRegion:'Бишкек', orderCount:8, lastSaleNumber:'CHX-2026-0101', lastOrderAt:'2026-10-10T10:00:00Z' },
      { id:'two', fullName:'Данияр', phone:null, whatsappPhone:'+9962', cityRegion:'Ош', orderCount:0, lastSaleNumber:null, lastOrderAt:null },
    ];
    render(<CustomerWorkspaceOverview items={rows} total={82} page={2} limitedSearch={false} withSales/>);
    expect(screen.getByText('Страница 3 · всего 82')).toBeInTheDocument();
    expect(screen.getByText('С заказами')).toBeInTheDocument();
    expect(screen.getByText('Среди показанных клиентов')).toBeInTheDocument();
    expect(within(screen.getByRole('region', { name: 'Обзор клиентской базы' })).getAllByText('2', { selector: 'strong' })).toHaveLength(2);
    expect(screen.getByText('1', { selector: 'strong' })).toBeInTheDocument();
  });

  it('keeps customer detail navigation on responsive list records', () => {
    render(<MemoryRouter><CustomerTable items={[base]} withSales={false}/></MemoryRouter>);
    const links=screen.getAllByRole('link', { name: 'Айбек' });
    expect(links).toHaveLength(2); // desktop row + mobile card
    links.forEach(link => expect(link).toHaveAttribute('href','/customers/'+base.id));
  });
});
