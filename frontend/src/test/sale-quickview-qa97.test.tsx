import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClientProvider } from '@tanstack/react-query';
import { createQueryClient } from '../shared/api/query';
import { SaleQuickActions } from '../shared/components/SaleQuickActions';
import { readSaleAction, saveSaleAction } from '../shared/components/pending-sale-action';
import { salesApi } from '../features/sales/api';
import type { SaleResponse } from '../features/sales/types';

vi.mock('../features/auth/AuthProvider',()=>({
  useAuth:()=>({
    user:{id:'qa97-user'},
    hasPermission:(p:string)=>['SALES_READ','SALES_DRAFT_MANAGE','SALES_UPDATE','SALES_CANCEL'].includes(p),
  }),
}));
vi.mock('../features/sales/api',()=>({
  salesApi:{get:vi.fn(),confirm:vi.fn(),fulfill:vi.fn(),cancel:vi.fn(),cancelWithReason:vi.fn()},
  salesKeys:{
    detail:(id:string)=>['sales','detail',id],
    lists:['sales','list'],
    kanban:['sales','kanban'],
    historyPrefix:(id:string)=>['sales','history',id],
  },
}));
const ID='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const USER='qa97-user';
const order=(status:SaleResponse['status']):SaleResponse=>({
  id:ID,saleNumber:'CHX-2026-0112',customerId:null,fulfillmentType:'SELF_PICKUP',
  status,items:[{id:'item',productVariantId:'variant',warehouseId:'warehouse',
    quantity:1,unitSalePrice:'8500.00',total:'8500.00'}],
  total:'8500.00',createdBy:'other-employee',createdAt:'2026-10-10T10:00:00Z',
  comment:null,fulfilledBy:null,fulfilledAt:null,cancelledBy:null,cancelledAt:null,
});
function mount(status:SaleResponse['status']) {
  return render(<QueryClientProvider client={createQueryClient()}>
    <SaleQuickActions sale={order(status)}/>
  </QueryClientProvider>);
}
beforeEach(()=>{sessionStorage.clear();vi.clearAllMocks();});
afterEach(()=>{sessionStorage.clear();vi.restoreAllMocks();});

describe('QA-02: uncertain sale Quick View actions fail closed',()=>{
  it('a matching server status cannot prove this user\'s lost POST succeeded',async()=>{
    expect(saveSaleAction(USER,ID,'confirm')).toBe(true);
    vi.mocked(salesApi.get).mockResolvedValue(order('CONFIRMED'));
    mount('DRAFT');
    await userEvent.setup().click(screen.getByRole('button',{name:'Проверить состояние сервера'}));
    expect(await screen.findByRole('alert')).toHaveTextContent(/исход именно вашего запроса не доказан/);
    expect(readSaleAction(USER,ID)).toEqual({kind:'pending',action:'confirm'});
    expect(screen.getByRole('button',{name:'Подтвердить заказ'})).toBeDisabled();
    expect(salesApi.confirm).not.toHaveBeenCalled();
    expect(salesApi.cancel).not.toHaveBeenCalled();
    expect(screen.queryByText('Результат подтверждён сервером.')).not.toBeInTheDocument();
  });
  it('another actor changing status must not unlock or resend the pending action',async()=>{
    expect(saveSaleAction(USER,ID,'fulfill')).toBe(true);
    vi.mocked(salesApi.get).mockResolvedValue(order('CANCELLED'));
    mount('CONFIRMED');
    await userEvent.setup().click(screen.getByRole('button',{name:'Проверить состояние сервера'}));
    expect(await screen.findByRole('alert')).toHaveTextContent(/остаётся неизвестным/);
    expect(readSaleAction(USER,ID)).toEqual({kind:'pending',action:'fulfill'});
    expect(screen.getByRole('button',{name:'Завершить выдачу'})).toBeDisabled();
    expect(salesApi.fulfill).not.toHaveBeenCalled();
  });
  it('a failed reconciliation GET preserves the durable pending record',async()=>{
    expect(saveSaleAction(USER,ID,'cancel')).toBe(true);
    vi.mocked(salesApi.get).mockRejectedValue(new TypeError('offline'));
    mount('DRAFT');
    await userEvent.setup().click(screen.getByRole('button',{name:'Проверить состояние сервера'}));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(readSaleAction(USER,ID)).toEqual({kind:'pending',action:'cancel'});
    expect(screen.getByRole('button',{name:'Отменить заказ'})).toBeDisabled();
    expect(salesApi.cancel).not.toHaveBeenCalled();
  });
  it('reasoned cancellation cannot post without selected reason and preserves optional comment',async()=>{
    vi.mocked(salesApi.get).mockResolvedValue(order('DRAFT'));
    vi.mocked(salesApi.cancelWithReason).mockResolvedValue(order('CANCELLED'));
    mount('DRAFT');
    const user=userEvent.setup();
    await user.click(screen.getByRole('button',{name:'Отменить заказ'}));
    expect(screen.getByRole('button',{name:'Продолжить отмену'})).toBeDisabled();
    expect(salesApi.cancelWithReason).not.toHaveBeenCalled();
    await user.selectOptions(screen.getByRole('combobox',{name:'Причина отмены'}),'PRODUCT_MISMATCH');
    await user.type(screen.getByRole('textbox',{name:'Комментарий отмены'}),'Покупатель ожидал другую модель');
    await user.click(screen.getByRole('button',{name:'Продолжить отмену'}));
    await user.click(screen.getByRole('button',{name:'Подтвердить'}));
    expect(salesApi.cancelWithReason).toHaveBeenCalledWith(ID,{
      reason:'PRODUCT_MISMATCH',comment:'Покупатель ожидал другую модель'
    });
    expect(salesApi.cancel).not.toHaveBeenCalled();
  });
  it('corrupted recovery storage fails closed before any POST',()=>{
    sessionStorage.setItem('chairx:sale:quick-pending:v1:'+USER+':'+ID, '{broken');
    mount('DRAFT');
    expect(screen.getByText(/Хранилище операции недоступно или повреждено/)).toBeInTheDocument();
    expect(screen.getByRole('button',{name:'Подтвердить заказ'})).toBeDisabled();
    expect(salesApi.confirm).not.toHaveBeenCalled();
  });
});
