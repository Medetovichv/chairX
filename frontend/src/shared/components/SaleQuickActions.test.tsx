import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SaleQuickActions } from './SaleQuickActions';
import type { SaleResponse } from '../../features/sales/types';

let permissions:string[]=[];
vi.mock('../../features/auth/AuthProvider',()=>({
  useAuth:()=>({hasPermission:(name:string)=>permissions.includes(name),user:{id:'staff'}}),
}));
const sale={
  id:'sale', saleNumber:'CHX-1', status:'DRAFT', fulfillmentType:'SELF_PICKUP',
  customerId:null,comment:null,total:1000,items:[{id:'item',productVariantId:'variant',warehouseId:'warehouse',quantity:1,unitSalePrice:1000,total:1000}],
} as SaleResponse;
function view() {
  const client=new QueryClient({defaultOptions:{queries:{retry:false}}});
  return render(<QueryClientProvider client={client}><SaleQuickActions sale={sale}/></QueryClientProvider>);
}
describe('SaleQuickActions RBAC',()=>{
  beforeEach(()=>{sessionStorage.clear();permissions=[];});
  it('does not expose operations without permissions',()=>{
    permissions=[];
    view();
    expect(screen.queryByRole('button',{name:'Подтвердить заказ'})).toBeNull();
    expect(screen.queryByRole('button',{name:'Отменить заказ'})).toBeNull();
  });
  it('permits draft confirmation only with draft management permission',()=>{
    permissions=['SALES_DRAFT_MANAGE'];
    view();
    expect(screen.getByRole('button',{name:'Подтвердить заказ'})).toBeTruthy();
    expect(screen.queryByRole('button',{name:'Отменить заказ'})).toBeNull();
  });
  it('blocks cancellation if an unresolved action exists after remount',()=>{
    permissions=['SALES_CANCEL'];
    sessionStorage.setItem('chairx:sale:quick-pending:v1:staff:sale',JSON.stringify('confirm'));
    view();
    expect(screen.getByRole('button',{name:'Отменить заказ'}).hasAttribute('disabled')).toBe(true);
    expect(screen.getByRole('button',{name:'Проверить состояние сервера'})).toBeTruthy();
  });
  it('fails closed on corrupted action journal',()=>{
    permissions=['SALES_DRAFT_MANAGE','SALES_CANCEL'];
    sessionStorage.setItem('chairx:sale:quick-pending:v1:staff:sale','{corrupt');
    view();
    expect(screen.getByRole('button',{name:'Подтвердить заказ'}).hasAttribute('disabled')).toBe(true);
    expect(screen.getByRole('button',{name:'Отменить заказ'}).hasAttribute('disabled')).toBe(true);
  });
  it('exposes cancel only to SALES_CANCEL',()=>{
    permissions=['SALES_CANCEL'];
    view();
    expect(screen.getByRole('button',{name:'Отменить заказ'})).toBeTruthy();
    expect(screen.queryByRole('button',{name:'Подтвердить заказ'})).toBeNull();
  });
});
