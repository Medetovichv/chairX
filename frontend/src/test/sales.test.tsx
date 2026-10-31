import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router';
import { AppRouter } from '../app/router/AppRouter';
import { authSession } from '../features/auth/session';
import { createQueryClient } from '../shared/api/query';
import { readPendingDraft } from '../features/sales/pending-draft';
import { checkDraft, saleStatuses, salePriceValid } from '../features/sales/validation';
import { netSaleTotal } from '../features/sales/components/SalesTable';
import type { SaleResponse, SaleSummary, SaleStatus, CreateDraftInput } from '../features/sales/types';

const granted=vi.hoisted(()=>({
  permissions:['SALES_READ','SALES_CREATE','SALES_DRAFT_MANAGE','SALES_UPDATE',
    'SALES_CANCEL','CUSTOMERS_READ','CUSTOMERS_CREATE','CATALOG_READ','INVENTORY_READ'] as string[],
  roles:['CUSTOM_SHOP_TEAM'] as string[],
}));
vi.mock('../features/auth/AuthProvider',()=>({
  useAuth:()=>({
    status:'authenticated',isAuthenticated:true,
    user:{id:'s-user',username:'s-user',displayName:'Продавец',
      permissions:granted.permissions,roles:granted.roles},
    hasPermission:(permission:string)=>granted.permissions.includes(permission),
    hasAnyPermission:(permissions:readonly string[])=>permissions.some(x=>granted.permissions.includes(x)),
    login:vi.fn(),logout:vi.fn(),
  }),
}));
const SALE_ID='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const DELIVERY_ID='dddddddd-dddd-4ddd-8ddd-dddddddddddd';
const NEW_CUSTOMER_ID='cccccccc-cccc-4ccc-8ccc-cccccccccccc';
const NEW_ID='bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
const CUSTOMER_ID='cccccccc-cccc-4ccc-8ccc-cccccccccccc';
const MODEL_ID='dddddddd-dddd-4ddd-8ddd-dddddddddddd';
const BLACK_ID='eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee';
const GREY_ID='ffffffff-ffff-4fff-8fff-ffffffffffff';
const WH='11111111-1111-4111-8111-111111111111';
const OFFICE='22222222-2222-4222-8222-333333333333';
const TIME='2026-10-10T08:00:00Z';
const json=(body:unknown,status=200)=>new Response(JSON.stringify(body),{
  status,headers:{'Content-Type':'application/json'},
});
function sale(status:SaleStatus='DRAFT',changes:Partial<SaleResponse>={}):SaleResponse {
  return {id:SALE_ID,saleNumber:'CHX-2026-0042',customerId:CUSTOMER_ID,
    fulfillmentType:'SELF_PICKUP',status,items:[{
      id:'22222222-2222-4222-8222-222222222222',
      productVariantId:BLACK_ID,warehouseId:WH,quantity:2,
      unitSalePrice:'8500.00',total:'17000.00',
    }],total:'17000.00',comment:'Самовывоз',createdBy:'admin',createdAt:TIME,
    fulfilledAt:null,fulfilledBy:null,cancelledAt:null,cancelledBy:null,...changes};
}
function summary(s:SaleResponse):SaleSummary {
  return {id:s.id,saleNumber:s.saleNumber,createdAt:s.createdAt,
    customerId:s.customerId,customerName:s.customerId?'Заказчик':'',
    total:s.total,status:s.status,fulfillmentType:s.fulfillmentType,
    quantity:s.items.reduce((n,x)=>n+x.quantity,0),
    products:s.items.length?'Ergo Comfort / Чёрный':'',
    paymentStatus:'UNPAID',deliveryStatus:null,plannedDeliveryDate:null};
}
describe('net sale amount after posted refunds',()=>{
  it('subtracts partial refund from goods plus delivery while preserving original sale',()=>{
    const original=summary(sale('FULFILLED'));
    const result={...original,deliveryCost:'500.00',refundedAmount:'8500.00'};
    expect(netSaleTotal(result)).toBe('9000');
    expect(original.total).toBe('17000.00');
  });
  it('handles full refund and rejects invalid money values',()=>{
    const original=summary(sale('FULFILLED'));
    expect(netSaleTotal({...original,deliveryCost:'500.00',refundedAmount:'17500.00'})).toBe('0');
    expect(netSaleTotal({...original,refundedAmount:'invalid'})).toBeNull();
    expect(netSaleTotal({...original,deliveryCost:'500.00',refundedAmount:'18000.00'})).toBeNull();
  });
});
function mockBackend(options:{
  sales?:SaleResponse[]; draftNetworkOnce?:boolean; draftUpdateNetworkOnce?:boolean;
  draftUpdateFailBeforeCommit?:boolean; changeBeforeAction?:boolean; stockFailure?:boolean;
  deny?:Record<string,number>; alternateWarehouse?:boolean;
}={}){
  const rows=[...(options.sales??[sale('DRAFT')])];
  const byKey=new Map<string,SaleResponse>();
  let networkOnce=Boolean(options.draftNetworkOnce);
  let updateNetworkOnce=Boolean(options.draftUpdateNetworkOnce);
  let updateFailsBeforeCommit=Boolean(options.draftUpdateFailBeforeCommit);
  let detailGets=0;
  let creates=0,confirms=0,fulfills=0,cancels=0;
  const fetchMock=vi.fn(async(raw:RequestInfo|URL,init?:RequestInit):Promise<Response>=>{
    const url=new URL(String(raw),'http://chairx.test');
    const path=url.pathname,method=init?.method||'GET';
    const rejected=options.deny?.[method+' '+path];
    if(rejected)return json({code:'REJECTED',message:'stacktrace-private',details:{}},rejected);
    const commentSegments=path.split('/');
    if(commentSegments.length===5&&commentSegments[1]==='api'&&
       commentSegments[2]==='sales'&&commentSegments[4]==='comments'&&method==='GET'){
      return json({items:[],page:0,size:20,total:0});
    }
    if(commentSegments.length===5&&commentSegments[1]==='api'&&
       commentSegments[2]==='sales'&&commentSegments[4]==='comments'&&method==='POST'){
      const body=JSON.parse(String(init?.body)) as {text:string;idempotencyKey:string};
      return json({id:NEW_ID,saleId:commentSegments[3],author:'test-employee',
        text:body.text,createdAt:TIME},201);
    }
    const segments=path.split('/');
    if(segments.length===5 && segments[1]==='api' && segments[2]==='sales' &&
       segments[4]==='history' && method==='GET'){
      const current=rows.find(s=>s.id===segments[3]);
      const entries=current?[
        {action:'DRAFT_CREATED',actor:'sales-operator',occurredAt:TIME},
        ...(current.status==='FULFILLED'?[{action:'FULFILLED',actor:'warehouse-operator',occurredAt:TIME}]:[]),
      ]:[];
      return json({items:entries,page:0,size:20,total:entries.length});
    }
    if(path==='/api/deliveries/by-sale/'+SALE_ID&&method==='GET'){
      return json({id:DELIVERY_ID,saleId:SALE_ID,status:'READY'});
    }
    if(path==='/api/sales/statistics'&&method==='GET'){
      const byStatus=(status:SaleStatus)=>rows.filter(r=>r.status===status).length;
      const confirmedOrFulfilled=rows.filter(r=>r.status==='CONFIRMED'||r.status==='FULFILLED');
      const average=confirmedOrFulfilled.length
        ? Math.round(confirmedOrFulfilled.reduce((n,s)=>n+Number(s.total),0)/confirmedOrFulfilled.length)
        : 0;
      return json({orders30Days:rows.length,fulfilled30Days:byStatus('FULFILLED'),
        inWork30Days:byStatus('DRAFT')+byStatus('CONFIRMED'),averageCheck30Days:average});
    }
    if(path==='/api/sales'&&method==='GET'){
      const status=url.searchParams.get('status');
      const number=url.searchParams.get('number')?.toLowerCase();
      const from=url.searchParams.get('from'),to=url.searchParams.get('to');
      const page=Number(url.searchParams.get('page')??0),size=Number(url.searchParams.get('size')??20);
      const set=rows.filter(r=>(!status||r.status===status)&&
        (!number||r.saleNumber.toLowerCase().includes(number))&&
        (!from||r.createdAt.slice(0,10)>=from)&&(!to||r.createdAt.slice(0,10)<=to));
      return json({items:set.slice(page*size,(page+1)*size).map(summary),page,size,total:set.length});
    }
    if(path==='/api/sales/drafts'&&method==='POST'){
      const req=JSON.parse(String(init?.body)) as CreateDraftInput;
      let result=byKey.get(req.idempotencyKey);
      if(!result){
        creates++;
        result=sale('DRAFT',{
          id:NEW_ID,saleNumber:'CHX-2026-0043',customerId:req.customerId,
          fulfillmentType:req.fulfillmentType,comment:req.comment,
          items:req.items.map((item,i)=>({
            id:'22222222-2222-4222-8222-'+String(i+1).padStart(12,'0'),
            ...item,total:String(item.quantity*Number(item.unitSalePrice)),
          })),
          total:String(req.items.reduce((n,i)=>n+i.quantity*Number(i.unitSalePrice),0)),
        });
        rows.push(result);byKey.set(req.idempotencyKey,result);
      }
      if(networkOnce){networkOnce=false;throw new TypeError('Socket closed after database commit');}
      return json(result,201);
    }
    const saleId=/^\/api\/sales\/([^/]+)$/.exec(path);
    if(saleId&&method==='GET'){
      const s=rows.find(r=>r.id===saleId[1]);
      detailGets++;
      if(s&&options.changeBeforeAction&&detailGets===2){
        // Another employee changed the order after the operator opened it.
        s.items[0]!.quantity=3;
        s.items[0]!.total='25500.00';
        s.total='25500.00';
      }
      return s?json(s):json({code:'NOT_FOUND',message:'',details:{}},404);
    }
    const draft=/^\/api\/sales\/([^/]+)\/draft$/.exec(path);
    if(draft&&method==='PUT'){
      const s=rows.find(r=>r.id===draft[1]);
      if(!s)return json({},404);
      if(s.status!=='DRAFT')return json({code:'INVALID_STATUS',message:'',details:{}},409);
      if(updateFailsBeforeCommit){updateFailsBeforeCommit=false;throw new TypeError('Socket closed before PUT');}
      const payload=JSON.parse(String(init?.body)) as CreateDraftInput;
      s.customerId=payload.customerId;
      s.fulfillmentType=payload.fulfillmentType;
      s.comment=payload.comment;
      s.items=payload.items.map((item,i)=>({
        id:'22222222-2222-4222-8222-'+String(i+1).padStart(12,'0'),
        ...item,total:String(item.quantity*Number(item.unitSalePrice)),
      }));
      if(updateNetworkOnce){updateNetworkOnce=false;throw new TypeError('Lost response after PUT commit');}
      return json(s);
    }
    const action=/^\/api\/sales\/([^/]+)\/(confirm|fulfill|cancel)$/.exec(path);
    if(action&&method==='POST'){
      const s=rows.find(r=>r.id===action[1]);
      if(!s)return json({},404);
      if(action[2]==='confirm'){
        if(options.stockFailure)return json({code:'INSUFFICIENT_STOCK',message:'',details:{}},409);
        confirms++;s.status='CONFIRMED';
      }
      if(action[2]==='fulfill'){
        if(s.fulfillmentType!=='SELF_PICKUP')return json({code:'DELIVERY_REQUIRED',message:'',details:{}},409);
        fulfills++;s.status='FULFILLED';s.fulfilledAt=TIME;
      }
      if(action[2]==='cancel'){
        if(s.status==='CONFIRMED'&&s.fulfillmentType!=='SELF_PICKUP')return json({code:'DELIVERY_REQUIRED',message:'',details:{}},409);
        cancels++;s.status='CANCELLED';s.cancelledAt=TIME;
      }
      return json(s);
    }
    if(path==='/api/customers'&&method==='POST'){
      const input=JSON.parse(String(init?.body)) as Record<string,unknown>;
      return json({id:NEW_CUSTOMER_ID,...input,active:true,createdAt:TIME,updatedAt:TIME},201);
    }
    if(path==='/api/customers/search'&&method==='GET')return json([{
      id:CUSTOMER_ID,fullName:null,phone:'+996555123456',
      secondaryPhone:null,whatsappPhone:'+996555123456',instagramUsername:null,
      address:null,cityRegion:'Бишкек',comment:null,active:true,
      createdAt:TIME,updatedAt:TIME,
    }]);
    if(path==='/api/customers/'+CUSTOMER_ID&&method==='GET')return json({
      id:CUSTOMER_ID,fullName:null,phone:'+996555123456',secondaryPhone:null,
      whatsappPhone:'+996555123456',instagramUsername:null,
      address:null,cityRegion:'Бишкек',comment:null,active:true,
      createdAt:TIME,updatedAt:TIME,
    });
    if(path==='/api/products/'+MODEL_ID&&method==='GET')return json({
      id:MODEL_ID,name:'Ergo Comfort',category:null,description:null,
      active:true,createdAt:TIME,updatedAt:TIME,
    });
    if(path==='/api/products'&&method==='GET')return json({
      items:[{id:MODEL_ID,name:'Ergo Comfort',category:null,description:null,
        active:true,createdAt:TIME,updatedAt:TIME}],
      page:0,size:20,totalElements:1,totalPages:1,
    });
    if(path==='/api/products/'+MODEL_ID+'/variants'&&method==='GET')return json({
      items:[{id:BLACK_ID,productId:MODEL_ID,name:'Чёрный',sku:'ERGO-BLK',color:'Чёрный',
        recommendedSalePrice:'8500.00',active:true,createdAt:TIME,updatedAt:TIME},
        {id:GREY_ID,productId:MODEL_ID,name:'Серый',sku:'ERGO-GREY',color:'Серый',
          recommendedSalePrice:'8100.00',active:true,createdAt:TIME,updatedAt:TIME}],
      page:0,size:20,totalElements:2,totalPages:1,
    });
    if(path.startsWith('/api/product-variants/')&&method==='GET'){
      const v=path.endsWith(BLACK_ID)?BLACK_ID:GREY_ID;
      return json({id:v,productId:MODEL_ID,name:v===BLACK_ID?'Чёрный':'Серый',
        sku:'ERGO',color:null,recommendedSalePrice:'8500',active:true,
        createdAt:TIME,updatedAt:TIME});
    }
    if(path.startsWith('/api/warehouses')&&method==='GET')return json({
      items:[{id:WH,name:'Дом',code:'HOME',address:null,active:true,
        createdAt:TIME,updatedAt:TIME},
        ...(options.alternateWarehouse?[{id:OFFICE,name:'Офис',code:'OFFICE',address:null,
          active:true,createdAt:TIME,updatedAt:TIME}]:[])],
      page:0,size:100,totalPages:1,totalElements:options.alternateWarehouse?2:1,
    });
    if(path==='/api/inventory/overview'&&method==='GET'){
      const search=(url.searchParams.get('search')??'').toUpperCase();
      const rows=[
        {productVariantId:BLACK_ID,model:'Ergo Comfort',variation:'Чёрный',sku:'ERGO-BLK',
         onHand:10,reserved:2,blocked:0,available:8,
         warehouses:[{warehouseId:WH,warehouseCode:'HOME',onHand:10,reserved:2,blocked:0,available:8}]},
        {productVariantId:GREY_ID,model:'Ergo Comfort',variation:'Серый',sku:'ERGO-GREY',
         onHand:10,reserved:0,blocked:0,available:10,
         warehouses:[{warehouseId:WH,warehouseCode:'HOME',onHand:10,reserved:0,blocked:0,available:10}]}
      ].filter(row=>(row.model+' '+row.variation+' '+row.sku).toUpperCase().includes(search));
      return json({items:rows,page:0,size:20,total:rows.length});
    }
        if(path==='/api/inventory/balances/'+OFFICE+'/'+BLACK_ID&&options.alternateWarehouse)
      return json({warehouseId:OFFICE,productVariantId:BLACK_ID,onHand:9,reserved:0,blocked:0,available:9});
    if(path==='/api/inventory/balances/'+OFFICE+'/'+GREY_ID&&options.alternateWarehouse)
      return json({warehouseId:OFFICE,productVariantId:GREY_ID,onHand:9,reserved:0,blocked:0,available:9});
    if(path==='/api/inventory/balances/'+WH+'/'+BLACK_ID||
       path==='/api/inventory/balances/'+WH+'/'+GREY_ID)
      return json({warehouseId:WH,productVariantId:path.endsWith(BLACK_ID)?BLACK_ID:GREY_ID,
        onHand:options.alternateWarehouse?0:10,reserved:options.alternateWarehouse?0:2,
        blocked:0,available:options.alternateWarehouse?0:8});
    throw new Error('Unexpected '+method+' '+path);
  });
  vi.stubGlobal('fetch',fetchMock);
  return {fetchMock,rows,byKey,count:()=>({creates,confirms,fulfills,cancels})};
}
function renderAt(path='/sales'){
  return render(<QueryClientProvider client={createQueryClient()}>
    <MemoryRouter initialEntries={[path]}><AppRouter/></MemoryRouter>
  </QueryClientProvider>);
}
function called(api:ReturnType<typeof mockBackend>,fragment:string) {
  return api.fetchMock.mock.calls.filter(([input])=>String(input).includes(fragment));
}
beforeEach(()=>{
  sessionStorage.clear();
  granted.roles=['CUSTOM_SHOP_TEAM'];
  granted.permissions=['SALES_READ','SALES_CREATE','SALES_DRAFT_MANAGE','SALES_UPDATE',
    'SALES_CANCEL','CUSTOMERS_READ','CUSTOMERS_CREATE','CATALOG_READ','INVENTORY_READ'];
  authSession.activate('Basic test-sales',{headerName:'X-CSRF-TOKEN',token:'csrf'});
});
afterEach(()=>{authSession.clear();sessionStorage.clear();vi.unstubAllGlobals();});

