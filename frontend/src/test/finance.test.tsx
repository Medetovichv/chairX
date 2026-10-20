import { afterEach,beforeEach,describe,expect,it,vi } from 'vitest';
import type { ReactNode } from 'react';
import { fireEvent,render,screen,waitFor,within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useNavigate } from 'react-router';
import { AppRouter } from '../app/router/AppRouter';
import { QueryClientProvider } from '@tanstack/react-query';
import { createQueryClient } from '../shared/api/query';
import { authSession } from '../features/auth/session';
import { ApiClientError } from '../shared/api/api-error';
import { GuardedPost } from '../features/finance/components/GuardedPost';
import { SaleFinancialSection } from '../features/finance/components/SaleFinancialSection';
import { ExpensesPage } from '../features/finance/pages/ExpensesPage';
import { FinanceOverviewPage } from '../features/finance/pages/FinanceOverviewPage';
import { FinanceReportPage } from '../features/finance/pages/FinanceReportPage';
import { ExchangeSettlementForm } from '../features/finance/components/ExchangeSettlementForm';
import { readFinancePending } from '../features/finance/pending';
import {
 accountFor,methodFor,amountCents,som,positiveSom,nonnegativeSom,validateRefund,
 validateSettlement,validateExpense,validateTransfer,validateOpening,periodMonth,validPeriod,
} from '../features/finance/validation';
import type { SaleResponse } from '../features/sales/types';
import type { ExchangeResponse } from '../features/returns/types';

