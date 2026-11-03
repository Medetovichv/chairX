import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ReturnsWorkspaceOverview } from '../features/returns/components/ReturnsWorkspaceOverview';
import type { ReturnSummary } from '../features/returns/types';

const item=(id:string,saleNumber:string):ReturnSummary=>({
  id,saleId:'22222222-2222-4222-8222-222222222222',
  saleNumber,warehouseId:'33333333-3333-4333-8333-333333333333',
  createdAt:'2026-10-10T10:00:00Z',
  reason:'Не подошло',createdBy:'manager',
});
describe('Studio Returns page-scoped overview',()=>{
  it('shows only visible return rows and explicitly avoids invented refund totals',()=>{
    render(<ReturnsWorkspaceOverview rows={[item('one','CHX-101'),item('two','')]}
      total={65} page={1} filtered/>);
    expect(screen.getByText('Поиск · Страница 2 · 2 из 65')).toBeInTheDocument();
    expect(screen.getByText('Суммы возмещений здесь не рассчитываются.',{exact:false})).toBeInTheDocument();
    expect(screen.getByText('С номером продажи')).toBeInTheDocument();
    expect(screen.getByText('1',{selector:'strong'})).toBeInTheDocument();
    expect(screen.getByText('2',{selector:'strong'})).toBeInTheDocument();
  });
  it('does not infer unseen return counts from response total',()=>{
    render(<ReturnsWorkspaceOverview rows={[]} total={42} page={2} filtered={false}/>);
    expect(screen.getAllByText('0',{selector:'strong'})).toHaveLength(2);
    expect(screen.getByText('Страница 3 · 0 из 42')).toBeInTheDocument();
  });
});
