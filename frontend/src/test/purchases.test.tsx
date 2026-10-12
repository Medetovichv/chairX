import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router';
import { AppRouter } from '../app/router/AppRouter';
import { authSession } from '../features/auth/session';
import { createQueryClient } from '../shared/api/query';
import type { Purchase, PurchaseLine, Supplier, ReceivingLine, PaymentSummary } from '../features/purchases/types';
import { checkMoney, integerQuantity, purchaseInput, validatePurchase } from '../features/purchases/validation';
import { purchasePreview } from '../features/purchases/components/PurchaseForm';

const access = vi.hoisted(() => ({
  roles: ['CUSTOM_PURCHASE_TEAM'] as string[],
  permissions: ['PURCHASE_READ','PURCHASE_CREATE','PURCHASE_UPDATE','PURCHASE_CONFIRM',
    'PURCHASE_CANCEL','INVENTORY_RECEIVE','INVENTORY_READ','SUPPLIERS_READ',
    'SUPPLIERS_MANAGE','CATALOG_READ','PURCHASE_PAYMENTS_READ'] as string[],
}));
vi.mock('../features/auth/AuthProvider', () => ({
  useAuth: () => ({
    status:'authenticated', isAuthenticated:true,
    user:{id:'staff', username:'worker', displayName:'Сотрудник',roles:access.roles,permissions:access.permissions},
    hasPermission:(p:string)=>access.permissions.includes(p),
    hasAnyPermission:(ps:readonly string[])=>ps.some(p=>access.permissions.includes(p)),
    login:vi.fn(), logout:vi.fn(),
  }),
}));
const SID='11111111-1111-4111-8111-111111111111';
const MODEL='22222222-2222-4222-8222-222222222222';
const VARIANT='33333333-3333-4333-8333-333333333333';
const WID='44444444-4444-4444-8444-444444444444';
const PID='55555555-5555-4555-8555-555555555555';
const ITEM='66666666-6666-4666-8666-666666666666';
const RID='77777777-7777-4777-8777-777777777777';
const TIME='2026-10-10T10:00:00Z';
const json=(body:unknown,status=200)=>new Response(JSON.stringify(body),
  {status,headers:{'Content-Type':'application/json'}});
