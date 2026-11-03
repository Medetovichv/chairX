import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { InventoryWorkspaceOverview } from '../features/inventory/components/InventoryWorkspaceOverview';
import type { StockLine } from '../features/inventory/types';

const stock = (id: string, available: number, reserved: number): StockLine => ({
  productVariantId: id, model: 'Ergo', variation: id, onHand: available + reserved,
  available, reserved, blocked: 0, warehouses: [],
});

describe('Studio inventory page-scoped summary', () => {
  it('sums only the fetched variation lines and discloses pagination scope', () => {
    render(<InventoryWorkspaceOverview
      lines={[stock('one', 5, 2), stock('two', 0, 3), stock('three', 12, 0)]}
      total={98} page={2}/>);
    expect(screen.getByText('Страница 3 · 3 из 98')).toBeInTheDocument();
    expect(screen.getByText('17')).toBeInTheDocument();
    expect(screen.getByText('5')).toBeInTheDocument();
    expect(screen.getByText('1')).toBeInTheDocument();
    expect(screen.getByText(/только по вариациям на текущей странице/)).toBeInTheDocument();
  });

  it('represents an empty page with real zero values rather than made-up totals', () => {
    render(<InventoryWorkspaceOverview lines={[]} total={0} page={0}/>);
    expect(screen.getAllByText('0')).toHaveLength(3);
    expect(screen.getByText('Страница 1 · 0 из 0')).toBeInTheDocument();
  });
});