const access=vi.hoisted(()=>({
 permissions:[] as string[],roles:['CUSTOM_FINANCE'] as string[],
}));
vi.mock('../features/auth/AuthProvider',()=>({
 useAuth:()=>({
  status:'authenticated',isAuthenticated:true,
  user:{id:'f10-operator',username:'f10',displayName:'Финансист',
   roles:access.roles,permissions:access.permissions},
  hasPermission:(p:string)=>access.permissions.includes(p),
  hasAnyPermission:(list:readonly string[])=>list.some(p=>access.permissions.includes(p)),
  login:vi.fn(),logout:vi.fn(),
 }),
}));
const SALE='11111111-1111-4111-8111-111111111111';
const RETURN='22222222-2222-4222-8222-222222222222';
const EXCHANGE='33333333-3333-4333-8333-333333333333';
const PAYMENT='44444444-4444-4444-8444-444444444444';
const validID='55555555-5555-4555-8555-555555555555';
const NOW='2026-10-11T10:00:00Z';
const sale:SaleResponse={
 id:SALE,saleNumber:'CX-125',customerId:null,fulfillmentType:'SELF_PICKUP',
 status:'CONFIRMED',items:[],total:'8500',createdBy:'f10',createdAt:NOW,
 fulfilledBy:null,fulfilledAt:null,cancelledBy:null,cancelledAt:null,comment:null,
};
const exchange:ExchangeResponse={
 id:EXCHANGE,originalSaleId:SALE,newSaleId:validID,returnId:RETURN,
 status:'PENDING_SETTLEMENT',returnedValue:'8500',newSaleTotal:'9500',
 creditApplied:'8500',additionalPaymentDue:'1000',refundDue:'0',
};
const j=(data:unknown,status=200)=>new Response(JSON.stringify(data),{status,headers:{'Content-Type':'application/json'}});
function renderPath(path:string){
 return render(<QueryClientProvider client={createQueryClient()}>
  <MemoryRouter initialEntries={[path]}><AppRouter/></MemoryRouter>
 </QueryClientProvider>);
}
function wrapper(children:ReactNode){
 return render(<QueryClientProvider client={createQueryClient()}>
  <MemoryRouter initialEntries={['/sales/'+SALE]}>{children}</MemoryRouter>
 </QueryClientProvider>);
}
beforeEach(()=>{
 sessionStorage.clear();
 access.permissions=[];
 authSession.activate('Basic f10-test',{headerName:'X-CSRF-TOKEN',token:'csrf'});
});
afterEach(()=>{
 authSession.clear();sessionStorage.clear();vi.restoreAllMocks();vi.unstubAllGlobals();
});
describe('Finance reporting RBAC and unavailable profit',()=>{
 it('does not render profitability without FINANCE_READ',()=>{
  access.permissions=[];
  wrapper(<FinanceReportPage/>);
  expect(screen.getByText('Доступ запрещён')).toBeInTheDocument();
 });
 it('shows unavailable profit instead of cash-based fake profit',async()=>{
  access.permissions=['FINANCE_READ'];
  vi.stubGlobal('fetch',vi.fn(async(url:RequestInfo|URL)=>{
   const path=String(url);
   if(path.includes('/api/finance/profitability?'))return j({
     from:'2026-10-01',to:'2026-11-01',grossRevenue:'10000',
     verifiedFifoCost:'0',recognizedAccruals:'500',otherOperatingExpenses:'0',
     returnedRevenue:'0',refundOnlyDiscounts:'0',restoredFifoCost:'0',
     netRevenue:'10000',completeCost:false,netProfitAvailable:false,
     grossProfit:null,operatingProfit:null,sales:[],caveat:'Не подтверждён FIFO'
   });
   if(path.includes('/api/finance/cash-flow?'))return j({
    totalIn:'10000',totalOut:'4000',netCashFlow:'6000',
    payments:'10000',paymentCorrections:'0',refunds:'0',exchangePayments:'0',
    exchangeRefunds:'0',operatingExpenses:'1000',purchasePayments:'3000'
   });
   if(path.includes('/api/finance/ledger?'))return j({items:[],total:0,page:0,size:50});
   if(path.includes('/api/finance/model-margins?'))return j([]);
   if(path.endsWith('/api/finance/inventory-valuation'))return j({totalCost:'0',quantity:0,models:[]});
   if(path.endsWith('/api/finance/purchase-obligations'))return j({totalOutstanding:'0',totalPrepaid:'0',purchases:[]});
   if(path.includes('/api/finance/analytics?'))return j({months:[],from:'2026-01-01',to:'2027-01-01'});
   throw Error('Unexpected '+path);
  }));
  wrapper(<FinanceReportPage/>);
  expect(await screen.findByRole('alert')).toHaveTextContent('Не подтверждён FIFO');
  expect(screen.getByText('Чистая выручка')).toBeInTheDocument();
  expect(screen.getByRole('table',{name:'Журнал всех операций'})).toBeInTheDocument();
  expect(screen.getAllByText('Недоступно').length).toBeGreaterThan(0);
 });
});
describe('F10 exact money validation, mappings and dates',()=>{
 it('maps all payment channels to backend methods and CASH/BANK accounts',()=>{
  expect([accountFor('CASH'),methodFor('CASH')]).toEqual(['CASH','CASH']);
  for(const channel of ['BANK_TRANSFER','MBANK','BANK_INSTALLMENT'] as const){
   expect([accountFor(channel),methodFor(channel)]).toEqual(['BANK','TRANSFER']);
  }
 });
 it('preserves BigDecimal input precision without unsafe JS floats',()=>{
  expect(amountCents('9007199254740999.50')).toBe(900719925474099950n);
  expect(amountCents(Number.MAX_SAFE_INTEGER)).toBeNull();
  expect(som('9007199254740999.50')).toContain('50');
  expect(amountCents('8500.17')).toBe(850017n);
  expect(amountCents('-42')).toBe(-4200n);
 });
 it('rejects negative, overflow, decimal and exponent money writes',()=>{
  for(const amount of ['0','-1','1.50','1e3','999999999999999999','Infinity','NaN']){
   expect(positiveSom(amount)).toBe(false);
  }
  expect(positiveSom('8500')).toBe(true);
  expect(nonnegativeSom('0')).toBe(true);
  expect(nonnegativeSom('-0')).toBe(false);
 });
 it('validates Refund link/method/whole-som requirement',()=>{
  const p={idempotencyKey:validID,saleId:SALE,returnId:RETURN,amount:'8500',
   method:'TRANSFER' as const,reason:'Возврат товара',reference:null,comment:null};
  expect(validateRefund(p)).toBeNull();
  expect(validateRefund({...p,returnId:'other'})).toMatch(/ID/);
  expect(validateRefund({...p,amount:'8500.01'})).toMatch(/целым/);
  expect(validateRefund({...p,reason:''})).toMatch(/Причина/);
 });
 it('validates ExchangeSettlement and transfer account boundaries',()=>{
  const p={idempotencyKey:validID,direction:'OUT' as const,method:'TRANSFER' as const,amount:'1000',reference:null};
  expect(validateSettlement(p)).toBeNull();
  expect(validateSettlement({...p,amount:'0'})).toMatch(/положительными/);
  expect(validateTransfer({transferId:validID,from:'CASH',to:'BANK',amount:'1000'})).toBeNull();
  expect(validateTransfer({transferId:validID,from:'CASH',to:'CASH',amount:'1000'})).toMatch(/разных/);
 });
 it('supports zero one-time initialization but no negative opening balance',()=>{
  expect(validateOpening({account:'CASH',amount:'0'})).toBeNull();
  expect(validateOpening({account:'BANK',amount:'-100'})).toMatch(/неотрицательные/);
  expect(validateExpense({idempotencyKey:validID,category:'RENT',amount:'1000',
   paymentMethod:'BANK',expenseDate:'2026-10-11',comment:null})).toBeNull();
  expect(validateExpense({idempotencyKey:validID,category:'RENT',amount:'1000',
   paymentMethod:'BANK',expenseDate:'2026-02-30',comment:null})).toMatch(/бизнес-дата/);
 });
 it('uses Bishkek month and [from,to) exclusive period not workstation locale',()=>{
  expect(periodMonth(new Date('2026-10-10T22:30:00Z'))).toEqual({from:'2026-10-01',to:'2026-11-01'});
  expect(validPeriod('2026-10-01','2026-11-01')).toBe(true);
  expect(validPeriod('2026-11-01','2026-11-01')).toBe(false);
  expect(validPeriod('2026-01-01','2026-12-31')).toBe(false);
 });
});
type TestPayload={idempotencyKey:string;amount:string};
function renderGuard(options:{
 post:(p:TestPayload)=>Promise<unknown>;canReplay?:boolean;onDone?:()=>Promise<void>;
 reconcile?:(p:{key:string})=>Promise<boolean>;kind?:string
}){
 return wrapper(<GuardedPost<TestPayload> allowed kind={options.kind??'test-money'} scope="test-scope"
  title="Подтвердить денежную операцию?" label="Подтвердить"
  operationKey={p=>p.idempotencyKey} meta={p=>({amount:p.amount})}
  post={options.post} canReplay={options.canReplay} onDone={options.onDone??(()=>{})}
  reconcile={options.reconcile}>
  {({start,locked})=><button type="button" disabled={locked}
   onClick={()=>start({idempotencyKey:validID,amount:'8500'},'Сумма: 8500 сом')}>
   Начать</button>}
 </GuardedPost>);
}
async function confirm(){
 await userEvent.setup().click(screen.getByRole('button',{name:'Начать'}));
 await userEvent.setup().click(within(screen.getByRole('alertdialog'))
  .getByRole('button',{name:'Подтвердить'}));
}
describe('F10 financial transaction journal and recovery',()=>{
 it('never POSTs if sessionStorage cannot persist operation marker',async()=>{
  const post=vi.fn(async()=>({}));
  vi.spyOn(Storage.prototype,'setItem').mockImplementation(()=>{throw Error('Storage blocked');});
  renderGuard({post,kind:'broken-storage'});
  await confirm();
  expect(await screen.findByText(/Не удалось сохранить исходную операцию/)).toBeInTheDocument();
  expect(post).not.toHaveBeenCalled();
  expect(screen.getByRole('button',{name:'Начать'})).toBeDisabled();
 });
 it('blocks non-idempotent money POST after network uncertainty and reload',async()=>{
  const post=vi.fn().mockRejectedValue(new ApiClientError('network','CONNECTION_LOST'));
  const first=renderGuard({post,kind:'no-idempotency'});
  await confirm();
  expect(await screen.findByText(/Результат финансовой операции неизвестен/)).toBeInTheDocument();
  expect(post).toHaveBeenCalledTimes(1);
  expect(screen.queryByRole('button',{name:'Повторить исходный запрос с прежним ключом'})).not.toBeInTheDocument();
  first.unmount();
  renderGuard({post,kind:'no-idempotency'});
  expect(screen.getByRole('button',{name:'Начать'})).toBeDisabled();
  expect(post).toHaveBeenCalledTimes(1);
 });
 it('isolates a pending money journal when the cached document changes without unmounting the route',async()=>{
  const post=vi.fn().mockRejectedValue(new ApiClientError('network','CONNECTION_LOST'));
  const client=createQueryClient();
  const content=(scope:string)=><QueryClientProvider client={client}>
   <MemoryRouter><GuardedPost<TestPayload> kind="route-switch" scope={scope} allowed
    title="Подтвердить денежную операцию?" label="Подтвердить"
    operationKey={p=>p.idempotencyKey} meta={p=>({amount:p.amount})}
    post={post} onDone={()=>{}}>
    {({start,locked})=><button type="button" disabled={locked}
     onClick={()=>start({idempotencyKey:validID,amount:'8500'},'Сумма 8500')}>Начать</button>}
   </GuardedPost></MemoryRouter></QueryClientProvider>;
  const view=render(content(SALE));
  await confirm();
  expect(await screen.findByText(/Результат финансовой операции неизвестен/)).toBeInTheDocument();
  expect(readFinancePending('f10-operator','route-switch',SALE).kind).toBe('pending');
  view.rerender(content(RETURN));
  expect(screen.getByRole('button',{name:'Начать'})).toBeEnabled();
  expect(readFinancePending('f10-operator','route-switch',SALE).kind).toBe('pending');
  view.rerender(content(SALE));
  expect(screen.getByRole('button',{name:'Начать'})).toBeDisabled();
  expect(post).toHaveBeenCalledTimes(1);
 });
 it('retries backend-idempotent POST with same exact payload/key, never a new UUID',async()=>{
  const post=vi.fn().mockRejectedValueOnce(new ApiClientError('network','LOST')).mockResolvedValueOnce({});
  renderGuard({post,canReplay:true,kind:'idempotent'});
  await confirm();
  expect(await screen.findByText(/Результат финансовой операции неизвестен/)).toBeInTheDocument();
  await userEvent.setup().click(screen.getByRole('button',{name:'Повторить исходный запрос с прежним ключом'}));
  await waitFor(()=>expect(post).toHaveBeenCalledTimes(2));
  expect(post.mock.calls[1]?.[0]).toEqual(post.mock.calls[0]?.[0]);
  expect(readFinancePending('f10-operator','idempotent','test-scope').kind).toBe('missing');
 });
 it('keeps confirmed posting final even when cache refresh fails',async()=>{
  const post=vi.fn(async()=>({}));
  renderGuard({post,kind:'refresh-failure',onDone:async()=>{throw Error('Query refetch failed');}});
  await confirm();
  expect(await screen.findByText(/обновить данные не удалось/)).toBeInTheDocument();
  expect(readFinancePending('f10-operator','refresh-failure','test-scope').kind).toBe('missing');
  expect(screen.queryByRole('button',{name:'Повторить исходный запрос с прежним ключом'})).not.toBeInTheDocument();
  expect(post).toHaveBeenCalledTimes(1);
 });
 it('does not mark an exactly reconciled financial posting unknown when only refresh fails',async()=>{
  const post=vi.fn().mockRejectedValue(new ApiClientError('network','LOST'));
  renderGuard({post,kind:'reconciled-refresh-error',reconcile:async p=>p.key===validID,
   onDone:async()=>{throw Error('React Query refresh failed');}});
  await confirm();
  expect(await screen.findByText(/Результат финансовой операции неизвестен/)).toBeInTheDocument();
  await userEvent.setup().click(screen.getByRole('button',{name:'Сверить с backend'}));
  expect(await screen.findByText(/Операция подтверждена backend, но обновить данные не удалось/)).toBeInTheDocument();
  expect(screen.queryByText(/Результат всё ещё неизвестен/)).not.toBeInTheDocument();
  expect(readFinancePending('f10-operator','reconciled-refresh-error','test-scope').kind).toBe('missing');
  expect(post).toHaveBeenCalledTimes(1);
 });
 it('confirms a previously ambiguous operation via exact backend lookup without POST replay',async()=>{
  const post=vi.fn().mockRejectedValue(new ApiClientError('network','LOST'));
  const first=renderGuard({post,kind:'reconcile',reconcile:async()=>true});
  await confirm();
  expect(await screen.findByText(/Результат финансовой операции неизвестен/)).toBeInTheDocument();
  first.unmount();
  renderGuard({post,kind:'reconcile',reconcile:async p=>p.key===validID});
  await userEvent.setup().click(screen.getByRole('button',{name:'Сверить с backend'}));
  expect(await screen.findByText(/Операция подтверждена сервером/)).toBeInTheDocument();
  expect(post).toHaveBeenCalledTimes(1);
  expect(readFinancePending('f10-operator','reconcile','test-scope').kind).toBe('missing');
 });
});
describe('F10 permissions and real backend HTTP contracts',()=>{
 it('never requests FINANCE_READ endpoints for non-finance role',()=>{
  const fetchMock=vi.fn();vi.stubGlobal('fetch',fetchMock);
  wrapper(<FinanceOverviewPage/>);
  expect(screen.getByRole('heading',{name:'Доступ запрещён'})).toBeInTheDocument();
  expect(fetchMock).not.toHaveBeenCalled();
 });
 it('allows EXPENSES_CREATE without EXPENSES_READ or FINANCE_READ and does not fetch private lists',async()=>{
  access.permissions=['EXPENSES_CREATE'];
  const fetchMock=vi.fn();vi.stubGlobal('fetch',fetchMock);
  wrapper(<ExpensesPage/>);
  expect(screen.getByRole('form',{name:'Создание расхода'})).toBeInTheDocument();
  expect(fetchMock).not.toHaveBeenCalled();
 });
 it('GET /api/expenses is a plain array and expenses total is separate GET',async()=>{
  access.permissions=['EXPENSES_READ'];
  const urls:string[]=[];
  vi.stubGlobal('fetch',vi.fn(async (url:RequestInfo|URL)=>{
   urls.push(String(url));
   if(String(url).includes('/api/expenses/accruals?'))return j([]);
   return String(url).includes('/total?')?j('2500'):j([{
    id:validID,category:'RENT',amount:'2500',paymentMethod:'BANK',expenseDate:'2026-10-11',
    comment:'Офис',createdBy:'f10',createdAt:NOW,
   }]);
  }));
  wrapper(<ExpensesPage/>);
  expect(await screen.findByText('Аренда')).toBeInTheDocument();
  expect((await screen.findAllByText(/2\s?500/)).length).toBeGreaterThan(0);
  expect(urls.some(x=>x.includes('/api/expenses?from='))).toBe(true);
  expect(urls.some(x=>x.includes('/api/expenses/total?from='))).toBe(true);
 });
 it('client payment offers only cash and transfer and posts the full total without amount' ,async()=>{
  access.permissions=['PAYMENTS_READ','PAYMENTS_CREATE'];
  const recorded:unknown[]=[];
  let paid:unknown[]=[];
  vi.stubGlobal('fetch',vi.fn(async (url:RequestInfo|URL,init?:RequestInit)=>{
   const path=String(url);
   if(path.includes('/api/payments/sale/'+SALE+'/active'))return j({
    code:'PAYMENT_NOT_FOUND',message:'',details:{},
   },paid.length?200:404);
   if(path.includes('/api/payments/sale/'+SALE))return j(paid);
   if(path==='/api/payments'&&init?.method==='POST'){
    const body=JSON.parse(String(init.body));recorded.push(body);
    paid=[{id:PAYMENT,saleId:SALE,amount:'8500',status:'PAID',
      method:body.method,channel:body.channel,reference:null,comment:null,
      paidBy:'f10',paidAt:NOW,cancelledBy:null,cancelledAt:null,cancellationReason:null}];
    return j(paid[0],201);
   }
   throw new Error('Unexpected '+path);
  }));
  wrapper(<SaleFinancialSection sale={sale}/>);
  const form=await screen.findByRole('form',{name:'Регистрация оплаты'});
  await waitFor(()=>expect(within(form).getByRole('button',{name:'Проверить и подтвердить оплату'})).toBeEnabled());
  expect(within(form).getAllByRole('option').map(o => o.textContent)).toEqual(['Наличные', 'Перевод']);
  fireEvent.change(within(form).getByRole('combobox',{name:'Канал клиентской оплаты'}),
   {target:{value:'BANK_TRANSFER'}});
  await userEvent.setup().click(within(form).getByRole('button',{name:'Проверить и подтвердить оплату'}));
  expect(screen.getByRole('alertdialog')).toHaveTextContent(/8.*500 сом/);
  await userEvent.setup().click(within(screen.getByRole('alertdialog'))
   .getByRole('button',{name:'Подтвердить оплату'}));
  await waitFor(()=>expect(recorded).toHaveLength(1));
  expect(recorded[0]).toEqual({saleId:SALE,method:'TRANSFER',channel:'BANK_TRANSFER',reference:null,comment:null});
  expect((recorded[0] as Record<string,unknown>).amount).toBeUndefined();
 });
 it('draft sale cannot send a normal customer payment',()=>{
  access.permissions=['PAYMENTS_CREATE'];
  const fetchMock=vi.fn();vi.stubGlobal('fetch',fetchMock);
  wrapper(<SaleFinancialSection sale={{...sale,status:'DRAFT'}}/>);
  expect(screen.getByRole('button',{name:'Проверить и подтвердить оплату'})).toBeDisabled();
  expect(fetchMock).not.toHaveBeenCalled();
 });
 it('Refund creation from a physical Return works without SALES_READ and never fetches protected Sales API',async()=>{
  access.permissions=['RETURNS_READ','REFUNDS_CREATE'];
  const requested:string[]=[],sent:Record<string,unknown>[]=[];
  vi.stubGlobal('fetch',vi.fn(async (url:RequestInfo|URL,init?:RequestInit)=>{
   const path=String(url);requested.push(path);
   if(path==='/api/returns/'+RETURN)return j({
    id:RETURN,saleId:SALE,warehouseId:validID,reason:'Дефект механизма',comment:null,
    createdBy:'f10',createdAt:NOW,items:[{id:PAYMENT,saleItemId:validID,quantity:1,condition:'SELLABLE'}],
   });
   if(path==='/api/refunds'&&init?.method==='POST'){
    const body=JSON.parse(String(init.body));sent.push(body);
    return j({id:PAYMENT,...body,refundedBy:'f10',refundedAt:NOW},201);
   }
   throw Error('Unexpected protected API: '+path);
  }));
  renderPath('/returns/'+RETURN+'/refund');
  const form=await screen.findByRole('form',{name:'Денежный возврат по физическому возврату'});
  fireEvent.change(within(form).getByRole('textbox',{name:'Сумма денежного возврата'}),
   {target:{value:'8500'}});
  fireEvent.change(within(form).getByRole('textbox',{name:'Причина денежного возврата'}),
   {target:{value:'Возврат кресла клиенту'}});
  await userEvent.setup().click(within(form).getByRole('button',{name:'Подтвердить возврат'}));
  expect(screen.getByRole('alertdialog')).toHaveTextContent(/Денежная компенсация 8 500 сом/);
  await userEvent.setup().click(within(screen.getByRole('alertdialog'))
   .getByRole('button',{name:'Списать деньги клиенту'}));
  await waitFor(()=>expect(sent).toHaveLength(1));
  expect(sent[0]).toMatchObject({
   saleId:SALE,returnId:RETURN,amount:'8500',method:'CASH',
   reason:'Возврат кресла клиенту',
  });
  expect(requested.some(x=>x.startsWith('/api/sales')||
   x.startsWith('/api/payments')||x.startsWith('/api/refunds/sale'))).toBe(false);
 });
 
 it('clears confirmed refund and its form on in-app navigation to a different Return',async()=>{
  access.permissions=['RETURNS_READ','REFUNDS_CREATE'];
  const otherReturn='66666666-6666-4666-8666-666666666666';
  vi.stubGlobal('fetch',vi.fn(async(url:RequestInfo|URL,init?:RequestInit)=>{
   const path=String(url);
   if(path==='/api/returns/'+RETURN||path==='/api/returns/'+otherReturn)
    return j({id:path.endsWith(otherReturn)?otherReturn:RETURN,saleId:SALE,warehouseId:validID,
     reason:'Возврат',comment:null,createdBy:'f10',createdAt:NOW,
     items:[{id:PAYMENT,saleItemId:validID,quantity:1,condition:'SELLABLE'}]});
   if(path==='/api/refunds'&&init?.method==='POST')
    return j({id:PAYMENT,...JSON.parse(String(init.body)),refundedBy:'f10',refundedAt:NOW},201);
   throw Error('Unexpected '+path);
  }));
  function SwitchReturn(){const navigate=useNavigate();return <button type="button"
   onClick={()=>navigate('/returns/'+otherReturn+'/refund')}>Другой Return</button>;}
  render(<QueryClientProvider client={createQueryClient()}>
   <MemoryRouter initialEntries={['/returns/'+RETURN+'/refund']}><SwitchReturn/><AppRouter/></MemoryRouter>
  </QueryClientProvider>);
  const first=await screen.findByRole('form',{name:'Денежный возврат по физическому возврату'});
  fireEvent.change(within(first).getByRole('textbox',{name:'Сумма денежного возврата'}),{target:{value:'8500'}});
  fireEvent.change(within(first).getByRole('textbox',{name:'Причина денежного возврата'}),{target:{value:'Возврат A'}});
  await userEvent.setup().click(within(first).getByRole('button',{name:'Подтвердить возврат'}));
  await userEvent.setup().click(within(screen.getByRole('alertdialog'))
   .getByRole('button',{name:'Списать деньги клиенту'}));
  expect(await screen.findByText(/Денежный возврат подтверждён backend:/)).toBeInTheDocument();
  await userEvent.setup().click(screen.getByRole('button',{name:'Другой Return'}));
  await waitFor(()=>expect(screen.getByText('Компенсация за оформленный физический возврат')).toBeInTheDocument());
  const second=screen.getByRole('form',{name:'Денежный возврат по физическому возврату'});
  expect(within(second).getByRole('textbox',{name:'Сумма денежного возврата'})).toHaveValue('');
  expect(within(second).getByRole('textbox',{name:'Причина денежного возврата'})).toHaveValue('');
  expect(screen.queryByText(/Денежный возврат подтверждён backend:/)).not.toBeInTheDocument();
 });
 it('EXPENSES_CREATE-only employee posts an expense without privileged GET to accounts or list',async()=>{
  access.permissions=['EXPENSES_CREATE'];
  const urls:string[]=[],posted:Record<string,unknown>[]=[];
  vi.stubGlobal('fetch',vi.fn(async (url:RequestInfo|URL,init?:RequestInit)=>{
   urls.push(String(url));
   if(String(url)==='/api/expenses'&&init?.method==='POST'){
    const body=JSON.parse(String(init.body));posted.push(body);
    return j({id:PAYMENT,...body,createdBy:'f10',createdAt:NOW},201);
   }
   throw Error('Unexpected protected API '+String(url));
  }));
  wrapper(<ExpensesPage/>);
  const form=screen.getByRole('form',{name:'Создание расхода'});
  fireEvent.change(within(form).getByRole('textbox',{name:'Сумма расхода'}),
   {target:{value:'1500'}});
  fireEvent.change(within(form).getByLabelText('Дата расхода'),
   {target:{value:'2026-10-11'}});
  await userEvent.setup().click(within(form).getByRole('button',{name:'Создать расход'}));
  expect(screen.getByRole('alertdialog')).toHaveTextContent(/1500|1.*500/);
  await userEvent.setup().click(within(screen.getByRole('alertdialog'))
   .getByRole('button',{name:'Провести расход'}));
  await waitFor(()=>expect(posted).toHaveLength(1));
  expect(posted[0]).toMatchObject({
   category:'ADVERTISING',amount:'1500',paymentMethod:'CASH',expenseDate:'2026-10-11',
  });
  expect(urls.every(x=>x==='/api/expenses')).toBe(true);
 });
 it('server 409 creates a locked unknown outcome rather than redirecting expense to another day',async()=>{
  access.permissions=['EXPENSES_CREATE'];
  vi.stubGlobal('fetch',vi.fn(async (_url:RequestInfo|URL,init?:RequestInit)=>{
   if(init?.method==='POST')return j({
    code:'CLOSED_FINANCIAL_PERIOD',message:'closed',details:{},
   },409);
   throw Error('Unexpected protected read');
  }));
  wrapper(<ExpensesPage/>);
  const form=screen.getByRole('form',{name:'Создание расхода'});
  fireEvent.change(within(form).getByRole('textbox',{name:'Сумма расхода'}),
   {target:{value:'1500'}});
  fireEvent.change(within(form).getByLabelText('Дата расхода'),
   {target:{value:'2026-10-01'}});
  await userEvent.setup().click(within(form).getByRole('button',{name:'Создать расход'}));
  await userEvent.setup().click(within(screen.getByRole('alertdialog'))
   .getByRole('button',{name:'Провести расход'}));
  expect(await screen.findByText(/Результат финансовой операции неизвестен/)).toBeInTheDocument();
  expect(screen.getByLabelText('Дата расхода')).toHaveValue('2026-10-01');
  expect(screen.getByRole('button',{name:'Создать расход'})).toBeDisabled();
 });
 it('exchange OUT calculation is fully authorized and accounts absent without FINANCE_READ',async()=>{
  access.permissions=['EXCHANGES_READ','EXCHANGES_SETTLE'];
  const calls:string[]=[];
  vi.stubGlobal('fetch',vi.fn(async (url:RequestInfo|URL)=>{
   calls.push(String(url));
   if(String(url).includes('/settlements'))return j([]);
   throw Error('Unexpected '+String(url));
  }));
  wrapper(<ExchangeSettlementForm exchange={{
   ...exchange,additionalPaymentDue:'0',refundDue:'1000',
  }}/>);
  expect(await screen.findByRole('heading',{name:'Выплатить разницу клиенту'})).toBeInTheDocument();
  expect(calls.every(x=>x.includes('/settlements'))).toBe(true);
  expect(screen.getByRole('combobox',{name:'Метод расчёта обмена'})).toBeInTheDocument();
 });
});