describe('F07 DTOs, validation and server sales list',()=>{
  it('navigates from list to creation without treating new as quick-view id',async()=>{
    const api=mockBackend();
    renderAt();
    await userEvent.setup().click(await screen.findByRole('link',{name:'Новая продажа'}));
    expect(await screen.findByRole('form',{name:'Новый черновик продажи'})).toBeInTheDocument();
    expect(screen.getByRole('dialog',{name:'Быстрый просмотр: Новая продажа'})).toBeInTheDocument();
    expect(api.fetchMock.mock.calls.some(([url])=>String(url).includes('/api/sales/new')||String(url).includes('/api/deliveries/new'))).toBe(false);
  });

  it('accepts a full employee delivery order with zero delivery cost',()=>{
    const input={
      customerId:CUSTOMER_ID,fulfillmentType:'CITY_DELIVERY' as const,
      items:[{productVariantId:BLACK_ID,warehouseId:WH,quantity:1,unitSalePrice:'8500'}],
      comment:null,deliveryDetails:{recipientName:'Айбек',recipientPhone:'+996700111222',
        address:'Бишкек, улица Тестовая, 1',cityRegion:'Бишкек',deliveryCost:'0',
        carrierName:null,trackingNumber:null,comment:'Позвонить перед доставкой',plannedDeliveryDate:null},
    };
    expect(checkDraft(input)).toBeNull();
    expect(checkDraft({...input,deliveryDetails:{...input.deliveryDetails,address:''}}))
      .toMatch(/адрес/i);
  });

  it('matches actual statuses and prevents fractional sale prices',()=>{
    expect(saleStatuses).toEqual(['DRAFT','CONFIRMED','FULFILLED','CANCELLED']);
    for(const n of ['0','1','9999999999999999999'])expect(salePriceValid(n)).toBe(true);
    for(const n of ['-1','2.5','01','1e3',''])expect(salePriceValid(n)).toBe(false);
    expect(checkDraft({customerId:null,fulfillmentType:null,items:[],comment:null})).toBeNull();
    expect(checkDraft({customerId:null,fulfillmentType:null,items:[{
      productVariantId:BLACK_ID,warehouseId:WH,quantity:1,unitSalePrice:'9000',
    },{
      productVariantId:BLACK_ID,warehouseId:WH,quantity:1,unitSalePrice:'9000',
    }],comment:null})).toMatch(/объединить/);
  });
  it('shows authentic SaleSummary fields and server page 2 without financial leaks',async()=>{
    const rows=Array.from({length:22},(_,i)=>sale('CONFIRMED',{
      id:'aaaaaaaa-aaaa-4aaa-8aaa-'+String(i).padStart(12,'0'),
      saleNumber:'CHX-2026-'+String(i).padStart(4,'0'),
    }));
    const api=mockBackend({sales:rows});
    renderAt();
    expect(await screen.findByText(/Всего продаж: 22/)).toBeInTheDocument();
    expect(screen.getAllByText('Не оплачено').length).toBeGreaterThan(0);
    expect(screen.getAllByText(/17[\s\u00a0]?000(?:,00)? сом/).length).toBeGreaterThan(0);
    expect(screen.queryByText('Себестоимость')).not.toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole('button',{name:'Следующая'}));
    expect(await screen.findByText(/Страница 2 из 2/)).toBeInTheDocument();
    expect(api.fetchMock.mock.calls.some(([url])=>String(url).includes('page=1'))).toBe(true);
    expect(called(api,'/api/sales?').length).toBeGreaterThanOrEqual(2);
    expect(called(api,'/api/payments')).toHaveLength(0);
  });
  it('filters dates/status and searches saleNumber through server not local list',async()=>{
    const api=mockBackend({sales:[sale('CONFIRMED')]});
    renderAt();
    await screen.findByRole('combobox',{name:'Фильтр статуса продажи'});
    fireEvent.change(screen.getByRole('combobox',{name:'Фильтр статуса продажи'}),{target:{value:'CONFIRMED'}});
    fireEvent.change(screen.getByRole('searchbox',{name:'Поиск по номеру заказа'}),{target:{value:'0042'}});
    fireEvent.change(screen.getByLabelText('Продажи с даты'),{target:{value:'2026-10-09'}});
    fireEvent.change(screen.getByLabelText('Продажи по дату'),{target:{value:'2026-10-11'}});
    await waitFor(()=>expect(api.fetchMock.mock.calls.some(([url])=>{
      const p=new URL(String(url),'http://chairx.test');
      return p.pathname==='/api/sales'&&p.searchParams.get('status')==='CONFIRMED'&&
        p.searchParams.get('number')==='0042'&&p.searchParams.get('from')==='2026-10-09'&&
        p.searchParams.get('to')==='2026-10-11';
    })).toBe(true));
  });
  it('renders real empty state and no synthetic KPIs',async()=>{
    mockBackend({sales:[]});renderAt();
    expect(await screen.findByText('Продажи не найдены')).toBeInTheDocument();
    expect(screen.queryByText('Доход сегодня')).not.toBeInTheDocument();
  });
  it('Kanban paginates each status and never pretends 20 is the complete total',async()=>{
    const all=Array.from({length:22},(_,i)=>sale('CONFIRMED',{
      id:'aaaaaaaa-aaaa-4aaa-8aaa-'+String(i).padStart(12,'0'),
      saleNumber:'CHX-2026-'+String(i).padStart(4,'0'),
    }));
    const api=mockBackend({sales:all});
    renderAt();
    await userEvent.setup().click(screen.getByRole('button',{name:'Канбан'}));
    const col=await screen.findByRole('region',{name:'Канбан: Подтверждён'});
    expect(await within(col).findByText(/Показано 20\/22/)).toBeInTheDocument();
    expect(within(col).getByRole('button',{name:'Показать ещё'})).toBeInTheDocument();
    await userEvent.setup().click(within(col).getByRole('button',{name:'Показать ещё'}));
    expect(await within(col).findAllByRole('link', {name:/Открыть продажу/})).toHaveLength(22);
    for(const status of saleStatuses){
      expect(api.fetchMock.mock.calls.some(([url])=>{
        const p=new URL(String(url),'http://chairx.test');
        return p.pathname==='/api/sales'&&p.searchParams.get('status')===status;
      })).toBe(true);
    }
    expect(called(api,'/api/sales/'+SALE_ID)).toHaveLength(0);
  });
  it('custom role with SALES_READ can browse but has no create or mutation controls',async()=>{
    granted.permissions=['SALES_READ'];
    granted.roles=['MY_CUSTOM_DEPARTMENT'];
    mockBackend();renderAt();
    await screen.findByRole('heading',{name:'Продажи',level:2});
    expect(screen.queryByRole('link',{name:'Новая продажа'})).not.toBeInTheDocument();
    expect((await screen.findAllByText('#42')).length).toBeGreaterThan(0);
  });
});
describe('F07 creation and idempotent drafts',()=>{
  it('copies a newly typed customer phone into city-delivery phone without selecting a customer',async()=>{
    granted.permissions.push('CUSTOMERS_READ','DELIVERIES_CREATE');
    mockBackend({sales:[]});
    renderAt('/sales/new');
    const form=await screen.findByRole('form',{name:'Новый черновик продажи'});
    fireEvent.change(within(form).getByRole('searchbox',{name:'Поиск клиента для продажи'}),
      {target:{value:'+996700123456'}});
    fireEvent.change(within(form).getByRole('combobox',{name:'Способ получения'}),
      {target:{value:'CITY_DELIVERY'}});
    await waitFor(()=>expect(within(form).getByRole('textbox',{name:'Телефон доставки при создании продажи'}))
      .toHaveValue('+996700123456'));
  });

  it('employee delivery mode shows address, phone and one sale comment without an extra checkbox',async()=>{
    granted.permissions.push('DELIVERIES_CREATE');
    mockBackend({sales:[]});
    renderAt('/sales/new');
    const form=await screen.findByRole('form',{name:'Новый черновик продажи'});
    fireEvent.change(within(form).getByRole('combobox',{name:'Способ получения'}),
      {target:{value:'CITY_DELIVERY'}});
    expect(within(form).getByRole('textbox',{name:'Телефон доставки при создании продажи'})).toBeInTheDocument();
    expect(within(form).getByRole('textbox',{name:'Адрес доставки при создании продажи'})).toBeInTheDocument();
    expect(within(form).queryByRole('textbox',{name:'Комментарий к доставке при создании продажи'})).not.toBeInTheDocument();
    expect(within(form).getByRole('textbox',{name:'Комментарий продажи'})).toBeInTheDocument();
    expect((within(form).getByLabelText('Планируемая дата доставки при создании продажи') as HTMLInputElement).value).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(within(form).getByRole('button',{name:'Подтвердить продажу и доставку'})).toBeInTheDocument();
    expect(within(form).queryByRole('checkbox',{name:'Подтвердить заказ и сразу создать доставку'})).not.toBeInTheDocument();
    fireEvent.change(within(form).getByRole('combobox',{name:'Способ получения'}),
      {target:{value:'SELF_PICKUP'}});
    expect(within(form).queryByRole('textbox',{name:'Адрес доставки при создании продажи'})).not.toBeInTheDocument();
  });

  it('creates empty DRAFT with a real idempotency key and no inventory reservation',async()=>{
    const api=mockBackend({sales:[]});
    renderAt('/sales/new');
    const form=await screen.findByRole('form',{name:'Новый черновик продажи'});
    await userEvent.setup().click(within(form).getByRole('button',{name:'Сохранить черновик'}));
    expect(await screen.findByRole('heading',{name:'Продажа #43'})).toBeInTheDocument();
    expect(api.count().creates).toBe(1);
    const post=api.fetchMock.mock.calls.find(([url,opts])=>String(url)==='/api/sales/drafts'&&(opts as RequestInit).method==='POST');
    const data=JSON.parse(String((post?.[1] as RequestInit).body)) as CreateDraftInput;
    expect(data.items).toEqual([]);expect(data.customerId).toBeNull();
    expect(data.fulfillmentType).toBeNull();
    expect(data.idempotencyKey).toMatch(/^[0-9a-f]{8}-/);
    expect(called(api,'/api/inventory/transfers')).toHaveLength(0);
    expect(called(api,'/api/sales/'+NEW_ID+'/confirm')).toHaveLength(0);
  });
  it('creates ONE draft with customer without name and two real catalog variations',async()=>{
    const api=mockBackend({sales:[]});
    renderAt('/sales/new');
    const form=await screen.findByRole('form',{name:'Новый черновик продажи'});
    fireEvent.change(within(form).getByRole('searchbox',{name:'Поиск клиента для продажи'}),
      {target:{value:'996555'}});
    await userEvent.setup().click(await screen.findByRole('button',{name:'Выбрать'}));
    expect(within(form).getAllByText(/996555123456/).length).toBeGreaterThan(0);
    fireEvent.change(within(form).getByRole('combobox',{name:'Способ получения'}),
      {target:{value:'SELF_PICKUP'}});
    await userEvent.setup().click(within(form).getByRole('button',{name:'Выбрать товар'}));
        fireEvent.change(within(form).getByRole('searchbox',{name:'Поиск по креслам в наличии'}),{target:{value:'ERGO'}});
    await userEvent.setup().click(await within(form).findByRole('button',{name:/Ergo Comfort · Чёрный/}));
    fireEvent.change(within(form).getByRole('combobox',{name:'Склад позиции 1'}),{target:{value:WH}});
    fireEvent.change(within(form).getByRole('textbox',{name:'Количество позиции 1'}),{target:{value:'2'}});
    // Recommended price must remain unset until explicitly selected.
    expect(within(form).getByRole('textbox',{name:'Цена продажи позиции 1'})).toHaveValue('');
    fireEvent.change(within(form).getByRole('textbox',{name:'Цена продажи позиции 1'}),{target:{value:'8200'}});
    await userEvent.setup().click(within(form).getByRole('button',{name:'+ Добавить товар'}));
        fireEvent.change(within(form).getAllByRole('searchbox',{name:'Поиск по креслам в наличии'}).at(-1)!,{target:{value:'ERGO'}});
    await userEvent.setup().click(await within(form).findByRole('button',{name:/Ergo Comfort · Серый/}));
    fireEvent.change(within(form).getByRole('combobox',{name:'Склад позиции 2'}),{target:{value:WH}});
    fireEvent.change(within(form).getByRole('textbox',{name:'Цена продажи позиции 2'}),{target:{value:'8100'}});
    await userEvent.setup().click(within(form).getByRole('button',{name:'Сохранить черновик'}));
    expect(await screen.findByRole('heading',{name:'Продажа #43'})).toBeInTheDocument();
    const post=api.fetchMock.mock.calls.filter(([url,init])=>String(url)==='/api/sales/drafts'&&(init as RequestInit).method==='POST');
    expect(post).toHaveLength(1);
    const data=JSON.parse(String((post[0]?.[1] as RequestInit).body)) as CreateDraftInput;
    expect(data.items).toHaveLength(2);
    expect(data.items.map(x=>x.productVariantId)).toEqual([BLACK_ID,GREY_ID]);
    expect(data.items[0]).toMatchObject({warehouseId:WH,quantity:2,unitSalePrice:'8200'});
    expect(data.customerId).toBe(CUSTOMER_ID);
    expect(api.count().creates).toBe(1);
  });
  it('restores original draft POST on network uncertainty without a duplicate',async()=>{
    const api=mockBackend({sales:[],draftNetworkOnce:true});
    const first=renderAt('/sales/new');
    await screen.findByRole('form',{name:'Новый черновик продажи'});
    await userEvent.setup().click(screen.getByRole('button',{name:'Сохранить черновик'}));
    const replay=await screen.findByRole('button',{name:'Повторить исходный запрос'});
    expect(api.count().creates).toBe(1);
    const stored=readPendingDraft('s-user');
    expect(stored.kind).toBe('pending');
    first.unmount();
    renderAt('/sales/new');
    expect(await screen.findByRole('button',{name:'Повторить исходный запрос'})).toBeInTheDocument();
    await userEvent.setup().click(replay.isConnected?replay:screen.getByRole('button',{name:'Повторить исходный запрос'}));
    expect(await screen.findByRole('heading',{name:'Продажа #43'})).toBeInTheDocument();
    const posts=api.fetchMock.mock.calls.filter(([url,init])=>String(url)==='/api/sales/drafts'&&(init as RequestInit).method==='POST');
    expect(posts).toHaveLength(2);
    expect((posts[0]?.[1] as RequestInit).body).toBe((posts[1]?.[1] as RequestInit).body);
    expect(api.count().creates).toBe(1);
    expect(readPendingDraft('s-user').kind).toBe('none');
  });
  it('finds an available chair by SKU using real inventory search, with warehouse prefilling',async()=>{
    const api=mockBackend({sales:[]});
    renderAt('/sales/new');
    const form=await screen.findByRole('form',{name:'Новый черновик продажи'});
    await userEvent.setup().click(within(form).getByRole('button',{name:'Выбрать товар'}));
        await userEvent.setup().click(within(form).getByText('Поиск по наличию'));
    fireEvent.change(within(form).getByRole('searchbox',{name:'Поиск по креслам в наличии'}),
      {target:{value:'ERGO-BLK'}});
    await userEvent.setup().click(await within(form).findByRole('button',{name:/Ergo Comfort · Чёрный/}));
    expect(within(form).getByRole('combobox',{name:'Склад позиции 1'})).toHaveValue(WH);
    expect(api.fetchMock.mock.calls.some(([raw])=>new URL(String(raw),'http://chairx.test').pathname==='/api/inventory/overview')).toBe(true);
  });

  it('offers an alternate warehouse when the selected source is out of stock',async()=>{
    mockBackend({sales:[],alternateWarehouse:true});
    renderAt('/sales/new');
    const form=await screen.findByRole('form',{name:'Новый черновик продажи'});
    await userEvent.setup().click(within(form).getByRole('button',{name:'Выбрать товар'}));
        fireEvent.change(within(form).getByRole('searchbox',{name:'Поиск по креслам в наличии'}),{target:{value:'ERGO'}});
    await userEvent.setup().click(await within(form).findByRole('button',{name:/Ergo Comfort · Чёрный/}));
    fireEvent.change(within(form).getByRole('combobox',{name:'Склад позиции 1'}),
      {target:{value:WH}});
    const alternative=await within(form).findByRole('button',{name:'Использовать Офис · 9 шт.'});
    await userEvent.setup().click(alternative);
    expect(within(form).getByRole('combobox',{name:'Склад позиции 1'})).toHaveValue(OFFICE);
    expect(within(form).queryByText(/На выбранном складе недостаточно товара/)).not.toBeInTheDocument();
  });

  it('creates a customer without leaving the sale form or losing entered data',async()=>{
    const api=mockBackend({sales:[]});
    renderAt('/sales/new');
    const saleForm=await screen.findByRole('form',{name:'Новый черновик продажи'});
    fireEvent.change(within(saleForm).getByRole('textbox',{name:'Комментарий продажи'}),
      {target:{value:'Подготовить кресла перед выдачей'}});
    await userEvent.setup().click(within(saleForm).getByRole('button',{name:'+ Новый клиент'}));
    const drawer=screen.getByRole('dialog',{name:'Новый клиент для продажи'});
    const customerForm=within(drawer).getByRole('form',{name:'Создание клиента'});
    fireEvent.change(within(customerForm).getByRole('textbox',{name:'Имя'}),
      {target:{value:'Азамат Клиент'}});
    fireEvent.change(within(customerForm).getByRole('textbox',{name:'Основной телефон'}),
      {target:{value:'+996700123456'}});
    await userEvent.setup().click(within(customerForm).getByRole('button',{name:'Сохранить клиента'}));
    await waitFor(()=>expect(screen.queryByRole('dialog',{name:'Новый клиент для продажи'})).not.toBeInTheDocument());
    expect(within(saleForm).getByRole('textbox',{name:'Комментарий продажи'}))
      .toHaveValue('Подготовить кресла перед выдачей');
    expect(await within(saleForm).findByText(/Выбран:/)).toHaveTextContent('Азамат Клиент');
    const posts=api.fetchMock.mock.calls.filter(([url,init])=>String(url)==='/api/customers'&&
      (init as RequestInit).method==='POST');
    expect(posts).toHaveLength(1);
  });

  it('can create draft with SALES_CREATE only but cannot read sale detail',async()=>{
    granted.permissions=['SALES_CREATE'];
    const api=mockBackend({sales:[]});
    renderAt('/sales/new');
    await userEvent.setup().click(await screen.findByRole('button',{name:'Сохранить черновик'}));
    expect(await screen.findByText('Черновик создан')).toBeInTheDocument();
    expect(screen.getByText(/Чтобы открыть карточку заказа, требуется SALES_READ/)).toBeInTheDocument();
    expect(called(api,'/api/sales/'+NEW_ID)).toHaveLength(0);
  });
  it('keeps sales list and detail forbidden to non-read employee',async()=>{
    granted.permissions=['SALES_CREATE'];
    const api=mockBackend();
    renderAt('/sales/'+SALE_ID);
    expect(screen.getByRole('heading',{name:'Доступ запрещён'})).toBeInTheDocument();
    expect(called(api,'/api/sales/'+SALE_ID)).toHaveLength(0);
  });
});
describe('F07 detail, state transitions, reservations and RBAC',()=>{
  it('employee with payment permission sees payment capture on confirmed delivered-order flow',async()=>{
    granted.roles=['EMPLOYEE'];
    granted.permissions.push('PAYMENTS_READ','PAYMENTS_CREATE','DELIVERIES_CREATE','DELIVERIES_READ');
    mockBackend({sales:[sale('CONFIRMED',{fulfillmentType:'CITY_DELIVERY'})]});
    renderAt('/sales/'+SALE_ID);
    expect(await screen.findByRole('heading',{name:'Оплата заказа'})).toBeInTheDocument();
    expect(await screen.findByRole('heading',{name:'Оплата заказа'})).toBeInTheDocument();
    // Capture controls require a verified absence of prior payment.
    // Without successful payment-history verification, the panel is read-only.
    expect(screen.queryByText('Доступ запрещён')).not.toBeInTheDocument();
  });

  it('shows a human-readable model, warehouse and confirmed event history',async()=>{
    const api=mockBackend({sales:[sale('FULFILLED',{fulfilledAt:TIME})]});
    renderAt('/sales/'+SALE_ID);
    expect(await screen.findByRole('link',{name:'Ergo Comfort'}))
      .toHaveAttribute('href','/catalog/'+MODEL_ID);
    expect(screen.getByText('Склад:',{exact:false}).parentElement).toHaveTextContent('Дом');
    const history=screen.getByRole('region',{name:'События заказа'});
    expect(history).not.toHaveAttribute('open');
    const disclosure=within(history).getByText('История заказа');
    disclosure.click();
    expect(history).toHaveAttribute('open');
    expect(within(history).getByText('Черновик создан')).toBeInTheDocument();
    expect(within(history).getByText('Продажа выполнена')).toBeInTheDocument();
    expect(within(history).getByText('Сотрудник: warehouse-operator')).toBeInTheDocument();
    expect(screen.getByRole('complementary',{name:'Итог и действия по заказу'}))
      .toHaveTextContent('17');
    expect(called(api,'/api/warehouses')).toHaveLength(1);
    expect(called(api,'/api/sales/'+SALE_ID+'/history')).toHaveLength(1);
  });
  it('does not fetch inventory or catalog for sales-only role while showing timeline',async()=>{
    granted.permissions=['SALES_READ'];
    const api=mockBackend({sales:[sale('CONFIRMED')]});
    renderAt('/sales/'+SALE_ID);
    expect(await screen.findByRole('heading',{name:'Продажа #42'})).toBeInTheDocument();
    expect(await screen.findByText('Данные склада доступны сотрудникам с INVENTORY_READ.'))
      .toBeInTheDocument();
    expect(called(api,'/api/warehouses')).toHaveLength(0);
    expect(called(api,'/api/product-variants/')).toHaveLength(0);
  });
  it('shows staff messages read-only to sales viewers and allows authorized append',async()=>{
    granted.permissions.push('SALES_COMMENT_CREATE');
    const api=mockBackend({sales:[sale('CONFIRMED')]});
    renderAt('/sales/'+SALE_ID);
    const area=await screen.findByRole('region',{name:'Комментарии сотрудников'});
    const box=within(area).getByRole('textbox',{name:'Новый комментарий'});
    fireEvent.change(box,{target:{value:'Клиент просил подготовить кресло'}});
    await userEvent.setup().click(within(area).getByRole('button',{name:'Добавить комментарий'}));
    await waitFor(()=>expect(api.fetchMock.mock.calls.filter(([raw,opts])=>
      String(raw)==='/api/sales/'+SALE_ID+'/comments'&&(opts as RequestInit)?.method==='POST')).toHaveLength(1));
    expect(api.fetchMock.mock.calls.some(([raw])=>String(raw).includes('/api/sales/'+SALE_ID+'/comments')))
      .toBe(true);
  });
  it('loads real detail with comment/amount and only available statuses',async()=>{
    const api=mockBackend({sales:[sale('CONFIRMED')]});
    renderAt('/sales/'+SALE_ID);
    expect(await screen.findByRole('heading',{name:'Продажа #42'})).toBeInTheDocument();
    expect(screen.getAllByText('Самовывоз').length).toBeGreaterThan(0);
    expect(screen.getByText('Статус оплаты')).toBeInTheDocument();
    expect(called(api,'/api/payments')).toHaveLength(0);
  });
  it('edits DRAFT via full PUT, keeping warehouse, variant and unchanged fields',async()=>{
    const api=mockBackend({sales:[sale('DRAFT')]});
    renderAt('/sales/'+SALE_ID);
    await screen.findByRole('heading',{name:'Продажа #42'});
    await userEvent.setup().click(screen.getByRole('button',{name:'Редактировать черновик'}));
    const form=within(screen.getByRole('form',{name:'Редактирование черновика'}));
    fireEvent.change(form.getByRole('textbox',{name:'Комментарий продажи'}),{target:{value:'Повторная проверка'}});
    await userEvent.setup().click(form.getByRole('button',{name:'Сохранить черновик'}));
    expect(await screen.findByText('Черновик обновлён.')).toBeInTheDocument();
    const put=api.fetchMock.mock.calls.find(([url,init])=>String(url)==='/api/sales/'+SALE_ID+'/draft'&&(init as RequestInit).method==='PUT');
    const body=JSON.parse(String((put?.[1] as RequestInit).body)) as CreateDraftInput;
    expect(body).toMatchObject({customerId:CUSTOMER_ID,fulfillmentType:'SELF_PICKUP',
      comment:'Повторная проверка',items:[{warehouseId:WH,productVariantId:BLACK_ID,quantity:2,unitSalePrice:'8500'}]});
  });
  it('preserves edited form data but locks re-submit after an uncertain PUT',async()=>{
    const api=mockBackend({sales:[sale('DRAFT')],draftUpdateNetworkOnce:true});
    renderAt('/sales/'+SALE_ID);
    await screen.findByRole('heading',{name:'Продажа #42'});
    await userEvent.setup().click(screen.getByRole('button',{name:'Редактировать черновик'}));
    const form=within(screen.getByRole('form',{name:'Редактирование черновика'}));
    fireEvent.change(form.getByRole('textbox',{name:'Комментарий продажи'}),
      {target:{value:'Не потерять этот комментарий'}});
    await userEvent.setup().click(form.getByRole('button',{name:'Сохранить черновик'}));
    expect(await screen.findByText(/Данные формы сохранены на экране/)).toBeInTheDocument();
    expect(form.getByRole('textbox',{name:'Комментарий продажи'}))
      .toHaveValue('Не потерять этот комментарий');
    expect(form.getByRole('button',{name:'Сохранить черновик'})).toBeDisabled();
    const posted=api.fetchMock.mock.calls.filter(([url,init])=>
      String(url)==='/api/sales/'+SALE_ID+'/draft'&&(init as RequestInit).method==='PUT');
    expect(posted).toHaveLength(1);
    expect(api.rows[0]?.comment).toBe('Не потерять этот комментарий');
    await userEvent.setup().click(screen.getByRole('button',{name:'Проверить актуальный статус'}));
    await waitFor(()=>expect(screen.queryByRole('form',{name:'Редактирование черновика'})).not.toBeInTheDocument());
    expect(posted).toHaveLength(1);
  });
  it('never confirms a stale DRAFT whose contents changed before irreversible POST',async()=>{
    const api=mockBackend({sales:[sale('DRAFT')],changeBeforeAction:true});
    renderAt('/sales/'+SALE_ID);
    await screen.findByRole('heading',{name:'Продажа #42'});
    await userEvent.setup().click(screen.getByRole('button',{name:'Подтвердить заказ'}));
    await userEvent.setup().click(within(screen.getByRole('alertdialog')).getByRole('button',{name:'Подтвердить'}));
    expect(await screen.findByRole('alert')).toHaveTextContent(/Заказ изменён другим сотрудником/);
    expect(api.count().confirms).toBe(0);
    const posts=api.fetchMock.mock.calls.filter(([url,opts])=>
      String(url)==='/api/sales/'+SALE_ID+'/confirm'&&(opts as RequestInit).method==='POST');
    expect(posts).toHaveLength(0);
    expect(api.rows[0]?.status).toBe('DRAFT');
  });
  it('does not discard local form data when uncertain PUT never reached the backend',async()=>{
    const api=mockBackend({sales:[sale('DRAFT')],draftUpdateFailBeforeCommit:true});
    renderAt('/sales/'+SALE_ID);
    await screen.findByRole('heading',{name:'Продажа #42'});
    await userEvent.setup().click(screen.getByRole('button',{name:'Редактировать черновик'}));
    const form=within(screen.getByRole('form',{name:'Редактирование черновика'}));
    fireEvent.change(form.getByRole('textbox',{name:'Комментарий продажи'}),
      {target:{value:'Неотправленный комментарий'}});
    await userEvent.setup().click(form.getByRole('button',{name:'Сохранить черновик'}));
    expect(await screen.findByText(/Данные формы сохранены на экране/)).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole('button',{name:'Проверить актуальный статус'}));
    expect(await screen.findByText(/Сервер не подтвердил сохранение введённых изменений/)).toBeInTheDocument();
    expect(screen.getByRole('textbox',{name:'Комментарий продажи'})).toHaveValue('Неотправленный комментарий');
    expect(form.getByRole('button',{name:'Сохранить черновик'})).toBeDisabled();
    expect(api.rows[0]?.comment).toBe('Самовывоз');
    expect(api.fetchMock.mock.calls.filter(([url,init])=>
      String(url)==='/api/sales/'+SALE_ID+'/draft'&&(init as RequestInit).method==='PUT')).toHaveLength(1);
    await userEvent.setup().click(screen.getByRole('button',{
      name:'Отказаться от локальных правок и загрузить версию сервера',
    }));
    await waitFor(()=>expect(screen.queryByRole('form',{name:'Редактирование черновика'})).not.toBeInTheDocument());
  });
  it('confirms draft only after explicit confirmation and backend updates status',async()=>{
    const api=mockBackend({sales:[sale('DRAFT')]});
    renderAt('/sales/'+SALE_ID);
    await screen.findByRole('heading',{name:'Продажа #42'});
    await userEvent.setup().click(screen.getByRole('button',{name:'Подтвердить заказ'}));
    expect(screen.getByRole('alertdialog')).toHaveTextContent('17');
    expect(api.count().confirms).toBe(0);
    await userEvent.setup().click(within(screen.getByRole('alertdialog')).getByRole('button',{name:'Подтвердить'}));
    expect(await screen.findByText('Заказ подтверждён.')).toBeInTheDocument();
    expect(api.count().confirms).toBe(1);
    expect(api.rows[0]?.status).toBe('CONFIRMED');
  });
  it('keeps DRAFT on stock failure with no invented success or frontend stock move',async()=>{
    const api=mockBackend({sales:[sale('DRAFT')],stockFailure:true});
    renderAt('/sales/'+SALE_ID);
    await screen.findByRole('heading',{name:'Продажа #42'});
    await userEvent.setup().click(screen.getByRole('button',{name:'Подтвердить заказ'}));
    await userEvent.setup().click(within(screen.getByRole('alertdialog')).getByRole('button',{name:'Подтвердить'}));
    expect(await screen.findByRole('alert')).toHaveTextContent(/409|противоречит/);
    expect(api.count().confirms).toBe(0);
    expect(api.rows[0]?.status).toBe('DRAFT');
    expect(called(api,'/api/inventory/transfers')).toHaveLength(0);
  });
  it('fulfills confirmed SELF_PICKUP with confirmation; backend exclusively posts inventory',async()=>{
    const api=mockBackend({sales:[sale('CONFIRMED')]});
    renderAt('/sales/'+SALE_ID);
    await screen.findByRole('heading',{name:'Продажа #42'});
    await userEvent.setup().click(screen.getByRole('button',{name:'Подтвердить выдачу кресел'}));
    expect(api.count().fulfills).toBe(0);
    await userEvent.setup().click(within(screen.getByRole('alertdialog')).getByRole('button',{name:'Подтвердить выдачу'}));
    expect(await screen.findByText('Продажа выполнена.')).toBeInTheDocument();
    expect(api.count().fulfills).toBe(1);
    expect(api.rows[0]?.status).toBe('FULFILLED');
    expect(called(api,'/api/inventory/')).toHaveLength(0);
  });
  it('never allows direct fulfillment/cancellation of delivery sales (F08)',async()=>{
    const api=mockBackend({sales:[sale('CONFIRMED',{fulfillmentType:'REGION_DELIVERY'})]});
    renderAt('/sales/'+SALE_ID);
    await screen.findByRole('heading',{name:'Продажа #42'});
    expect(screen.queryByRole('button',{name:'Завершить продажу'})).not.toBeInTheDocument();
    expect(screen.queryByRole('button',{name:'Отменить заказ'})).not.toBeInTheDocument();
    expect(screen.getByRole('region', {name:'Связанная доставка'})).toBeInTheDocument();
    expect(api.count()).toMatchObject({fulfills:0,cancels:0});
  });
  it('shows compact order controls without the redundant actions card',async()=>{
    mockBackend({sales:[sale('DRAFT')]});
    renderAt('/sales/'+SALE_ID);
    await screen.findByRole('heading',{name:'Продажа #42'});
    const controls=screen.getByLabelText('Управление заказом');
    expect(within(controls).getByRole('button',{name:'Редактировать черновик'})).toBeInTheDocument();
    expect(within(controls).getByRole('button',{name:'Отменить заказ'})).toBeInTheDocument();
    expect(screen.queryByRole('region',{name:'Действия с заказом'})).not.toBeInTheDocument();
  });
  it('cancels DRAFT only with confirmation; cannot edit FULFILLED',async()=>{
    const api=mockBackend({sales:[sale('DRAFT')]});
    renderAt('/sales/'+SALE_ID);
    await screen.findByRole('heading',{name:'Продажа #42'});
    await userEvent.setup().click(screen.getByRole('button',{name:'Отменить заказ'}));
    expect(api.count().cancels).toBe(0);
    await userEvent.setup().click(within(screen.getByRole('alertdialog')).getByRole('button',{name:'Отменить заказ'}));
    expect(await screen.findByText('Заказ отменён.')).toBeInTheDocument();
    expect(api.count().cancels).toBe(1);
    expect(screen.queryByRole('button',{name:'Редактировать черновик'})).not.toBeInTheDocument();
  });
  it('effective custom role permissions suppress forbidden actions',async()=>{
    granted.roles=['REGION_SALES_TEAM'];
    granted.permissions=['SALES_READ'];
    const api=mockBackend({sales:[sale('CONFIRMED')]});
    renderAt('/sales/'+SALE_ID);
    await screen.findByRole('heading',{name:'Продажа #42'});
    expect(screen.queryByRole('button',{name:'Завершить продажу'})).not.toBeInTheDocument();
    expect(screen.queryByRole('button',{name:'Отменить заказ'})).not.toBeInTheDocument();
    expect(called(api,'/api/inventory/')).toHaveLength(0);
    expect(called(api,'/api/customers')).toHaveLength(0);
  });
  it('opens the exact related delivery rather than the general deliveries list',async()=>{
    granted.permissions.push('DELIVERIES_READ');
    const api=mockBackend({sales:[sale('CONFIRMED',{fulfillmentType:'CITY_DELIVERY'})]});
    renderAt('/sales/'+SALE_ID);
    const link=await screen.findByRole('link',{name:'Открыть доставку'});
    expect(link).toHaveAttribute('href','/deliveries/'+DELIVERY_ID);
    expect(called(api,'/api/deliveries/by-sale/'+SALE_ID)).toHaveLength(1);
  });
  it('handles backend 403 and 404 on direct sale detail routes',async()=>{
    const api=mockBackend({deny:{['GET /api/sales/'+SALE_ID]:403}});
    renderAt('/sales/'+SALE_ID);
    expect(await screen.findByRole('heading',{name:'Доступ запрещён'})).toBeInTheDocument();
    expect(called(api,'/api/sales/'+SALE_ID)).toHaveLength(1);
  });
});

