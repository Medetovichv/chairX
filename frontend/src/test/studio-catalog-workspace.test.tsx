import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { CatalogWorkspaceOverview } from '../features/catalog/components/CatalogWorkspaceOverview';
import type { Product, ProductPage } from '../features/catalog/types';

const model = (id: string, active: boolean): Product => ({
  id, name: 'Кресло ' + id, description: null, category: 'Офисные', active,
  createdAt: '2026-10-10T08:00:00Z', updatedAt: '2026-10-10T08:00:00Z',
});
const page = (items: Product[], totalElements: number, index = 0): ProductPage<Product> =>
  ({ items, page: index, size: 20, totalElements, totalPages: Math.ceil(totalElements / 20) });

describe('Studio catalog overview', () => {
  it('never counts unseen products or claims that the catalog API returns stock', () => {
    render(<CatalogWorkspaceOverview page={page([
      model('one', true), model('two', false), model('three', true),
    ], 67, 1)}/>);
    expect(screen.getByText('Страница 2 · всего 67')).toBeInTheDocument();
    expect(screen.getByText('Статусы только показанных моделей. Наличие товара проверяйте в разделе «Склады».')).toBeInTheDocument();
    expect(screen.getByText('3', {selector: 'strong'})).toBeInTheDocument();
    expect(screen.getByText('2', {selector: 'strong'})).toBeInTheDocument();
    expect(screen.getByText('1', {selector: 'strong'})).toBeInTheDocument();
  });
  it('renders zero-state counts without fabricated stock or variant metrics', () => {
    render(<CatalogWorkspaceOverview page={page([],0)}/>);
    expect(screen.getAllByText('0', {selector: 'strong'})).toHaveLength(3);
    expect(screen.queryByText(/остатков на всех складах/i)).not.toBeInTheDocument();
  });
});