function supplier(changes:Partial<Supplier>={}):Supplier {
  return {id:SID,name:'Шэньчжэнь',contactInformation:'China, +8612345',comment:'Основной',
    active:true,createdAt:TIME,updatedAt:TIME,...changes};
}
function purchase(changes:Partial<Purchase>={}):Purchase {
  return {id:PID,supplierId:SID,status:'CONFIRMED',cargoCost:'1000.00',
    cargoAllocationMethod:'QUANTITY',costsLockedAt:null,confirmedAt:TIME,
    comment:'Первая партия',createdBy:'manager',createdAt:TIME,updatedAt:TIME,
    items:[{id:ITEM,productVariantId:VARIANT,lineNumber:1,
      productId:MODEL,modelName:'Ergo Comfort',variantName:'Чёрный',sku:'ERGO-BLK',
      orderedQuantity:10,receivedQuantity:0,purchaseUnitCost:'4500.00',
      allocatedCargoCost:'1000.00',finalUnitCost:'4600.00'}],...changes};
}
function listLine(p:Purchase):PurchaseLine {
  const ordered=p.items.reduce((x,i)=>x+i.orderedQuantity,0);
  const received=p.items.reduce((x,i)=>x+i.receivedQuantity,0);
  return {id:p.id,supplierId:p.supplierId,supplierName:'Шэньчжэнь',status:p.status,createdAt:p.createdAt,
    itemCount:p.items.length,orderedQuantity:ordered,receivedQuantity:received,
    remainingQuantity:ordered-received,goodsCost:'45000.00',cargoCost:p.cargoCost,
    totalCost:p.cargoCost?'46000.00':null,lastReceiptAt:null};
}
function receiving(p:Purchase):ReceivingLine[] {
  return p.items.map(line=>({
    purchaseId:p.id,status:p.status,createdAt:p.createdAt,purchaseItemId:line.id,
    productVariantId:line.productVariantId,model:'Ergo Comfort',variant:'Чёрный',
    orderedQuantity:line.orderedQuantity,receivedQuantity:line.receivedQuantity,
    remainingQuantity:line.orderedQuantity-line.receivedQuantity,
  }));
}
const payments:PaymentSummary={
  purchaseId:PID,supplierTotal:'45000.00',cargoTotal:'1000.00',
  paidSupplier:'15000.00',paidCargo:'0.00',
  remainingSupplier:'30000.00',remainingCargo:'1000.00',
  payments:[{id:RID,purchaseId:PID,paymentKind:'SUPPLIER',account:'BANK',
    amount:'15000.00',idempotencyKey:RID,reference:'PAY-1',comment:null,
    createdBy:'manager',createdAt:TIME}],
};
function mockApi(opts:{
  purchases?:Purchase[]; suppliers?:Supplier[]; initialReceived?:number;
  fail?:Record<string,number>; networkReceiptOnce?:boolean; networkSupplierCreateOnce?:boolean; network?:boolean; receiptHistory?:boolean;
}={}) {
  const list=[...(opts.purchases??[purchase()])];
  const allSuppliers=[...(opts.suppliers??[supplier()])];
  if(opts.initialReceived!==undefined && list[0]?.items[0]){
    list[0].items[0].receivedQuantity=opts.initialReceived;
    list[0].status=opts.initialReceived>=10?'RECEIVED':opts.initialReceived>0?'PARTIALLY_RECEIVED':'CONFIRMED';
  }
  const receipts=new Map<string,unknown>();
  let failOnce=Boolean(opts.networkReceiptOnce);
  let failSupplierOnce=Boolean(opts.networkSupplierCreateOnce);
  const calls=vi.fn(async (input:RequestInfo|URL,init?:RequestInit):Promise<Response>=>{
    const url=new URL(String(input),'http://chairx.local');
    const path=url.pathname;const method=init?.method??'GET';
    if(opts.network) throw new TypeError('No connection');
    const failure=opts.fail?.[method+' '+path];
    if(failure) return json({code:'REJECTED',message:'java.lang.private stack',details:{}},failure);
    const page=Number(url.searchParams.get('page')??0);
    const size=Number(url.searchParams.get('size')??20);
    if(path==='/api/purchases/overview' && method==='GET') {
      const status=url.searchParams.get('status');
      const sup=url.searchParams.get('supplierId');
      const num=url.searchParams.get('number')?.toLowerCase();
      const from=url.searchParams.get('from'),to=url.searchParams.get('to');
      const items=list.filter(p=>(!status||p.status===status)&&(!sup||p.supplierId===sup)&&
        (!num||p.id.toLowerCase().includes(num))&&(!from||p.createdAt.slice(0,10)>=from)&&
        (!to||p.createdAt.slice(0,10)<=to)).map(listLine);
      return json({items:items.slice(page*size,(page+1)*size),page,size,total:items.length});
    }
    if(path==='/api/purchases/receiving' && method==='GET') {
      const items=list.filter(p=>['CONFIRMED','PARTIALLY_RECEIVED'].includes(p.status))
        .flatMap(p=>receiving(p).filter(line=>line.remainingQuantity>0));
      return json({items:items.slice(page*size,(page+1)*size),page,size,total:items.length});
    }
    if(path==='/api/purchases' && method==='POST') {
      const value=JSON.parse(String(init?.body)) as {supplierId:string;items:Array<{productVariantId:string;orderedQuantity:number;purchaseUnitCost:string}>;cargoCost:string|null;comment:string|null};
      const p=purchase({id:RID,status:'DRAFT',supplierId:value.supplierId,cargoCost:value.cargoCost,
        comment:value.comment,confirmedAt:null,items:value.items.map((v,i)=>({
          id:i===0?ITEM:RID,productVariantId:v.productVariantId,lineNumber:i+1,
          productId:MODEL,modelName:'Ergo Comfort',variantName:'Чёрный',sku:'ERGO-BLK',
          orderedQuantity:v.orderedQuantity,receivedQuantity:0,purchaseUnitCost:v.purchaseUnitCost,
          allocatedCargoCost:null,finalUnitCost:null,
        }))});list.push(p);return json(p,201);
    }
    if(path==='/api/suppliers' && method==='GET') return json({
      items:allSuppliers.slice(page*size,(page+1)*size),
      page,size,totalElements:allSuppliers.length,totalPages:Math.ceil(allSuppliers.length/size),
    });
    if(path==='/api/suppliers' && method==='POST') {
      const data=JSON.parse(String(init?.body)) as Supplier;
      const saved=supplier({...data,id:RID});allSuppliers.push(saved);
      if(failSupplierOnce){failSupplierOnce=false;throw new TypeError('Response lost after creating supplier');}
      return json(saved,201);
    }
    const sState=/^\/api\/suppliers\/([^/]+)\/(activate|deactivate)$/.exec(path);
    if(sState && method==='POST') {
      const s=allSuppliers.find(item=>item.id===sState[1]);
      if(!s)return json({},404);
      s.active=sState[2]==='activate';return json(s);
    }
    const sid=/^\/api\/suppliers\/([^/]+)$/.exec(path);
    if(sid){
      const s=allSuppliers.find(item=>item.id===sid[1]);
      if(!s)return json({},404);
      if(method==='PUT')Object.assign(s,JSON.parse(String(init?.body)) as Supplier);
      return json(s);
    }
    if(path==='/api/products/'+MODEL && method==='GET')
      return json({id:MODEL,name:'Ergo Comfort',category:null,description:null,active:true,
        createdAt:TIME,updatedAt:TIME});
    if(path==='/api/product-variants/'+VARIANT && method==='GET')
      return json({id:VARIANT,productId:MODEL,name:'Чёрный',sku:'ERGO-BLK',color:'Чёрный',
        active:true,recommendedSalePrice:8500,createdAt:TIME,updatedAt:TIME});
    if(path==='/api/products' && method==='GET') return json({page,size,items:[
      {id:MODEL,name:'Ergo Comfort',category:null,description:null,active:true,
        createdAt:TIME,updatedAt:TIME}],totalElements:1,totalPages:1});
    if(path==='/api/products/'+MODEL+'/variants' && method==='GET') return json({page,size,
      items:[{id:VARIANT,productId:MODEL,name:'Чёрный',sku:'ERGO-BLK',color:'Чёрный',
        active:true,recommendedSalePrice:8500,createdAt:TIME,updatedAt:TIME}],
      totalElements:1,totalPages:1});
    if(path==='/api/warehouses' && method==='GET')return json({
      items:[{id:WID,name:'Дом',code:'HOME',address:null,active:true,createdAt:TIME,updatedAt:TIME}],
      page,size,totalElements:1,totalPages:1,
    });
    const receiptSummary=/^\/api\/purchases\/([^/]+)\/receiving-summary$/.exec(path);
    if(receiptSummary && method==='GET'){
      const p=list.find(p=>p.id===receiptSummary[1]);
      if(!p)return json({},404);
      return json({purchaseId:p.id,status:p.status,items:receiving(p)});
    }
    const receipt=/^\/api\/purchases\/([^/]+)\/receipts$/.exec(path);
    if(receipt&&method==='GET'){
      return json({items:opts.receiptHistory?[{
        id:RID,warehouseId:WID,postedAt:TIME,createdBy:'manager',
      }]:[],page,size,totalElements:opts.receiptHistory?1:0});
    }
    const receiptDetail=/^\/api\/purchases\/([^/]+)\/receipts\/([^/]+)$/.exec(path);
    if(receiptDetail&&method==='GET'&&opts.receiptHistory){
      return json({id:RID,purchaseId:PID,warehouseId:WID,idempotencyKey:RID,
        postedAt:TIME,createdBy:'manager',comment:'Первая партия',
        items:[{id:RID,purchaseItemId:ITEM,productVariantId:VARIANT,quantity:6,
          allocatedCargoCost:'0.00',totalCost:'27000.00'}]});
    }
    if(receipt && method==='POST'){
      const body=JSON.parse(String(init?.body)) as {
        warehouseId:string;idempotencyKey:string;items:Array<{purchaseItemId:string;quantity:number}>;comment:string|null;
      };
      const key=receipt[1]+':'+body.idempotencyKey;
      if(receipts.has(key))return json(receipts.get(key),201);
      const p=list.find(p=>p.id===receipt[1]);
      if(!p)return json({},404);
      for(const line of body.items){
        const item=p.items.find(i=>i.id===line.purchaseItemId);
        if(!item||line.quantity>item.orderedQuantity-item.receivedQuantity)
          return json({code:'PURCHASE_QUANTITY_EXCEEDED',message:'',details:{}},409);
        item.receivedQuantity+=line.quantity;
      }
      p.status=p.items.every(i=>i.orderedQuantity===i.receivedQuantity)?'RECEIVED':'PARTIALLY_RECEIVED';
      const result={id:RID,purchaseId:p.id,warehouseId:body.warehouseId,
        idempotencyKey:body.idempotencyKey,postedAt:TIME,items:[
          {id:RID,purchaseItemId:ITEM,quantity:body.items[0]?.quantity,
            allocatedCargoCost:'999999.00',totalCost:'999999.00'}]};
      receipts.set(key,result);
      if(failOnce){failOnce=false;throw new TypeError('Socket disconnected after commit');}
      return json(result,201);
    }
    const pay=/^\/api\/purchases\/([^/]+)\/payments$/.exec(path);
    if(pay && method==='GET')return json({...payments,purchaseId:pay[1]});
    const cargo=/^\/api\/purchases\/([^/]+)\/cargo$/.exec(path);
    if(cargo && method==='PUT'){
      const p=list.find(p=>p.id===cargo[1]);if(!p)return json({},404);
      p.cargoCost=(JSON.parse(String(init?.body)) as {cargoCost:string}).cargoCost;
      return json(p);
    }
    const action=/^\/api\/purchases\/([^/]+)\/(confirm|cancel)$/.exec(path);
    if(action && method==='POST'){
      const p=list.find(p=>p.id===action[1]);if(!p)return json({},404);
      p.status=action[2]==='confirm'?'CONFIRMED':'CANCELLED';return json(p);
    }
    const detail=/^\/api\/purchases\/([^/]+)$/.exec(path);
    if(detail){
      const p=list.find(p=>p.id===detail[1]);if(!p)return json({},404);
      if(method==='PUT'){
        const req=JSON.parse(String(init?.body)) as {supplierId:string;items:Purchase['items'];cargoCost:string|null;comment:string|null};
        p.supplierId=req.supplierId;p.cargoCost=req.cargoCost;p.comment=req.comment;
        p.items=req.items.map((line,i)=>({...line,id:i===0?ITEM:RID,lineNumber:i+1,
          receivedQuantity:0,allocatedCargoCost:null,finalUnitCost:null}));
      }
      return json(p);
    }
    throw new Error('Unexpected '+method+' '+path);
  });
  vi.stubGlobal('fetch',calls);
  return {calls,list,allSuppliers,receipts};
}
function renderAt(path='/purchases'){
  return render(<QueryClientProvider client={createQueryClient()}>
    <MemoryRouter initialEntries={[path]}><AppRouter /></MemoryRouter>
  </QueryClientProvider>);
}
function requests(calls:ReturnType<typeof vi.fn>,phrase:string){
  return calls.mock.calls.filter(([input])=>String(input).includes(phrase));
}
beforeEach(()=>{
  access.roles=['CUSTOM_PURCHASE_TEAM'];
  access.permissions=['PURCHASE_READ','PURCHASE_CREATE','PURCHASE_UPDATE','PURCHASE_CONFIRM',
    'PURCHASE_CANCEL','INVENTORY_RECEIVE','INVENTORY_READ','SUPPLIERS_READ',
    'SUPPLIERS_MANAGE','CATALOG_READ','PURCHASE_PAYMENTS_READ'];
  authSession.activate('Basic f06-test',{headerName:'X-CSRF-TOKEN',token:'csrf'});
});
afterEach(()=>{authSession.clear();vi.unstubAllGlobals();});

