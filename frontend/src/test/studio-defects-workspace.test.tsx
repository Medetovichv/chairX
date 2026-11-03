import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DefectsWorkspaceOverview } from '../features/returns/components/DefectsWorkspaceOverview';
import type { Defect } from '../features/returns/types';
const defect=(id:string,status:Defect['status']):Defect=>({
  id,warehouseId:'warehouse',productVariantId:'variant',supplierId:null,purchaseReceiptItemId:null,
  quantity:1,status,description:'Проверить механизм',resolutionNote:null,
  createdBy:'manager',createdAt:'2026-10-10T10:00:00Z',resolvedBy:null,resolvedAt:null,
});
describe('Studio defect status overview',()=>{
  it('scopes all counts to the current paginated page',()=>{
    render(<DefectsWorkspaceOverview rows={[
      defect('one','OPEN'),defect('two','WAITING_PARTS'),defect('three','RESOLVED')
    ]} page={2} total={77} status="" onStatus={vi.fn()}/>);
    expect(screen.getByText('Страница 3 · 3 из 77')).toBeInTheDocument();
    expect(screen.getByRole('button',{name:/Открыты: 1/})).toBeInTheDocument();
    expect(screen.getByRole('button',{name:/Ожидают запчасти: 1/})).toBeInTheDocument();
    expect(screen.getByRole('button',{name:/Исправлены: 1/})).toBeInTheDocument();
    expect(screen.getByRole('button',{name:/Списаны: 0/})).toBeInTheDocument();
  });
  it('applies existing server status filter and toggles back to all',async()=>{
    const onStatus=vi.fn();
    render(<DefectsWorkspaceOverview rows={[defect('one','OPEN')]} total={41} page={0}
      status="OPEN" onStatus={onStatus}/>);
    const user=userEvent.setup();
    expect(screen.getByRole('button',{name:/Открыты: 1/})).toHaveAttribute('aria-pressed','true');
    await user.click(screen.getByRole('button',{name:/Открыты: 1/}));
    await user.click(screen.getByRole('button',{name:/Списаны: 0/}));
    expect(onStatus.mock.calls).toEqual([[''],['WRITTEN_OFF']]);
  });
});
