import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PurchasesWorkspaceOverview } from '../features/purchases/components/PurchasesWorkspaceOverview';
import type { PurchaseLine } from '../features/purchases/types';

const line = (id:string, status: PurchaseLine['status'], orderedQuantity:number,
  receivedQuantity:number, remainingQuantity:number):PurchaseLine=>({
  id, supplierId:'supplier', supplierName:'Поставщик',status,
  createdAt:'2026-10-10T10:00:00Z',itemCount:1,orderedQuantity,
  receivedQuantity,remainingQuantity,goodsCost:'1000.00',cargoCost:'100.00',
  totalCost:'1100.00',lastReceiptAt:null,
});

describe('Studio purchases overview',()=>{
  it('uses actual returned quantities and visible pending status only',()=>{
    render(<PurchasesWorkspaceOverview lines={[
      line('a','CONFIRMED',10,0,10),
      line('b','PARTIALLY_RECEIVED',6,2,4),
      line('c','RECEIVED',4,4,0),
    ]} total={65} page={1}/>);
    expect(screen.getByText('Страница 2 · 3 из 65')).toBeInTheDocument();
    expect(screen.getByText('Показатели только по записям на текущей странице с учётом фильтров.')).toBeInTheDocument();
    expect(screen.getByText('20',{selector:'strong'})).toBeInTheDocument();
    expect(screen.getByText('6',{selector:'strong'})).toBeInTheDocument();
    expect(screen.getByText('14',{selector:'strong'})).toBeInTheDocument();
    expect(screen.getByText('2',{selector:'strong'})).toBeInTheDocument();
  });
  it('does not infer totals from unreturned pages or from money values',()=>{
    render(<PurchasesWorkspaceOverview lines={[]} total={205} page={5}/>);
    expect(screen.getAllByText('0',{selector:'strong'})).toHaveLength(4);
    expect(screen.getByText('Страница 6 · 0 из 205')).toBeInTheDocument();
  });
});