describe('F06 list, filters and drafts',()=>{
  it('loads financial overview, real UUID and server pagination',async()=>{
    const {calls}=mockApi({purchases:Array.from({length:22},(_,i)=>purchase({
      id:'55555555-5555-4555-8555-'+String(i).padStart(12,'0')}))});
    renderAt();
    expect(await screen.findByText(/Всего закупок: 22/)).toBeInTheDocument();
    expect(screen.getAllByText('Шэньчжэнь').length).toBeGreaterThan(0);
    expect(screen.getAllByText(/46\s?000,00 сом/).length).toBeGreaterThan(0);
    await userEvent.setup().click(screen.getByRole('button',{name:'Следующая'}));
    expect(await screen.findByText(/Страница 2 из 2/)).toBeInTheDocument();
    expect(requests(calls,'page=1').length).toBeGreaterThanOrEqual(1);
  });
  it('filters by human-readable status, supplier and backend Bishkek date params',async()=>{
    const {calls}=mockApi();
    renderAt();
    await screen.findByRole('combobox',{name:'Фильтр статуса закупки'});
    fireEvent.change(screen.getByRole('combobox',{name:'Фильтр статуса закупки'}),{target:{value:'CONFIRMED'}});
    fireEvent.change(screen.getByRole('combobox',{name:'Фильтр поставщика'}),{target:{value:SID}});
    fireEvent.change(screen.getByLabelText('Период с'),{target:{value:'2026-10-09'}});
    fireEvent.change(screen.getByLabelText('Период по'),{target:{value:'2026-10-11'}});
    await waitFor(()=>expect(calls.mock.calls.some(([url])=>{
      const u=new URL(String(url),'http://chairx.local');
      return u.pathname==='/api/purchases/overview'&&u.searchParams.get('status')==='CONFIRMED'&&
        u.searchParams.get('supplierId')===SID&&!u.searchParams.has('number')&&
        u.searchParams.get('from')==='2026-10-09'&&u.searchParams.get('to')==='2026-10-11';
    })).toBe(true));
  });
  it('does not fetch full purchases without PURCHASE_READ (warehouse-only role)',async()=>{
    access.permissions=['INVENTORY_RECEIVE'];
    const {calls}=mockApi();
    renderAt('/purchases');
    expect(screen.getByRole('heading',{name:'Поступления',level:2})).toBeInTheDocument();
    expect(await screen.findByRole('region',{name:'Позиции к приёмке'})).toBeInTheDocument();
    expect(requests(calls,'/api/purchases/overview')).toHaveLength(0);
    expect(requests(calls,'/api/purchases/'+PID)).toHaveLength(0);
    expect(screen.queryByText(/46\s?000/)).not.toBeInTheDocument();
  });
  it('requires only effective custom permissions, never role name',async()=>{
    access.roles=['CUSTOM_DEPARTMENT_123'];
    access.permissions=['PURCHASE_READ'];
    mockApi();renderAt();
    expect(await screen.findByText(/Всего закупок: 1/)).toBeInTheDocument();
    expect(screen.queryByRole('button',{name:'+ Новая закупка'})).not.toBeInTheDocument();
  });
  it('prevents invalid quantities, duplicate variants and invalid currency',()=>{
    expect(integerQuantity('0')).toBeNull();
    expect(integerQuantity('3.5')).toBeNull();
    expect(integerQuantity('999999999999999999999')).toBeNull();
    expect(checkMoney('1.234')).not.toBeNull();
    expect(checkMoney('-1')).not.toBeNull();
    expect(purchaseInput(SID,[], '', '').cargoCost).toBeNull();
    const row={productVariantId:VARIANT,orderedQuantity:1,purchaseUnitCost:'4500.00'};
    expect(validatePurchase({supplierId:SID,items:[row,row],cargoCost:'0',comment:null})).toMatch(/повторяться/);
    expect(validatePurchase({supplierId:SID,items:[],cargoCost:null,comment:null})).toMatch(/хотя бы одну/);
  });
  it('calculates precise subtotals and distinguishes unknown cargo from zero',()=>{
    const unknown=purchasePreview([{quantity:'3',unitCost:'0,29'}],'');
    expect(unknown.goods).toBe(87n);
    expect(unknown.subtotals).toEqual([87n]);
    expect(unknown.cargoCost).toBeNull();
    expect(unknown.total).toBeNull();
    const known=purchasePreview([{quantity:'3',unitCost:'0,29'}],'1.01');
    expect(known.total).toBe(188n);
    expect(purchasePreview([{quantity:'9007199254740991',unitCost:'1.01'}],'0').goods)
      .toBe(909727124728840091n);
    expect(purchasePreview([{quantity:'2',unitCost:'invalid'}],'0').total).toBeNull();
  });
  it('opens focused purchase creation without list/filters and safely restores list on cancel',async()=>{
    const {calls}=mockApi();
    renderAt();
    expect(await screen.findByRole('region',{name:'Список закупок'})).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole('button',{name:'+ Новая закупка'}));
    expect(screen.getByRole('form',{name:'Создание закупки'})).toBeInTheDocument();
    expect(screen.queryByRole('region',{name:'Список закупок'})).not.toBeInTheDocument();
    expect(screen.queryByRole('region',{name:'Фильтры закупок'})).not.toBeInTheDocument();
    await userEvent.setup().click(within(screen.getByRole('form',{name:'Создание закупки'}))
      .getByRole('button',{name:'Отмена'}));
    expect(await screen.findByRole('region',{name:'Список закупок'})).toBeInTheDocument();
    expect(screen.getByRole('region',{name:'Фильтры закупок'})).toBeInTheDocument();
    expect(calls.mock.calls.filter(([url,init])=>String(url)==='/api/purchases'&&
      (init as RequestInit)?.method==='POST')).toHaveLength(0);
  });
  it('searches the full catalog on server and shows real variant name, SKU and exact cost preview',async()=>{
    const {calls}=mockApi();
    renderAt();
    await userEvent.setup().click(await screen.findByRole('button',{name:'+ Новая закупка'}));
    const form=within(screen.getByRole('form',{name:'Создание закупки'}));
    expect(form.getByText('Пока не указано')).toBeInTheDocument();
    await userEvent.setup().click(form.getByRole('button',{name:'Выбрать вариацию'}));
    fireEvent.change(await form.findByRole('searchbox',{name:'Поиск моделей для закупки'}),
      {target:{value:'Ergo'}});
    await waitFor(()=>expect(calls.mock.calls.some(([url])=>{
      const u=new URL(String(url),'http://chairx.local');
      return u.pathname==='/api/products' && u.searchParams.get('search')==='Ergo';
    })).toBe(true));
    fireEvent.change(await form.findByRole('combobox',{name:'Модель для вариации'}),{target:{value:MODEL}});
    await userEvent.setup().click(await form.findByRole('button',{name:/Чёрный.*ERGO-BLK/}));
    expect(await form.findByText(/Выбрано:/)).toHaveTextContent('Ergo Comfort');
    expect(form.getByText(/Выбрано:/)).toHaveTextContent('ERGO-BLK');
    expect(screen.queryByText('UUID: '+VARIANT)).not.toBeInTheDocument();
    fireEvent.change(form.getByRole('textbox',{name:'Количество позиции 1'}),{target:{value:'3'}});
    fireEvent.change(form.getByRole('textbox',{name:'Цена позиции 1'}),{target:{value:'0,29'}});
    expect(form.getByText(/Стоимость позиции 1:/)).toHaveTextContent('0,87 сом');
    expect(form.getByRole('complementary',{name:'Шаг 4. Проверка закупки'}))
      .toHaveTextContent('После указания карго');
    fireEvent.change(form.getByRole('textbox',{name:'Карго закупки'}),{target:{value:'1,01'}});
    expect(form.getByRole('complementary',{name:'Шаг 4. Проверка закупки'}))
      .toHaveTextContent('1,88 сом');
  });
  it('creates a supplier in the purchase drawer without losing items or cargo',async()=>{
    const {calls}=mockApi();
    renderAt();
    await userEvent.setup().click(await screen.findByRole('button',{name:'+ Новая закупка'}));
    const form=within(screen.getByRole('form',{name:'Создание закупки'}));
    fireEvent.change(form.getByRole('textbox',{name:'Количество позиции 1'}),{target:{value:'7'}});
    fireEvent.change(form.getByRole('textbox',{name:'Цена позиции 1'}),{target:{value:'1500'}});
    fireEvent.change(form.getByRole('textbox',{name:'Карго закупки'}),{target:{value:'100'}});
    await userEvent.setup().click(form.getByRole('button',{name:'+ Новый поставщик'}));
    const drawer=screen.getByRole('dialog',{name:'Новый поставщик для закупки'});
    const supplierForm=within(drawer).getByRole('form',{name:'Создание поставщика'});
    fireEvent.change(within(supplierForm).getByRole('textbox',{name:'Название поставщика'}),
      {target:{value:'Шаньдун'}});
    await userEvent.setup().click(within(supplierForm).getByRole('button',{name:'Сохранить поставщика'}));
    await waitFor(()=>expect(screen.queryByRole('dialog',{name:'Новый поставщик для закупки'})).not.toBeInTheDocument());
    expect(form.getByRole('combobox',{name:'Поставщик закупки'})).toHaveValue(RID);
    expect(form.getByRole('textbox',{name:'Количество позиции 1'})).toHaveValue('7');
    expect(form.getByRole('textbox',{name:'Цена позиции 1'})).toHaveValue('1500');
    expect(form.getByRole('textbox',{name:'Карго закупки'})).toHaveValue('100');
    expect(calls.mock.calls.filter(([url,opts])=>String(url)==='/api/suppliers'&&
      (opts as RequestInit)?.method==='POST')).toHaveLength(1);
    expect(calls.mock.calls.filter(([url,opts])=>String(url)==='/api/purchases'&&
      (opts as RequestInit)?.method==='POST')).toHaveLength(0);
  });
  it('hides inline supplier creation without SUPPLIERS_MANAGE',async()=>{
    access.permissions=access.permissions.filter(p=>p!=='SUPPLIERS_MANAGE');
    mockApi();
    renderAt();
    await userEvent.setup().click(await screen.findByRole('button',{name:'+ Новая закупка'}));
    const form=within(screen.getByRole('form',{name:'Создание закупки'}));
    expect(form.queryByRole('button',{name:'+ Новый поставщик'})).not.toBeInTheDocument();
    expect(await form.findByRole('combobox',{name:'Поставщик закупки'})).toBeInTheDocument();
  });
  it('blocks duplicate supplier POST after a potentially committed network failure',async()=>{
    const {calls}=mockApi({networkSupplierCreateOnce:true});
    renderAt();
    await userEvent.setup().click(await screen.findByRole('button',{name:'+ Новая закупка'}));
    await userEvent.setup().click(screen.getByRole('button',{name:'+ Новый поставщик'}));
    const drawer=screen.getByRole('dialog',{name:'Новый поставщик для закупки'});
    const inner=within(drawer).getByRole('form',{name:'Создание поставщика'});
    fireEvent.change(within(inner).getByRole('textbox',{name:'Название поставщика'}),
      {target:{value:'Тестовый поставщик'}});
    await userEvent.setup().click(within(inner).getByRole('button',{name:'Сохранить поставщика'}));
    expect(await within(drawer).findByText(/Повторная отправка заблокирована/)).toBeInTheDocument();
    expect(within(inner).getByRole('button',{name:'Сохранить поставщика'})).toBeDisabled();
    expect(calls.mock.calls.filter(([url,opts])=>String(url)==='/api/suppliers'&&
      (opts as RequestInit)?.method==='POST')).toHaveLength(1);
  });
  it('creates DRAFT from selected real catalog product and variant UUID',async()=>{
    const {calls}=mockApi();
    renderAt();
    await screen.findByRole('button',{name:'+ Новая закупка'});
    await userEvent.setup().click(screen.getByRole('button',{name:'+ Новая закупка'}));
    const form=within(screen.getByRole('form',{name:'Создание закупки'}));
    await screen.findByRole('combobox',{name:'Поставщик закупки'});
    fireEvent.change(form.getByRole('combobox',{name:'Поставщик закупки'}),{target:{value:SID}});
    await userEvent.setup().click(form.getByRole('button',{name:'Выбрать вариацию'}));
    const model=await form.findByRole('combobox',{name:'Модель для вариации'});
    fireEvent.change(model,{target:{value:MODEL}});
    await userEvent.setup().click(await form.findByRole('button',{name:/Чёрный.*ERGO-BLK/}));
    fireEvent.change(form.getByRole('textbox',{name:'Количество позиции 1'}),{target:{value:'10'}});
    fireEvent.change(form.getByRole('textbox',{name:'Цена позиции 1'}),{target:{value:'4500,00'}});
    fireEvent.change(form.getByRole('textbox',{name:'Карго закупки'}),{target:{value:'0'}});
    await userEvent.setup().click(form.getByRole('button',{name:'Сохранить закупку'}));
    expect(await screen.findByRole('heading',{name:'Карточка закупки'})).toBeInTheDocument();
    const post=calls.mock.calls.find(([url,init])=>String(url)==='/api/purchases'&&(init as RequestInit).method==='POST');
    expect(post).toBeTruthy();
    const body=JSON.parse(String((post?.[1] as RequestInit).body)) as {items:Array<{productVariantId:string;purchaseUnitCost:string}>;cargoCost:string};
    expect(body.items[0]).toMatchObject({productVariantId:VARIANT,purchaseUnitCost:'4500.00'});
    expect(body.cargoCost).toBe('0');
  });
});

