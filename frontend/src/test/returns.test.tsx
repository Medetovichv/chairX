import { afterEach,beforeEach,describe,expect,it,vi } from 'vitest';
import { fireEvent,render,screen,waitFor,within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router';
import { AppRouter } from '../app/router/AppRouter';
import { createQueryClient } from '../shared/api/query';
import { authSession } from '../features/auth/session';
import { readPending } from '../features/returns/pending-operation';
import { validateReturn,validateExchange,validateDefect } from '../features/returns/validation';
import type { ReturnInput,ReturnResponse,Defect,ExchangeResponse } from '../features/returns/types';

const access=vi.hoisted(()=>({
 permissions:['RETURNS_READ','RETURNS_CREATE','EXCHANGES_READ','EXCHANGES_CREATE',
  'DEFECTS_READ','DEFECTS_MANAGE','DEFECTS_WRITE_OFF','SALES_READ','INVENTORY_READ'] as string[],
 roles:['CUSTOM_AFTERSALES'] as string[],
}));
vi.mock('../features/auth/AuthProvider',()=>({
 useAuth:()=>({
  status:'authenticated',isAuthenticated:true,
  user:{id:'after-sales-operator',username:'operator',displayName:'Продавец',
    permissions:access.permissions,roles:access.roles},
  hasPermission:(p:string)=>access.permissions.includes(p),
  hasAnyPermission:(ps:readonly string[])=>ps.some(p=>access.permissions.includes(p)),
  login:vi.fn(),logout:vi.fn(),
 }),
}));
const SALE='11111111-1111-4111-8111-111111111111';
const ITEM='22222222-2222-4222-8222-222222222222';
const ITEM2='33333333-3333-4333-8333-333333333333';
const VAR='44444444-4444-4444-8444-444444444444';
const VAR2='55555555-5555-4555-8555-555555555555';
const WH='66666666-6666-4666-8666-666666666666';
const RET='77777777-7777-4777-8777-777777777777';
const EX='88888888-8888-4888-8888-888888888888';
const DEF='99999999-9999-4999-8999-999999999999';
const DEF2='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaab';
const NOW='2026-10-10T08:00:00Z';
const json=(value:unknown,status=200)=>new Response(JSON.stringify(value),
 {status,headers:{'Content-Type':'application/json'}});
function returned(count=1,condition:'SELLABLE'|'BLOCKED'='SELLABLE'):ReturnResponse{
 return {id:RET,saleId:SALE,warehouseId:WH,createdAt:NOW,reason:'Клиент вернул кресло',comment:null,
  createdBy:'operator',items:[{id:'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
   saleItemId:ITEM,quantity:count,condition}]};
}
function defect(status:Defect['status']='OPEN'):Defect{
 return {id:DEF,warehouseId:WH,productVariantId:VAR,supplierId:null,purchaseReceiptItemId:null,
  quantity:1,status,description:'Не работает механизм наклона',
  resolutionNote:null,createdBy:'operator',createdAt:NOW,resolvedBy:null,resolvedAt:null};
}
function exchange(status:ExchangeResponse['status']='PENDING_SETTLEMENT'):ExchangeResponse{
 return {id:EX,originalSaleId:SALE,newSaleId:ITEM2,returnId:RET,status,
  returnedValue:'8500.00',newSaleTotal:'9500.00',creditApplied:'8500.00',
  additionalPaymentDue:'1000.00',refundDue:'0.00'};
}
interface Setup {
 returns?:ReturnResponse[];defects?:Defect[];
 fulfillment?:'SELF_PICKUP'|'CITY_DELIVERY';deliveryStatus?:string;
 afterReturnNetwork?:boolean;returnConflict?:boolean;
 afterExchangeNetwork?:boolean;afterDefectNetwork?:boolean;afterWriteOffNetwork?:boolean;
}
function mockBackend(options:Setup={}){
 const returns=[...(options.returns??[])];
 const defects=[...(options.defects??[])];
 const exchanges:ExchangeResponse[]=[];
 const idempotent=new Map<string,ReturnResponse>();
 let afterReturn=!!options.afterReturnNetwork,afterExchange=!!options.afterExchangeNetwork;
 let afterDefect=!!options.afterDefectNetwork,afterWriteOff=!!options.afterWriteOffNetwork;
 const count={returnPOST:0,exchangePOST:0,defectPOST:0,writeOff:0,resolve:0,wait:0};
 const fetchMock=vi.fn(async (input:RequestInfo|URL,init?:RequestInit):Promise<Response>=>{
  const url=new URL(String(input),'http://chairx.test'),path=url.pathname,method=init?.method??'GET';
  if(path==='/api/returns'&&method==='GET'){
   const page=Number(url.searchParams.get('page')??0),size=Number(url.searchParams.get('size')??20);
   const selected=returns.filter(r=>!url.searchParams.get('saleId')||r.saleId===url.searchParams.get('saleId'));
   return json({items:selected.slice(page*size,(page+1)*size).map(x=>({
    id:x.id,saleId:x.saleId,warehouseId:x.warehouseId,createdAt:x.createdAt,reason:x.reason,createdBy:x.createdBy,
   })),page,size,total:selected.length});
  }
  if(path==='/api/returns'&&method==='POST'){
   const body=JSON.parse(String(init?.body)) as ReturnInput;
   if(idempotent.has(body.idempotencyKey))return json(idempotent.get(body.idempotencyKey),201);
   if(options.returnConflict)return json({code:'RETURN_INVALID_DELIVERY',message:'',details:{}},409);
   count.returnPOST++;
   const r={id:RET,...body,createdBy:'operator',createdAt:NOW,
     items:body.items.map((i,index)=>({...i,id:'aaaaaaaa-aaaa-4aaa-8aaa-'+String(index).padStart(12,'0')}))};
   returns.push(r);idempotent.set(body.idempotencyKey,r);
   if(afterReturn){afterReturn=false;throw new TypeError('Connection lost after commit');}
   return json(r,201);
  }
  const ret=/^\/api\/returns\/([^/]+)$/.exec(path);
  if(ret&&method==='GET'){
   const r=returns.find(x=>x.id===ret[1]);
   return r?json(r):json({code:'NOT_FOUND',message:'',details:{}},404);
  }
  if(path==='/api/sales/'+SALE&&method==='GET')
   return json({id:SALE,saleNumber:'CHX-101',customerId:null,
    fulfillmentType:options.fulfillment??'SELF_PICKUP',status:'FULFILLED',
    items:[{id:ITEM,productVariantId:VAR,warehouseId:WH,quantity:3,unitSalePrice:'8500.00',total:'25500.00'},
      {id:ITEM2,productVariantId:VAR2,warehouseId:WH,quantity:1,unitSalePrice:'8000.00',total:'8000.00'}],
    total:'33500.00',createdBy:'operator',createdAt:NOW,
    fulfilledBy:'operator',fulfilledAt:NOW,cancelledBy:null,cancelledAt:null,comment:null});
  if(path==='/api/sales'&&method==='GET')return json({page:0,size:20,total:1,items:[{
    id:SALE,saleNumber:'CHX-101',status:'FULFILLED',
    fulfillmentType:options.fulfillment??'SELF_PICKUP',deliveryStatus:options.deliveryStatus??'DELIVERED',
  }]});
  if(path==='/api/warehouses'&&method==='GET')return json({
   page:0,size:100,totalElements:1,totalPages:1,items:[{
    id:WH,name:'Склад офис',code:'OFFICE',active:true,createdAt:NOW,updatedAt:NOW,
   }],
  });
  if(path==='/api/exchanges'&&method==='POST'){
   const body=JSON.parse(String(init?.body));
   count.exchangePOST++;
   if(!returns.some(r=>r.id===body.returnId))return json({code:'RETURN_NOT_FOUND',message:'',details:{}},409);
   const result=exchange();
   exchanges.push(result);
   if(afterExchange){afterExchange=false;throw new TypeError('Lost response after create');}
   return json(result,201);
  }
  const ex=/^\/api\/exchanges\/([^/]+)$/.exec(path);
  if(ex&&method==='GET')return json(exchanges.find(x=>x.id===ex[1])??exchange());
  if(path==='/api/exchanges/'+EX+'/settlements'&&method==='GET')return json([]);
  if(path==='/api/defects'&&method==='GET'){
   const page=Number(url.searchParams.get('page')??0),size=Number(url.searchParams.get('size')??20);
   const status=url.searchParams.get('status');
   const selected=defects.filter(x=>!status||x.status===status);
   return json({items:selected.slice(page*size,(page+1)*size),page,size,total:selected.length});
  }
  if(path==='/api/defects'&&method==='POST'){
   const body=JSON.parse(String(init?.body));
   count.defectPOST++;
   const result={...defect(),...body};
   defects.push(result);
   if(afterDefect){afterDefect=false;throw new TypeError('Lost response after defect block');}
   return json(result,201);
  }
  const df=/^\/api\/defects\/([^/]+)$/.exec(path);
  if(df&&method==='GET')return json(defects.find(x=>x.id===df[1])??defect());
  const act=/^\/api\/defects\/([^/]+)\/(wait-for-parts|resolve|write-off)$/.exec(path);
  if(act&&method==='POST'){
   const d=defects.find(x=>x.id===act[1]);
   if(!d)return json({code:'NOT_FOUND',message:'',details:{}},404);
   if(d.status==='RESOLVED'||d.status==='WRITTEN_OFF')return json({code:'ALREADY_CLOSED',message:'',details:{}},409);
   if(act[2]==='wait-for-parts'){d.status='WAITING_PARTS';count.wait++;}
   if(act[2]==='resolve'){d.status='RESOLVED';d.resolvedAt=NOW;d.resolvedBy='operator';
     d.resolutionNote=(JSON.parse(String(init?.body)) as {resolutionNote:string}).resolutionNote;count.resolve++;}
   if(act[2]==='write-off'){d.status='WRITTEN_OFF';d.resolvedAt=NOW;d.resolvedBy='operator';
     d.resolutionNote=(JSON.parse(String(init?.body)) as {resolutionNote:string}).resolutionNote;count.writeOff++;}
   if(act[2]==='write-off'&&afterWriteOff){
     afterWriteOff=false;throw new TypeError('Write-off committed but HTTP response lost');
   }
   return json(d);
  }
  if(path==='/api/products'&&method==='GET')return json({
   items:[{id:SALE,name:'Ergo Comfort',category:null,description:null,active:true,createdAt:NOW,updatedAt:NOW}],
   page:0,size:20,totalElements:1,totalPages:1,
  });
  if(path==='/api/products/'+SALE+'/variants'&&method==='GET')return json({
   items:[{id:VAR,productId:SALE,name:'Чёрный',sku:'ERGO-BLK',color:'Чёрный',
     recommendedSalePrice:'8500.00',active:true,createdAt:NOW,updatedAt:NOW}],
   page:0,size:20,totalElements:1,totalPages:1,
  });
  if(path.startsWith('/api/inventory/balances/')&&method==='GET')return json({
   warehouseId:WH,productVariantId:VAR,onHand:10,reserved:0,blocked:0,available:10,
  });
  if(path.startsWith('/api/product-variants/')&&method==='GET')
   return json({id:VAR,name:'Чёрное кресло',sku:'BLACK',productId:SALE,
    recommendedSalePrice:'8500',active:true});
  throw new Error('Unexpected '+method+' '+path);
 });
 vi.stubGlobal('fetch',fetchMock);
 return {count,returns,defects,exchanges,fetchMock};
}
function renderAt(path='/returns'){
 return render(<QueryClientProvider client={createQueryClient()}>
  <MemoryRouter initialEntries={[path]}><AppRouter/></MemoryRouter>
 </QueryClientProvider>);
}
const calls=(a:ReturnType<typeof mockBackend>,fragment:string)=>
 a.fetchMock.mock.calls.filter(([url])=>String(url).includes(fragment));
beforeEach(()=>{
 sessionStorage.clear();
 access.permissions=['RETURNS_READ','RETURNS_CREATE','EXCHANGES_READ','EXCHANGES_CREATE',
  'DEFECTS_READ','DEFECTS_MANAGE','DEFECTS_WRITE_OFF','SALES_READ','INVENTORY_READ'];
 access.roles=['CUSTOM_AFTERSALES'];
 authSession.activate('Basic aftersales',{headerName:'X-CSRF-TOKEN',token:'csrf'});
});
afterEach(()=>{authSession.clear();sessionStorage.clear();vi.unstubAllGlobals();vi.restoreAllMocks();});

describe('F09 backend contracts and validation',()=>{
 it('uses exact ReturnConditions and saleItemId rather than productVariantId',()=>{
  const sample:ReturnInput={saleId:SALE,warehouseId:WH,idempotencyKey:RET,reason:'Причина',
   comment:null,items:[{saleItemId:ITEM,quantity:1,condition:'SELLABLE'}]};
  expect(validateReturn(sample)).toBeNull();
  expect(validateReturn({...sample,items:[...sample.items,...sample.items]})).toMatch(/одну позицию/);
  expect(validateReturn({...sample,items:[]})).toMatch(/хотя бы одну/);
  expect(validateReturn({...sample,items:[{saleItemId:VAR,quantity:0,condition:'BLOCKED'}]})).toMatch(/количество/);
 });
 it('validates exchange against real item UUID and integer-som amounts',()=>{
  const s={idempotencyKey:RET,returnId:RET,fulfillmentType:'SELF_PICKUP' as const,
   items:[{productVariantId:VAR,warehouseId:WH,quantity:1,unitSalePrice:'9500'}]};
  expect(validateExchange(s)).toBeNull();
  expect(validateExchange({...s,items:[{...s.items[0]!,unitSalePrice:'9500.12'}]})).toMatch(/цену/);
 });
 it('requires a real variant and positive quantity for Defect',()=>{
  expect(validateDefect({warehouseId:WH,productVariantId:VAR,quantity:1,
   supplierId:null,purchaseReceiptItemId:null,description:'Повреждён'})).toBeNull();
  expect(validateDefect({warehouseId:WH,productVariantId:VAR,quantity:0,
   supplierId:null,purchaseReceiptItemId:null,description:'Повреждён'})).toMatch(/Количество/);
 });
});
describe('F09 list, detail and returns lifecycle',()=>{
 it('shows backend ReturnSummary without fictional status or costs',async()=>{
  const a=mockBackend({returns:[returned()]});renderAt('/returns');
  expect(await screen.findByRole('heading',{name:'Возвраты',level:2})).toBeInTheDocument();
  expect((await screen.findAllByText('Клиент вернул кресло')).length).toBeGreaterThan(0);
  expect(screen.queryByText('RETURN_COMPLETED')).not.toBeInTheDocument();
  expect(calls(a,'/api/payments')).toHaveLength(0);
 });
 it('filters by real saleId and fetches server page 2',async()=>{
  const entries=Array.from({length:22},(_,i)=>({...returned(),
   id:'aaaaaaaa-aaaa-4aaa-8aaa-'+String(i).padStart(12,'0')}));
  const a=mockBackend({returns:entries});renderAt();
  expect(await screen.findByText(/Всего возвратов: 22/)).toBeInTheDocument();
  await userEvent.setup().click(screen.getByRole('button',{name:'Следующая'}));
  await screen.findByText(/Страница 2 из 2/);
  fireEvent.change(screen.getByRole('textbox',{name:'Фильтр по UUID продажи'}),{target:{value:SALE}});
  await userEvent.setup().click(screen.getByRole('button',{name:'Применить'}));
  await waitFor(()=>expect(calls(a,'/api/returns?').some(([url])=>String(url).includes('saleId='+SALE))).toBe(true));
 });
 it('shows return detail with saleItemId under RETURNS_READ without SALES_READ',async()=>{
  access.permissions=['RETURNS_READ'];
  const a=mockBackend({returns:[returned(1,'BLOCKED')]});renderAt('/returns/'+RET);
  expect(await screen.findByRole('heading',{name:'Карточка возврата',level:2})).toBeInTheDocument();
  expect(await screen.findByRole('region',{name:'Возвращённые позиции'})).toHaveTextContent('Требует проверки / неисправен');
  expect(calls(a,'/api/returns/'+RET)).toHaveLength(1);
  expect(calls(a,'/api/sales')).toHaveLength(0);
 });
 it('loads all previous return pages before displaying remaining quantities',async()=>{
  const entries=Array.from({length:22},(_,i)=>({...returned(i===0?1:0),
   id:'aaaaaaaa-aaaa-4aaa-8aaa-'+String(i).padStart(12,'0')}));
  // zero-quantity existing rows are mock-only; they verify all pages are fetched.
  const a=mockBackend({returns:entries});renderAt('/returns/new?saleId='+SALE);
  expect(await screen.findByText(/Продано: 3 · Возвращено: 1/)).toBeInTheDocument();
  expect(calls(a,'/api/returns?').some(([url])=>String(url).includes('page=1'))).toBe(true);
 });
 it('creates an exact partial BLOCKED return with no extra defect or stock POST',async()=>{
  const a=mockBackend();renderAt('/returns/new?saleId='+SALE);
  const f=await screen.findByRole('form',{name:'Создание возврата'});
  const quantity=await within(f).findByRole('textbox',{name:'Количество возврата '+ITEM});
  fireEvent.change(quantity,{target:{value:'1'}});
  fireEvent.change(within(f).getByRole('combobox',{name:'Состояние '+ITEM}),{target:{value:'BLOCKED'}});
  fireEvent.change(within(f).getByRole('combobox',{name:'Склад возврата'}),{target:{value:WH}});
  fireEvent.change(within(f).getByRole('textbox',{name:'Причина возврата'}),{target:{value:'Повреждён механизм'}});
  await userEvent.setup().click(within(f).getByRole('button',{name:'Подтвердить физический возврат'}));
  expect(await screen.findByRole('heading',{name:'Карточка возврата'})).toBeInTheDocument();
  const sent=a.fetchMock.mock.calls.find(([url,init])=>String(url)==='/api/returns'&&(init as RequestInit).method==='POST');
  const body=JSON.parse(String((sent?.[1] as RequestInit).body)) as ReturnInput;
  expect(body.items).toEqual([{saleItemId:ITEM,quantity:1,condition:'BLOCKED'}]);
  expect(body.idempotencyKey).toMatch(/^[0-9a-f]{8}-/);
  expect(a.count.returnPOST).toBe(1);
  expect(calls(a,'/api/defects')).toHaveLength(0);
  expect(calls(a,'/api/inventory/transfers')).toHaveLength(0);
 });
 it('creates full return of two separate SaleItem IDs with SELLABLE quantities',async()=>{
  const a=mockBackend();renderAt('/returns/new?saleId='+SALE);
  const f=await screen.findByRole('form',{name:'Создание возврата'});
  fireEvent.change(await within(f).findByRole('textbox',{name:'Количество возврата '+ITEM}),{target:{value:'3'}});
  fireEvent.change(within(f).getByRole('textbox',{name:'Количество возврата '+ITEM2}),{target:{value:'1'}});
  fireEvent.change(within(f).getByRole('combobox',{name:'Склад возврата'}),{target:{value:WH}});
  fireEvent.change(within(f).getByRole('textbox',{name:'Причина возврата'}),{target:{value:'Полный возврат кресел'}});
  await userEvent.setup().click(within(f).getByRole('button',{name:'Подтвердить физический возврат'}));
  expect(await screen.findByRole('heading',{name:'Карточка возврата'})).toBeInTheDocument();
  const post=a.fetchMock.mock.calls.find(([url,init])=>String(url)==='/api/returns'&&(init as RequestInit).method==='POST');
  const body=JSON.parse(String((post?.[1] as RequestInit).body)) as ReturnInput;
  expect(body.items).toEqual([
   {saleItemId:ITEM,quantity:3,condition:'SELLABLE'},
   {saleItemId:ITEM2,quantity:1,condition:'SELLABLE'},
  ]);
  expect(a.count.returnPOST).toBe(1);
 });
 it('rejects exceeding remaining quantity before POST',async()=>{
  const a=mockBackend({returns:[returned(2)]});renderAt('/returns/new?saleId='+SALE);
  const f=await screen.findByRole('form',{name:'Создание возврата'});
  fireEvent.change(await within(f).findByRole('textbox',{name:'Количество возврата '+ITEM}),{target:{value:'2'}});
  fireEvent.change(within(f).getByRole('combobox',{name:'Склад возврата'}),{target:{value:WH}});
  fireEvent.change(within(f).getByRole('textbox',{name:'Причина возврата'}),{target:{value:'Возврат'}});
  await userEvent.setup().click(within(f).getByRole('button',{name:'Подтвердить физический возврат'}));
  expect(await screen.findByRole('alert')).toHaveTextContent(/превышает/);
  expect(a.count.returnPOST).toBe(0);
 });
 it('blocks ordinary return for FAILED delivery, only F08 can RETURN_IN',async()=>{
  const a=mockBackend({fulfillment:'CITY_DELIVERY',deliveryStatus:'FAILED'});
  renderAt('/returns/new?saleId='+SALE);
  expect(await screen.findByText(/для возврата доставки необходим статус DELIVERED/i)).toBeInTheDocument();
  expect(screen.getByRole('button',{name:'Подтвердить физический возврат'})).toBeDisabled();
  expect(a.count.returnPOST).toBe(0);
 });
 it('replays an ambiguous physical return with the exact payload/key and only one RETURN_IN',async()=>{
  const a=mockBackend({afterReturnNetwork:true});
  renderAt('/returns/new?saleId='+SALE);
  const form=await screen.findByRole('form',{name:'Создание возврата'});
  fireEvent.change(await within(form).findByRole('textbox',{name:'Количество возврата '+ITEM}),{target:{value:'1'}});
  fireEvent.change(within(form).getByRole('combobox',{name:'Склад возврата'}),{target:{value:WH}});
  fireEvent.change(within(form).getByRole('textbox',{name:'Причина возврата'}),{target:{value:'Повреждение'}});
  await userEvent.setup().click(within(form).getByRole('button',{name:'Подтвердить физический возврат'}));
  expect(await screen.findByText(/Результат возврата неизвестен/)).toBeInTheDocument();
  const before=a.fetchMock.mock.calls.filter(([url,init])=>String(url)==='/api/returns'&&
    (init as RequestInit)?.method==='POST').map(([,init])=>String((init as RequestInit).body));
  expect(before).toHaveLength(1);
  await userEvent.setup().click(screen.getByRole('button',{name:'Повторить тот же запрос и ключ'}));
  expect(await screen.findByRole('heading',{name:'Карточка возврата',level:2})).toBeInTheDocument();
  const after=a.fetchMock.mock.calls.filter(([url,init])=>String(url)==='/api/returns'&&
    (init as RequestInit)?.method==='POST').map(([,init])=>String((init as RequestInit).body));
  expect(after).toHaveLength(2);
  expect(after[1]).toEqual(before[0]);
  expect(a.count.returnPOST).toBe(1);
  expect(readPending('after-sales-operator','return').kind).toBe('missing');
 });
 it('recovers uncertain return in same tab without creating a second idempotency key',async()=>{
  const a=mockBackend({afterReturnNetwork:true});
  const first=renderAt('/returns/new?saleId='+SALE);
  const f=await screen.findByRole('form',{name:'Создание возврата'});
  fireEvent.change(await within(f).findByRole('textbox',{name:'Количество возврата '+ITEM}),{target:{value:'1'}});
  fireEvent.change(within(f).getByRole('combobox',{name:'Склад возврата'}),{target:{value:WH}});
  fireEvent.change(within(f).getByRole('textbox',{name:'Причина возврата'}),{target:{value:'Возврат'}});
  await userEvent.setup().click(within(f).getByRole('button',{name:'Подтвердить физический возврат'}));
  expect(await screen.findByText(/Результат возврата неизвестен/)).toBeInTheDocument();
  expect(a.count.returnPOST).toBe(1);
  expect(readPending('after-sales-operator','return').kind).toBe('pending');
  first.unmount();
  renderAt('/returns/new');
  expect(await screen.findByText(/Операция возврата заблокирована до сверки/)).toBeInTheDocument();
  expect(a.count.returnPOST).toBe(1);
 });
});
describe('F09 exchange and settlements boundaries',()=>{
 it('shows honest create/known-ID exchange screen but never calls GET /api/exchanges collection',async()=>{
  const a=mockBackend({returns:[returned()]});
  renderAt('/returns/exchanges');
  expect(await screen.findByRole('heading',{name:'Обмены',level:2})).toBeInTheDocument();
  expect(screen.getByText(/Глобального GET \/api\/exchanges нет/)).toBeInTheDocument();
  expect(calls(a,'/api/exchanges').filter(([url])=>String(url)==='/api/exchanges')).toHaveLength(0);
 });
 it('shows backend amounts and read-only settlements with no settlement POST',async()=>{
  const a=mockBackend({returns:[returned()]});renderAt('/returns/exchanges/'+EX);
  expect(await screen.findByRole('heading',{name:'Карточка обмена'})).toBeInTheDocument();
  expect(await screen.findByText(/Ожидает финансового расчёта/)).toBeInTheDocument();
  expect(screen.getByText('Доплата клиента')).toBeInTheDocument();
  expect(calls(a,'/api/exchanges/'+EX+'/settlements').filter(([,init])=>(init as RequestInit)?.method==='POST')).toHaveLength(0);
  expect(calls(a,'/api/refunds')).toHaveLength(0);
 });
 it('creates one linked exchange sale with explicit confirmation, never directly POSTs sales',async()=>{
  access.permissions.push('CATALOG_READ');
  const a=mockBackend({returns:[returned()]});
  renderAt('/returns/exchanges?returnId='+RET);
  const form=await screen.findByRole('form',{name:'Создать обмен'});
  await userEvent.setup().click(within(form).getByRole('button',{name:'+ Добавить кресло'}));
  await userEvent.setup().click(within(form).getByRole('button',{name:'Выбрать вариацию'}));
  fireEvent.change(await within(form).findByRole('combobox',{name:'Модель кресла'}),{target:{value:SALE}});
  await userEvent.setup().click(await within(form).findByRole('button',{name:/Чёрный.*ERGO-BLK/}));
  fireEvent.change(within(form).getByRole('combobox',{name:'Склад позиции 1'}),{target:{value:WH}});
  fireEvent.change(within(form).getByRole('textbox',{name:'Цена продажи позиции 1'}),{target:{value:'9500'}});
  await userEvent.setup().click(within(form).getByRole('button',{name:'Проверить и создать обмен'}));
  expect(a.count.exchangePOST).toBe(0);
  expect(screen.getByRole('alertdialog')).toHaveTextContent(/Backend создаст новую продажу/);
  await userEvent.setup().click(within(screen.getByRole('alertdialog')).getByRole('button',{name:'Создать обмен'}));
  expect(await screen.findByRole('heading',{name:'Карточка обмена'})).toBeInTheDocument();
  expect(a.count.exchangePOST).toBe(1);
  const post=a.fetchMock.mock.calls.find(([url,init])=>String(url)==='/api/exchanges'&&(init as RequestInit).method==='POST');
  const body=JSON.parse(String((post?.[1] as RequestInit).body)) as {items:{productVariantId:string;warehouseId:string;quantity:number;unitSalePrice:string}[];returnId:string;idempotencyKey:string};
  expect(body.returnId).toBe(RET);
  expect(body.items).toEqual([{productVariantId:VAR,warehouseId:WH,quantity:1,unitSalePrice:'9500'}]);
  expect(body.idempotencyKey).toMatch(/^[a-f0-9]{8}-/);
  expect(calls(a,'/api/sales/drafts')).toHaveLength(0);
  expect(calls(a,'/api/exchanges/'+EX+'/settlements').filter(([,init])=>(init as RequestInit)?.method==='POST')).toHaveLength(0);
 });
 it('allows custom EXCHANGES_CREATE-only employee to access creation without reading protected data',async()=>{
  access.permissions=['EXCHANGES_CREATE'];access.roles=['AFTER_SALES_CREATE_ONLY'];
  const a=mockBackend({returns:[returned()]});renderAt('/returns/exchanges');
  expect(await screen.findByRole('form',{name:'Создать обмен'})).toBeInTheDocument();
  expect(screen.queryByRole('searchbox',{name:'Поиск обмена по UUID'})).not.toBeInTheDocument();
  expect(calls(a,'/api/returns')).toHaveLength(0);
  expect(calls(a,'/api/exchanges')).toHaveLength(0);
 });
});
describe('F09 defect states, permissions and stock boundaries',()=>{
 it('lists actual paginated defects and filters WAITING_PARTS',async()=>{
  const a=mockBackend({defects:[defect('WAITING_PARTS'),defect('OPEN')]});
  renderAt('/returns/defects');
  expect(await screen.findByRole('heading',{name:'Брак',level:2})).toBeInTheDocument();
  fireEvent.change(screen.getByRole('combobox',{name:'Фильтр статуса брака'}),
   {target:{value:'WAITING_PARTS'}});
  await waitFor(()=>expect(calls(a,'/api/defects?').some(([url])=>String(url).includes('status=WAITING_PARTS'))).toBe(true));
 });
 it('renders defect detail without forbidden catalog lookup',async()=>{
  access.permissions=['DEFECTS_READ'];
  const a=mockBackend({defects:[defect()]});renderAt('/returns/defects/'+DEF);
  expect(await screen.findByRole('heading',{name:/Дефект: Открыт/})).toBeInTheDocument();
  expect(screen.getByText(/Не работает механизм наклона/)).toBeInTheDocument();
  expect(calls(a,'/api/product-variants/')).toHaveLength(0);
  expect(screen.queryByRole('button',{name:'Списать товар'})).not.toBeInTheDocument();
 });
 it('moves OPEN defect to WAITING_PARTS without manual stock unblocking',async()=>{
  const a=mockBackend({defects:[defect()]});renderAt('/returns/defects/'+DEF);
  await screen.findByRole('heading',{name:/Дефект: Открыт/});
  await userEvent.setup().click(screen.getByRole('button',{name:'Ожидает запчасти'}));
  await userEvent.setup().click(within(screen.getByRole('alertdialog')).getByRole('button',{name:'Подтвердить действие'}));
  await waitFor(()=>expect(a.count.wait).toBe(1));
  expect(await screen.findByRole('heading',{name:/Дефект: Ожидает запчасти/})).toBeInTheDocument();
  expect(calls(a,'/api/inventory')).toHaveLength(0);
 });
 it('write-off needs dedicated DEFECTS_WRITE_OFF, not DEFECTS_MANAGE',async()=>{
  access.permissions=['DEFECTS_READ','DEFECTS_MANAGE'];
  const a=mockBackend({defects:[defect()]});renderAt('/returns/defects/'+DEF);
  await screen.findByRole('heading',{name:/Дефект: Открыт/});
  expect(screen.getByRole('button',{name:'Исправлено'})).toBeInTheDocument();
  expect(screen.queryByRole('button',{name:'Списать товар'})).not.toBeInTheDocument();
  expect(a.count.writeOff).toBe(0);
 });
 it('write-off is explicitly confirmed; only backend mutates FIFO and stock',async()=>{
  access.permissions=['DEFECTS_READ','DEFECTS_WRITE_OFF'];
  const a=mockBackend({defects:[defect()]});renderAt('/returns/defects/'+DEF);
  await screen.findByRole('heading',{name:/Дефект: Открыт/});
  fireEvent.change(screen.getByRole('textbox',{name:'Результат обработки брака'}),
   {target:{value:'Ремонту не подлежит'}});
  await userEvent.setup().click(screen.getByRole('button',{name:'Списать товар'}));
  expect(a.count.writeOff).toBe(0);
  expect(screen.getByRole('alertdialog')).toHaveTextContent(/FIFO/);
  await userEvent.setup().click(within(screen.getByRole('alertdialog'))
   .getByRole('button',{name:'Подтвердить действие'}));
  await waitFor(()=>expect(a.count.writeOff).toBe(1));
  expect(calls(a,'/api/inventory/')).toHaveLength(0);
 });
 it('resolve releases blocked stock only on backend; never calls inventory unblock',async()=>{
  const a=mockBackend({defects:[defect('WAITING_PARTS')]});renderAt('/returns/defects/'+DEF);
  await screen.findByRole('heading',{name:/Дефект: Ожидает запчасти/});
  fireEvent.change(screen.getByRole('textbox',{name:'Результат обработки брака'}),
   {target:{value:'Заменён механизм'}});
  await userEvent.setup().click(screen.getByRole('button',{name:'Исправлено'}));
  await userEvent.setup().click(within(screen.getByRole('alertdialog'))
   .getByRole('button',{name:'Подтвердить действие'}));
  await waitFor(()=>expect(a.count.resolve).toBe(1));
  expect(calls(a,'/api/inventory/')).toHaveLength(0);
 });
 it('defect creation with no idempotency key stays locked after lost response/reload',async()=>{
  const a=mockBackend({defects:[],afterDefectNetwork:true});
  const first=renderAt('/returns/defects');
  await screen.findByRole('heading',{name:'Брак',level:2});
  await userEvent.setup().click(screen.getByRole('button',{name:'+ Зарегистрировать брак'}));
  const form=screen.getByRole('form',{name:'Регистрация дефекта'});
  fireEvent.change(within(form).getByRole('combobox',{name:'Склад брака'}),{target:{value:WH}});
  fireEvent.change(within(form).getByRole('textbox',{name:'UUID вариации брака'}),{target:{value:VAR}});
  fireEvent.change(within(form).getByRole('textbox',{name:'Описание брака'}),
   {target:{value:'Сломан механизм'}});
  await userEvent.setup().click(within(form).getByRole('button',{name:'Зарегистрировать брак'}));
  expect(await screen.findByText(/Результат регистрации брака неизвестен/)).toBeInTheDocument();
  expect(a.count.defectPOST).toBe(1);
  first.unmount();
  renderAt('/returns/defects');
  await screen.findByRole('heading',{name:'Брак',level:2});
  await userEvent.setup().click(screen.getByRole('button',{name:'+ Зарегистрировать брак'}));
  expect(screen.getByRole('button',{name:'Зарегистрировать брак'})).toBeDisabled();
  expect(a.count.defectPOST).toBe(1);
 });
 it('fails closed before irreversible write-off POST if recovery storage is unavailable',async()=>{
   const a=mockBackend({defects:[defect()]});renderAt('/returns/defects/'+DEF);
   await screen.findByRole('heading',{name:/Дефект: Открыт/});
   fireEvent.change(screen.getByRole('textbox',{name:'Результат обработки брака'}),
    {target:{value:'Неремонтопригодно'}});
   vi.spyOn(Storage.prototype,'setItem').mockImplementation(()=>{throw new Error('Storage denied');});
   await userEvent.setup().click(screen.getByRole('button',{name:'Списать товар'}));
   await userEvent.setup().click(within(screen.getByRole('alertdialog'))
    .getByRole('button',{name:'Подтвердить действие'}));
   expect(await screen.findByText(/Хранилище операции недоступно/)).toBeInTheDocument();
   expect(a.count.writeOff).toBe(0);
   expect(screen.getByRole('button',{name:'Списать товар'})).toBeDisabled();
 });
 it('recovers lost write-off by defect ID after reload without repeating FIFO stock movement',async()=>{
   const a=mockBackend({defects:[defect(),{...defect(),id:DEF2}],afterWriteOffNetwork:true});
   const first=renderAt('/returns/defects/'+DEF);
   await screen.findByRole('heading',{name:/Дефект: Открыт/});
   fireEvent.change(screen.getByRole('textbox',{name:'Результат обработки брака'}),
    {target:{value:'Ремонту не подлежит'}});
   await userEvent.setup().click(screen.getByRole('button',{name:'Списать товар'}));
   await userEvent.setup().click(within(screen.getByRole('alertdialog'))
    .getByRole('button',{name:'Подтвердить действие'}));
   expect(await screen.findByText(/Результат операции неизвестен/)).toBeInTheDocument();
   expect(a.count.writeOff).toBe(1);
   expect(readPending('after-sales-operator','writeoff',DEF).kind).toBe('pending');
   first.unmount();
   const second=renderAt('/returns/defects/'+DEF2);
   await screen.findByRole('heading',{name:/Дефект: Открыт/});
   expect(screen.getByRole('button',{name:'Списать товар'})).toBeEnabled();
   second.unmount();
   renderAt('/returns/defects/'+DEF);
   expect(await screen.findByText(/Действия заблокированы/)).toBeInTheDocument();
   await userEvent.setup().click(screen.getByRole('button',{name:'Загрузить актуальный статус'}));
   expect(await screen.findByText(/Сверка подтверждена backend/)).toBeInTheDocument();
   expect(readPending('after-sales-operator','writeoff',DEF).kind).toBe('missing');
   expect(a.count.writeOff).toBe(1);
   expect(calls(a,'/api/inventory/')).toHaveLength(0);
 });
 it('create-only defect operator can register without triggering restricted list GET',async()=>{
   access.permissions=['DEFECTS_MANAGE','INVENTORY_READ'];
   const a=mockBackend({defects:[]});
   renderAt('/returns');
   expect(await screen.findByRole('heading',{name:'Брак',level:2})).toBeInTheDocument();
   expect(screen.getByText(/Для списка и карточек дефектов требуется DEFECTS_READ/)).toBeInTheDocument();
   expect(calls(a,'/api/defects?')).toHaveLength(0);
   await userEvent.setup().click(screen.getByRole('button',{name:'+ Зарегистрировать брак'}));
   expect(screen.getByRole('form',{name:'Регистрация дефекта'})).toBeInTheDocument();
   expect(calls(a,'/api/defects?')).toHaveLength(0);
 });
 it('return-create-only operator gets creation route rather than hidden menu or forbidden list',async()=>{
   access.permissions=['RETURNS_CREATE'];
   const a=mockBackend();
   renderAt('/returns');
   expect(await screen.findByRole('heading',{name:'Новый возврат'})).toBeInTheDocument();
   expect(screen.getByText(/Необходимо SALES_READ/)).toBeInTheDocument();
   expect(calls(a,'/api/sales')).toHaveLength(0);
   expect(calls(a,'/api/returns')).toHaveLength(0);
 });
 it('legacy /exchanges and /defects redirects remain valid, no extra sidebar link',async()=>{
  mockBackend({defects:[],returns:[]});renderAt('/defects');
  expect(await screen.findByRole('heading',{name:'Брак',level:2})).toBeInTheDocument();
  expect(screen.queryByRole('navigation',{name:'Вкладки раздела'})).toBeInTheDocument();
 });
});