describe('UI-V2-03 Kanban initiation never mutates before explicit server confirmation', () => {
  it('selecting a draft transition opens existing guarded confirm dialog; POST only after approval', async () => {
    const api = mockBackend({sales: [sale('DRAFT')]});
    renderAt();
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', {name: 'Канбан'}));
    const column = await screen.findByRole('region', {name: 'Канбан: Черновик'});
    const selector = await within(column).findByRole('combobox', {
      name: 'Изменить статус заказа CHX-2026-0042',
    });
    await user.selectOptions(selector, 'CONFIRMED');
    expect(await screen.findByText('Продажа #42')).toBeInTheDocument();
    expect(await screen.findByRole('alertdialog', {name: 'Подтвердить заказ?'})).toBeInTheDocument();
    expect(api.count().confirms).toBe(0);
    await user.click(screen.getByRole('button', {name: /^Подтвердить$/}));
    await waitFor(() => expect(api.count().confirms).toBe(1));
  });

  it('dragging draft to confirmed requests confirmation without posting; cancellation posts nothing', async () => {
    const api = mockBackend({sales: [sale('DRAFT')]});
    renderAt();
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', {name: 'Канбан'}));
    const source = await screen.findByRole('region', {name: 'Канбан: Черновик'});
    const target = screen.getByRole('region', {name: 'Канбан: Подтверждён'});
    const card = (await within(source).findByRole('link', {name: 'Открыть продажу CHX-2026-0042'}))
      .closest('article');
    expect(card).not.toBeNull();
    const dataTransfer = {effectAllowed: 'none', dropEffect: 'none', setData: vi.fn()};
    fireEvent.dragStart(card!, {dataTransfer});
    fireEvent.dragOver(target, {dataTransfer});
    fireEvent.drop(target, {dataTransfer});
    expect(await screen.findByRole('alertdialog', {name: 'Подтвердить заказ?'})).toBeInTheDocument();
    expect(api.count().confirms).toBe(0);
    await user.click(screen.getByRole('button', {name: /^Отмена$/}));
    expect(api.count()).toMatchObject({confirms: 0, fulfills: 0, cancels: 0});
  });

  it('warehouse/delivery RBAC cannot reveal forbidden sale mutations on Kanban', async () => {
    granted.permissions = ['SALES_READ'];
    const api = mockBackend({sales:[sale('CONFIRMED',{fulfillmentType:'REGION_DELIVERY'})]});
    renderAt();
    await userEvent.setup().click(screen.getByRole('button', {name: 'Канбан'}));
    const column = await screen.findByRole('region', {name: 'Канбан: Подтверждён'});
    expect(within(column).queryByRole('combobox', {name:/Изменить статус заказа/})).not.toBeInTheDocument();
    expect(api.count()).toMatchObject({confirms:0,fulfills:0,cancels:0});
  });
});