describe('F06 purchase details and mutations',()=>{
  it('renders read-only factual receipt history and fetches detail only on demand',async()=>{
    const {calls}=mockApi({receiptHistory:true});
    renderAt('/purchases/'+PID);
    const history=await screen.findByRole('region',{name:'История поступлений'});
    expect(await within(history).findByText(/Поступление ·/)).toBeInTheDocument();
    expect(within(history).getByText(/Принял: manager/)).toBeInTheDocument();
    expect(within(history).getByText(/Дом · Принял: manager/)).toBeInTheDocument();
    expect(requests(calls,'/api/purchases/'+PID+'/receipts/'+RID)).toHaveLength(0);
    await userEvent.setup().click(within(history).getByRole('button',{name:'Подробнее о поступлении'}));
    expect(await within(history).findByText(/Стоимость поступивших товаров:/)).toBeInTheDocument();
    expect(within(history).getByText(/27\s?000,00 сом/)).toBeInTheDocument();
    expect(requests(calls,'/api/purchases/'+PID+'/receipts/'+RID)).toHaveLength(1);
    expect(screen.queryByText('UUID: '+RID)).not.toBeInTheDocument();
  });
  it('shows purchase receiving history without inventory API calls when not inventory authorized',async()=>{
    access.permissions=['PURCHASE_READ'];
    const {calls}=mockApi({receiptHistory:true});
    renderAt('/purchases/'+PID);
    const history=await screen.findByRole('region',{name:'История поступлений'});
    expect(await within(history).findByText(/Склад \(подробности недоступны\)/)).toBeInTheDocument();
    expect(requests(calls,'/api/warehouses')).toHaveLength(0);
    expect(requests(calls,'/api/purchases/'+PID+'/receipts')).not.toHaveLength(0);
  });
  it('shows an honest empty state when no receipt has been posted yet',async()=>{
    mockApi();
    renderAt('/purchases/'+PID);
    const history=await screen.findByRole('region',{name:'История поступлений'});
    expect(await within(history).findByText('По этой закупке поступлений пока не было.')).toBeInTheDocument();
  });
  it('shows real product model, variation and SKU from purchase response with no extra catalog lookup',async()=>{
    const {calls}=mockApi();
    renderAt('/purchases/'+PID);
    expect(await screen.findByRole('heading',{name:'Карточка закупки'})).toBeInTheDocument();
    const lines=screen.getByRole('region',{name:'Позиции закупки'});
    expect(within(lines).getByRole('heading',{name:/Ergo Comfort\s*·\s*Чёрный/})).toBeInTheDocument();
    expect(within(lines).getByText('Артикул: ERGO-BLK')).toBeInTheDocument();
    expect(within(lines).getByRole('link',{name:'Открыть в каталоге'}))
      .toHaveAttribute('href','/catalog/'+MODEL);
    expect(within(lines).getByText('Закупочная позиция')).toBeInTheDocument();
    expect(screen.queryByText(/Вариация UUID: /)).not.toBeInTheDocument();
    expect(screen.getAllByText(/4\s?500,00 сом/).length).toBeGreaterThan(0);
    expect(requests(calls,'/api/product-variants/')).toHaveLength(0);
    expect(requests(calls,'/api/products/'+MODEL)).toHaveLength(0);
  });
  it('shows readable line labels but no catalog link to users without CATALOG_READ',async()=>{
    access.permissions=access.permissions.filter(p=>p!=='CATALOG_READ');
    const {calls}=mockApi();
    renderAt('/purchases/'+PID);
    expect(await screen.findByRole('heading',{name:/Ergo Comfort\s*·\s*Чёрный/})).toBeInTheDocument();
    expect(screen.queryByRole('link',{name:'Открыть в каталоге'})).not.toBeInTheDocument();
    expect(requests(calls,'/api/products/')).toHaveLength(0);
  });
  it('edits full DRAFT PUT and keeps all items',async()=>{
    const {calls}=mockApi({purchases:[purchase({status:'DRAFT'})]});
    renderAt('/purchases/'+PID);
    await screen.findByRole('heading',{name:'Карточка закупки'});
    await userEvent.setup().click(screen.getByRole('button',{name:'Редактировать'}));
    const form=within(screen.getByRole('form',{name:'Редактирование закупки'}));
    await form.findByRole('combobox',{name:'Поставщик закупки'});
    fireEvent.change(form.getByRole('textbox',{name:'Комментарий закупки'}),{target:{value:'Обновление'}});
    await userEvent.setup().click(form.getByRole('button',{name:'Сохранить закупку'}));
    expect(await screen.findByText('Черновик обновлён.')).toBeInTheDocument();
    const put=calls.mock.calls.find(([url,init])=>String(url)==='/api/purchases/'+PID&&(init as RequestInit).method==='PUT');
    const body=JSON.parse(String((put?.[1] as RequestInit)?.body)) as {items:unknown[];comment:string;supplierId:string};
    expect(body.items).toHaveLength(1);expect(body.supplierId).toBe(SID);
    expect(body.comment).toBe('Обновление');
  });
  it('does not edit a RECEIVED purchase; cost lock prevents cargo change',async()=>{
    mockApi({purchases:[purchase({status:'RECEIVED',costsLockedAt:TIME})]});
    renderAt('/purchases/'+PID);await screen.findByRole('heading',{name:'Карточка закупки'});
    expect(screen.queryByRole('button',{name:'Редактировать'})).not.toBeInTheDocument();
    expect(screen.queryByRole('button',{name:'Изменить карго'})).not.toBeInTheDocument();
    expect(screen.getByText(/Себестоимость заблокирована/)).toBeInTheDocument();
  });
  it('confirms DRAFT only after confirmation dialog',async()=>{
    const {calls}=mockApi({purchases:[purchase({status:'DRAFT'})]});
    renderAt('/purchases/'+PID);await screen.findByRole('heading',{name:'Карточка закупки'});
    await userEvent.setup().click(screen.getByRole('button',{name:'Подтвердить'}));
    expect(screen.getByRole('alertdialog')).toHaveTextContent('Шэньчжэнь');
    expect(requests(calls,'/confirm')).toHaveLength(0);
    await userEvent.setup().click(within(screen.getByRole('alertdialog')).getByRole('button',{name:'Подтвердить'}));
    expect(await screen.findByText('Закупка подтверждена.')).toBeInTheDocument();
    expect(requests(calls,'/confirm')).toHaveLength(1);
  });
  it('cancels only with confirmation, changes cargo only when unlocked',async()=>{
    const {calls}=mockApi();
    renderAt('/purchases/'+PID);await screen.findByRole('heading',{name:'Карточка закупки'});
    await userEvent.setup().click(screen.getByRole('button',{name:'Изменить карго'}));
    fireEvent.change(screen.getByRole('textbox',{name:'Новая стоимость карго'}),{target:{value:'1500.00'}});
    await userEvent.setup().click(screen.getByRole('button',{name:'Сохранить карго'}));
    expect(await screen.findByText('Стоимость карго обновлена.')).toBeInTheDocument();
    expect(requests(calls,'/cargo')).toHaveLength(1);
    await userEvent.setup().click(screen.getByRole('button',{name:'Отменить закупку'}));
    expect(screen.getByRole('alertdialog')).toHaveTextContent('Отмена допускается');
    expect(requests(calls,'/cancel')).toHaveLength(0);
  });
});

