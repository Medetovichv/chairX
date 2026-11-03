import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ReceivingWorkspaceOverview } from '../features/purchases/components/ReceivingWorkspaceOverview';
import type { ReceivingLine } from '../features/purchases/types';

const make = (id:string, ordered:number, received:number, remaining:number):ReceivingLine=>({
  purchaseId:'p', purchaseItemId:id, status:'PARTIALLY_RECEIVED',
  createdAt:'2026-10-10T08:00:00Z', productVariantId:id,
  model:'Модель',variant:'Чёрный',orderedQuantity:ordered,
  receivedQuantity:received,remainingQuantity:remaining,
});

describe('Studio receiving overview',()=>{
  it('shows the server page quantities only, without finance disclosure',()=>{
    render(<ReceivingWorkspaceOverview lines={[
      make('one',10,4,6),make('two',8,0,8),make('three',2,2,0)
    ]} total={44} page={1}/>);
    expect(screen.getByText('Страница 2 · 3 из 44')).toBeInTheDocument();
    expect(screen.getByText('14',{selector:'strong'})).toBeInTheDocument();
    expect(screen.getByText('6',{selector:'strong'})).toBeInTheDocument();
    expect(screen.getByText('2',{selector:'strong'})).toBeInTheDocument();
    expect(screen.getByText(/Себестоимость не отображается/)).toBeInTheDocument();
  });
  it('does not invent numbers for absent pages',()=>{
    render(<ReceivingWorkspaceOverview lines={[]} total={90} page={3}/>);
    expect(screen.getAllByText('0',{selector:'strong'})).toHaveLength(3);
    expect(screen.getByText('Страница 4 · 0 из 90')).toBeInTheDocument();
  });
});
