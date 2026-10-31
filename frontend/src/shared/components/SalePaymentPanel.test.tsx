import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SalePaymentPanel } from './SalePaymentPanel';
import type { SaleResponse } from '../../features/sales/types';

const api=vi.hoisted(()=>({get:vi.fn(),post:vi.fn()}));
vi.mock('../api/client',()=>({apiClient:api}));
vi.mock('../../features/auth/AuthProvider',()=>({
  useAuth:()=>({hasPermission:(permission:string)=>permission==='PAYMENTS_READ'||permission==='PAYMENTS_CREATE'}),
}));
const sale={id:'11111111-1111-4111-8111-111111111111',status:'CONFIRMED',
  fulfillmentType:'SELF_PICKUP',total:'8500.00'} as SaleResponse;
function view() {
  const queryClient=new QueryClient({defaultOptions:{queries:{retry:false}}});
  return render(<QueryClientProvider client={queryClient}><SalePaymentPanel sale={sale}/></QueryClientProvider>);
}
describe('SalePaymentPanel duplicate-payment protection',()=>{
  beforeEach(()=>{api.get.mockReset();api.post.mockReset();});
  it('shows an existing payment without exposing a duplicate registration',async()=>{
    api.get.mockResolvedValue([{id:'payment',amount:'8500.00',method:'CASH',status:'PAID'}]);
    view();
    expect(await screen.findByText(/Оплачено:/)).toBeTruthy();
    expect(screen.queryByRole('button',{name:'Подтвердить получение оплаты'})).toBeNull();
    expect(screen.queryByRole('combobox',{name:'Способ оплаты'})).toBeNull();
    expect(screen.queryByRole('textbox',{name:/референс|комментарий/i})).toBeNull();
    expect(api.post).not.toHaveBeenCalled();
  });
  it('registers once and disables the action after server success',async()=>{
    api.get.mockResolvedValue([]);
    api.post.mockResolvedValue({id:'payment'});
    view();
    const button=await screen.findByRole('button',{name:'Подтвердить получение оплаты'});
    await userEvent.setup().click(button);
    await waitFor(()=>expect(api.post).toHaveBeenCalledTimes(1));
    await waitFor(()=>expect(button.hasAttribute('disabled')).toBe(true));
    await userEvent.setup().click(button);
    expect(api.post).toHaveBeenCalledTimes(1);
  });
  it('does not retry a payment automatically after the response is lost',async()=>{
    api.get.mockResolvedValue([]);
    api.post.mockRejectedValue(new TypeError('Connection lost after commit'));
    view();
    const button=await screen.findByRole('button',{name:'Подтвердить получение оплаты'});
    await userEvent.setup().click(button);
    expect(await screen.findByRole('button',{name:'Проверить оплату'})).toBeTruthy();
    expect(button.hasAttribute('disabled')).toBe(true);
    expect(api.post).toHaveBeenCalledTimes(1);
  });
});