describe('F06 receiving and idempotency',()=>{
  it('opens this purchase in the receiving workflow instead of the general list',async()=>{
    const {calls}=mockApi();
    renderAt('/purchases/'+PID);
    const link=await screen.findByRole('link',{name:'Перейти к поступлениям'});
    expect(link).toHaveAttribute('href','/purchases/receipts?purchaseId='+PID);
    expect(calls.mock.calls.some(([url])=>String(url)==='/api/purchases/'+PID)).toBe(true);
  });
  it('uses the cost-free preselected receiving summary with INVENTORY_RECEIVE and INVENTORY_READ only',async()=>{
    access.permissions=['INVENTORY_RECEIVE','INVENTORY_READ'];
    const {calls}=mockApi();
    renderAt('/purchases/receipts?purchaseId='+PID);
    expect(await screen.findByRole('region',{name:'Форма поступления'})).toBeInTheDocument();
    expect(await screen.findByRole('combobox',{name:'Склад назначения'})).toBeInTheDocument();
    expect(screen.queryByRole('region',{name:'Позиции к приёмке'})).not.toBeInTheDocument();
    expect(screen.queryByRole('link',{name:'Открыть карточку закупки'})).not.toBeInTheDocument();
    expect(calls.mock.calls.filter(([url])=>String(url)==='/api/purchases/'+PID)).toHaveLength(0);
    expect(requests(calls,'/api/purchases/overview')).toHaveLength(0);
    expect(requests(calls,'/api/purchases/receiving?')).toHaveLength(0);
    expect(screen.queryByText(/45\\s?000/)).not.toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole('button',{name:'Закрыть'}));
    expect(await screen.findByRole('region',{name:'Позиции к приёмке'})).toBeInTheDocument();
  });
  it('does not show UUIDs to warehouse-only operators in the receiving worklist',async()=>{
    access.permissions=['INVENTORY_RECEIVE'];
    mockApi();
    renderAt('/purchases/receipts');
    expect(await screen.findByRole('region',{name:'Позиции к приёмке'})).toBeInTheDocument();
    expect(screen.queryByText(PID,{exact:false})).not.toBeInTheDocument();
  });
  it('rejects malformed purchase deep links without fetching unknown purchase data',async()=>{
    const {calls}=mockApi();
    renderAt('/purchases/receipts?purchaseId=not-an-id');
    expect(await screen.findByRole('alert')).toHaveTextContent('Неверная ссылка');
    expect(await screen.findByRole('region',{name:'Позиции к приёмке'})).toBeInTheDocument();
    expect(calls.mock.calls.filter(([url])=>String(url).includes('/api/purchases/not-an-id'))).toHaveLength(0);
  });
  it('warehouse-only role can see cost-free worklist without PURCHASE_READ or other private APIs',async()=>{
    access.permissions=['INVENTORY_RECEIVE'];
    const {calls}=mockApi();
    renderAt('/purchases/receipts');
    expect(await screen.findByText('Ergo Comfort')).toBeInTheDocument();
    expect(screen.getByText(/Осталось: 10/)).toBeInTheDocument();
    expect(requests(calls,'/api/purchases/overview')).toHaveLength(0);
    expect(requests(calls,'/api/purchases/'+PID)).toHaveLength(0);
    expect(requests(calls,'/api/warehouses')).toHaveLength(0);
    await userEvent.setup().click(screen.getAllByRole('button',{name:'Принять'})[0]!);
    expect(await screen.findByRole('alert')).toHaveTextContent(/INVENTORY_READ/);
    expect(requests(calls,'/api/warehouses')).toHaveLength(0);
  });
  it('posts partial receipt by purchaseItemId, selects real active warehouse, never renders response costs',async()=>{
    const {calls,list}=mockApi();
    renderAt('/purchases/receipts');
    await screen.findByText('Ergo Comfort');
    await userEvent.setup().click(screen.getAllByRole('button',{name:'Принять'})[0]!);
    await screen.findByRole('combobox',{name:'Склад назначения'});
    fireEvent.change(screen.getByRole('combobox',{name:'Склад назначения'}),{target:{value:WID}});
    fireEvent.change(screen.getByRole('textbox',{name:'Получено: Ergo Comfort / Чёрный'}),
      {target:{value:'6'}});
    await userEvent.setup().click(screen.getByRole('button',{name:'Провести поступление'}));
    expect(await screen.findByText(/Поступление проведено/)).toBeInTheDocument();
    const post=calls.mock.calls.find(([url,init])=>String(url)==='/api/purchases/'+PID+'/receipts'&&(init as RequestInit).method==='POST');
    expect(post).toBeTruthy();
    const body=JSON.parse(String((post?.[1] as RequestInit).body)) as {items:Array<{purchaseItemId:string;quantity:number}>;idempotencyKey:string};
    expect(body.items).toEqual([{purchaseItemId:ITEM,quantity:6}]);
    expect(body.idempotencyKey).toMatch(/^[0-9a-f]{8}-/);
    expect(list[0]?.status).toBe('PARTIALLY_RECEIVED');
    expect(screen.queryByText('999999.00')).not.toBeInTheDocument();
  });
  it('blocks excess receipt before POST and keeps the entered quantity',async()=>{
    const {calls}=mockApi({initialReceived:6});
    renderAt('/purchases/receipts');await screen.findByText('Ergo Comfort');
    await userEvent.setup().click(screen.getAllByRole('button',{name:'Принять'})[0]!);
    await screen.findByRole('combobox',{name:'Склад назначения'});
    fireEvent.change(screen.getByRole('combobox',{name:'Склад назначения'}),{target:{value:WID}});
    fireEvent.change(screen.getByRole('textbox',{name:'Получено: Ergo Comfort / Чёрный'}),
      {target:{value:'5'}});
    await userEvent.setup().click(screen.getByRole('button',{name:'Провести поступление'}));
    expect(await screen.findByRole('alert')).toHaveTextContent(/превышает остаток/);
    expect(requests(calls,'/receipts')).toHaveLength(0);
  });
  it('network uncertainty locks form and retry uses identical payload/key; server posts only once',async()=>{
    const {calls,receipts,list}=mockApi({networkReceiptOnce:true});
    renderAt('/purchases/receipts');await screen.findByText('Ergo Comfort');
    await userEvent.setup().click(screen.getAllByRole('button',{name:'Принять'})[0]!);
    await screen.findByRole('combobox',{name:'Склад назначения'});
    fireEvent.change(screen.getByRole('combobox',{name:'Склад назначения'}),{target:{value:WID}});
    fireEvent.change(screen.getByRole('textbox',{name:'Получено: Ergo Comfort / Чёрный'}),
      {target:{value:'4'}});
    await userEvent.setup().click(screen.getByRole('button',{name:'Провести поступление'}));
    const retry=await screen.findByRole('button',{name:'Повторить ту же операцию'});
    expect(screen.getByRole('button',{name:'Провести поступление'})).toBeDisabled();
    expect(screen.getByRole('textbox',{name:'Получено: Ergo Comfort / Чёрный'})).toBeDisabled();
    await userEvent.setup().click(retry);
    expect(await screen.findByText(/Поступление проведено/)).toBeInTheDocument();
    const posted=calls.mock.calls.filter(([url,init])=>String(url)==='/api/purchases/'+PID+'/receipts'&&(init as RequestInit).method==='POST');
    expect(posted).toHaveLength(2);
    expect((posted[0]?.[1] as RequestInit).body).toBe((posted[1]?.[1] as RequestInit).body);
    expect(receipts.size).toBe(1);
    expect(list[0]?.items[0]?.receivedQuantity).toBe(4);
  });
  it('can fully receive remaining quantities from partial purchase',async()=>{
    const {list}=mockApi({initialReceived:6});
    renderAt('/purchases/receipts');await screen.findByText('Ergo Comfort');
    await userEvent.setup().click(screen.getAllByRole('button',{name:'Принять'})[0]!);
    await screen.findByRole('combobox',{name:'Склад назначения'});
    fireEvent.change(screen.getByRole('combobox',{name:'Склад назначения'}),{target:{value:WID}});
    fireEvent.change(screen.getByRole('textbox',{name:'Получено: Ergo Comfort / Чёрный'}),
      {target:{value:'4'}});
    await userEvent.setup().click(screen.getByRole('button',{name:'Провести поступление'}));
    expect(await screen.findByText(/Поступление проведено/)).toBeInTheDocument();
    expect(list[0]?.status).toBe('RECEIVED');
    expect(list[0]?.items[0]?.receivedQuantity).toBe(10);
  });
});

describe('F06 suppliers and read-only purchase payments',()=>{
  it('loads supplier records and paginates on server',async()=>{
    const {calls}=mockApi({suppliers:Array.from({length:22},(_,i)=>supplier({
      id:'11111111-1111-4111-8111-'+String(i).padStart(12,'0'),name:'Поставщик '+i}))});
    renderAt('/purchases/suppliers');
    expect(await screen.findByText(/Всего поставщиков: 22/)).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole('button',{name:'Следующая'}));
    expect(await screen.findByText(/Страница 2 из 2/)).toBeInTheDocument();
    expect(requests(calls,'page=1').length).toBeGreaterThan(0);
  });
  it('creates and edits supplier; requires SUPPLIERS_MANAGE for controls',async()=>{
    const {calls}=mockApi();
    renderAt('/purchases/suppliers');await screen.findByText('Шэньчжэнь');
    await userEvent.setup().click(screen.getByRole('button',{name:'+ Добавить поставщика'}));
    const form=within(screen.getByRole('form',{name:'Создание поставщика'}));
    fireEvent.change(form.getByRole('textbox',{name:'Название поставщика'}),{target:{value:'Новый поставщик'}});
    await userEvent.setup().click(form.getByRole('button',{name:'Сохранить поставщика'}));
    expect(await screen.findByText('Поставщик добавлен.')).toBeInTheDocument();
    const post=calls.mock.calls.find(([u,init])=>String(u)==='/api/suppliers'&&(init as RequestInit).method==='POST');
    expect(post).toBeTruthy();
    await userEvent.setup().click(screen.getByRole('button',{name:'Изменить Шэньчжэнь'}));
    const edit=within(screen.getByRole('form',{name:'Редактирование поставщика'}));
    fireEvent.change(edit.getByRole('textbox',{name:'Название поставщика'}),{target:{value:'Китай Центр'}});
    await userEvent.setup().click(edit.getByRole('button',{name:'Сохранить поставщика'}));
    expect(await screen.findByText('Китай Центр')).toBeInTheDocument();
  });
  it('deactivates suppliers only after confirmation',async()=>{
    const {calls}=mockApi();
    renderAt('/purchases/suppliers');await screen.findByText('Шэньчжэнь');
    await userEvent.setup().click(screen.getByRole('button',{name:'Деактивировать Шэньчжэнь'}));
    expect(screen.getByRole('alertdialog')).toHaveTextContent('останется в исторических закупках');
    expect(requests(calls,'/deactivate')).toHaveLength(0);
    await userEvent.setup().click(within(screen.getByRole('alertdialog')).getByRole('button',{name:'Деактивировать'}));
    expect(await screen.findByText('Поставщик деактивирован.')).toBeInTheDocument();
  });
  it('hides supplier mutations without permission',async()=>{
    access.permissions=['SUPPLIERS_READ'];
    mockApi();renderAt('/purchases/suppliers');await screen.findByText('Шэньчжэнь');
    expect(screen.queryByRole('button',{name:'+ Добавить поставщика'})).not.toBeInTheDocument();
    expect(screen.queryByRole('button',{name:'Изменить Шэньчжэнь'})).not.toBeInTheDocument();
  });
  it('shows read-only payment summary by explicit purchase UUID and never posts payments',async()=>{
    const {calls}=mockApi();
    renderAt('/purchases/payments?purchaseId='+PID);
    expect(await screen.findByText('PAY-1',{exact:false})).toBeInTheDocument();
    expect(screen.getByText(/30\s?000,00 сом/)).toBeInTheDocument();
    expect(screen.queryByRole('button',{name:/Создать платёж/})).not.toBeInTheDocument();
    expect(requests(calls,'/api/purchases/'+PID+'/payments')).toHaveLength(1);
    expect(calls.mock.calls.every(([,init])=>(init as RequestInit).method!=='POST')).toBe(true);
  });
  it('can view payments without PURCHASE_READ and without calling purchase list',async()=>{
    access.permissions=['PURCHASE_PAYMENTS_READ'];
    const {calls}=mockApi();
    renderAt('/purchases/payments?purchaseId='+PID);
    expect(await screen.findByText(/Обязательства перед поставщиком/)).toBeInTheDocument();
    expect(requests(calls,'/api/purchases/overview')).toHaveLength(0);
    expect(requests(calls,'/api/purchases?')).toHaveLength(0);
  });
  it('mounts F06.1 payment form with CREATE + READ but without finance or purchase read',async()=>{
    access.permissions=['PURCHASE_PAYMENTS_READ','PURCHASE_PAYMENTS_CREATE'];
    const {calls}=mockApi();
    renderAt('/purchases/payments?purchaseId='+PID);
    expect(await screen.findByRole('form',{name:'Новая оплата закупки'})).toBeInTheDocument();
    expect(screen.getByRole('button',{name:'Проверить и подтвердить оплату'})).toBeInTheDocument();
    expect(requests(calls,'/api/purchases/overview')).toHaveLength(0);
    expect(requests(calls,'/api/finance/accounts')).toHaveLength(0);
    expect(calls.mock.calls.every(([,init])=>(init as RequestInit).method!=='POST')).toBe(true);
  });
  it('keeps payment summary read-only without CREATE permission',async()=>{
    access.permissions=['PURCHASE_PAYMENTS_READ'];
    mockApi();
    renderAt('/purchases/payments?purchaseId='+PID);
    await screen.findByText(/Обязательства перед поставщиком/);
    expect(screen.queryByRole('form',{name:'Новая оплата закупки'})).not.toBeInTheDocument();
  });
  it('does not call restricted payment endpoint without PURCHASE_PAYMENTS_READ',async()=>{
    access.permissions=['PURCHASE_READ'];
    const {calls}=mockApi();
    renderAt('/purchases/payments?purchaseId='+PID);
    expect(screen.getByRole('heading',{name:'Доступ запрещён'})).toBeInTheDocument();
    expect(requests(calls,'/payments')).toHaveLength(0);
  });
});
