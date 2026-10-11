import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router';
import { AppRouter } from '../app/router/AppRouter';
import { createQueryClient } from '../shared/api/query';
import { authSession } from '../features/auth/session';
import { applyQuick } from '../features/deliveries/components/DeliveryFilters';
import { bishkekToday, nextBishkekDay, validateCreate, deliveryStatuses } from '../features/deliveries/validation';
import { readPendingCreate } from '../features/deliveries/pending-create';
import type { CreateDeliveryInput, DeliveryResponse, DeliverySummary, DeliveryStatus } from '../features/deliveries/types';
import type { SaleResponse } from '../features/sales/types';

const access=vi.hoisted(()=>({permissions:[
  'DELIVERIES_READ','DELIVERIES_MANAGE','SALES_CANCEL','SALES_READ','INVENTORY_READ',
] as string[], roles:['CUSTOM_LOGISTICS_ROLE'] as string[]}));
vi.mock('../features/auth/AuthProvider',()=>({
  useAuth:()=>({
    status:'authenticated',isAuthenticated:true,
    user:{id:'delivery-employee',username:'delivery-employee',displayName:'Logistics',
      permissions:access.permissions,roles:access.roles},
    hasPermission:(p:string)=>access.permissions.includes(p),
    hasAnyPermission:(ps:readonly string[])=>ps.some(p=>access.permissions.includes(p)),
    login:vi.fn(),logout:vi.fn(),
  }),
}));
const ID='11111111-1111-4111-8111-111111111111';
const SALE='22222222-2222-4222-8222-222222222222';
const WH='33333333-3333-4333-8333-333333333333';
const BASE='2026-10-10T10:30:00Z';
const json=(body:unknown,status=200)=>new Response(JSON.stringify(body),
  {status,headers:{'Content-Type':'application/json'}});
function delivery(status:DeliveryStatus='READY',changes:Partial<DeliveryResponse>={}):DeliveryResponse{
  return {
    id:ID,saleId:SALE,status,recipientName:'Айбек',recipientPhone:'+996700123456',
    address:'Бишкек, улица Тестовая 12',cityRegion:'Бишкек',deliveryCost:'300.00',
    carrierName:'Cargo Bishkek',trackingNumber:'TRACK-7',comment:'Осторожно, мебель',
    createdBy:'admin',createdAt:BASE,dispatchedBy:status==='IN_TRANSIT'?'admin':null,
    dispatchedAt:status==='IN_TRANSIT'?BASE:null,deliveredBy:null,deliveredAt:null,
    failedBy:status==='FAILED'?'worker':null,failedAt:status==='FAILED'?BASE:null,
    failureReason:status==='FAILED'?'Адресат отсутствует':null,
    cancelledBy:null,cancelledAt:null,
    returnedToWarehouseBy:null,returnedToWarehouseAt:null,returnWarehouseId:null,
    plannedDeliveryDate:'2026-10-16',...changes,
  };
}
const asSummary=(d:DeliveryResponse):DeliverySummary=>({
  id:d.id,saleId:d.saleId,status:d.status,recipientName:d.recipientName,
  cityRegion:d.cityRegion,createdAt:d.createdAt,plannedDeliveryDate:d.plannedDeliveryDate,
  saleNumber:'CHX-2026-0101',recipientPhone:d.recipientPhone,address:d.address,
  fulfillmentType:'CITY_DELIVERY',deliveredAt:d.deliveredAt,
});
const confirmedSale:SaleResponse={
  id:SALE,saleNumber:'CHX-2026-0101',customerId:null,fulfillmentType:'CITY_DELIVERY',
  status:'CONFIRMED',items:[],total:'10000.00',createdBy:'seller',createdAt:BASE,
  fulfilledBy:null,fulfilledAt:null,cancelledBy:null,cancelledAt:null,comment:null,
};
interface Options {
  deliveries?:DeliveryResponse[]; afterCreateNetwork?:boolean; errorOnce?:Record<string,number>;
  saleType?:'CITY_DELIVERY'|'REGION_DELIVERY'|'SELF_PICKUP'; saleStatus?:'CONFIRMED'|'DRAFT';
  mutateBeforeAction?:boolean; afterDispatchNetwork?:boolean; afterDateNetwork?:boolean;
}
function mockBackend(options:Options={}){
  const data=[...(options.deliveries??[delivery()])];
  const errors={...options.errorOnce};let createNetwork=Boolean(options.afterCreateNetwork);
  let creates=0,dispatches=0,saleFulfilled=0,saleCancelled=0;
  let saleOut=0,returnIn=0,deliverCount=0,failCount=0,dateChanges=0;
  let detailGets=0;
  let dispatchNetwork=Boolean(options.afterDispatchNetwork);
  let dateNetwork=Boolean(options.afterDateNetwork);
  const fetchMock=vi.fn(async(raw:RequestInfo|URL,init?:RequestInit):Promise<Response>=>{
    const url=new URL(String(raw),'http://chairx.test');
    const path=url.pathname,method=init?.method??'GET';
    const issue=method+' '+path;
    if(errors[issue]){const code=errors[issue];delete errors[issue];
      return json({code:'API_ERROR',message:'internal secret',details:{}},code!);}
    if(path==='/api/deliveries'&&method==='GET'){
      const f=url.searchParams;
      const page=Number(f.get('page')||0),size=Number(f.get('size')||20);
      const rows=data.filter(d=>(!f.get('status')||d.status===f.get('status'))&&
        (!f.get('plannedFrom')||!!d.plannedDeliveryDate&&d.plannedDeliveryDate>=f.get('plannedFrom')!)&&
        (!f.get('plannedTo')||!!d.plannedDeliveryDate&&d.plannedDeliveryDate<=f.get('plannedTo')!)&&
        (!f.get('from')||d.createdAt.slice(0,10)>=f.get('from')!)&&
        (!f.get('to')||d.createdAt.slice(0,10)<=f.get('to')!)&&
        (!f.get('cityRegion')||d.cityRegion?.toLowerCase().includes(f.get('cityRegion')!.toLowerCase())));
      return json({page,size,total:rows.length,items:rows.slice(page*size,(page+1)*size).map(asSummary)});
    }
    if(path==='/api/deliveries/by-sale/'+SALE&&method==='GET'){
      const existing=data.find(d=>d.saleId===SALE);
      return existing?json(existing):json({code:'DELIVERY_NOT_FOUND',message:'',details:{}},404);
    }
    if(path==='/api/deliveries'&&method==='POST'){
      const body=JSON.parse(String(init?.body)) as CreateDeliveryInput;
      if(body.saleId!==SALE||options.saleType==='SELF_PICKUP'||options.saleStatus==='DRAFT')
        return json({code:'INVALID_SALE_STATUS',message:'',details:{}},409);
      if(data.some(d=>d.saleId===body.saleId))return json({code:'DELIVERY_ALREADY_EXISTS',message:'',details:{}},409);
      const result=delivery('READY',{...body,id:ID});
      data.push(result);creates++;
      if(createNetwork){createNetwork=false;throw new TypeError('connection lost after commit');}
      return json(result,201);
    }
    const match=/^\/api\/deliveries\/([^/]+)$/.exec(path);
    if(match&&method==='GET'){
      detailGets++;
      const d=data.find(d=>d.id===match[1]);
      if(d&&options.mutateBeforeAction&&detailGets===2)d.status='CANCELLED';
      return d?json(d):json({code:'NOT_FOUND',message:'',details:{}},404);
    }
    const date=/^\/api\/deliveries\/([^/]+)\/planned-date$/.exec(path);
    if(date&&method==='PUT'){
      const d=data.find(x=>x.id===date[1]);
      if(!d)return json({},404);
      if(d.status!=='READY'&&d.status!=='IN_TRANSIT')return json({code:'INVALID_DELIVERY_STATUS',message:'',details:{}},409);
      const payload=JSON.parse(String(init?.body)) as {plannedDeliveryDate:string|null};
      d.plannedDeliveryDate=payload.plannedDeliveryDate;dateChanges++;
      if(dateNetwork){dateNetwork=false;throw new TypeError('PUT committed but response lost');}
      return json(d);
    }
    const action=/^\/api\/deliveries\/([^/]+)\/(dispatch|deliver|fail|cancel|return-to-warehouse)$/.exec(path);
    if(action&&method==='POST'){
      const d=data.find(x=>x.id===action[1]);
      if(!d)return json({},404);
      const op=action[2];
      if(op==='dispatch'){
        if(d.status!=='READY'&&d.status!=='IN_TRANSIT')return json({code:'INVALID_DELIVERY_STATUS',message:'',details:{}},409);
        if(d.status==='READY'){
          d.status='IN_TRANSIT';d.dispatchedAt=BASE;d.dispatchedBy='logistics';
          dispatches++;saleOut++;saleFulfilled++;
        }
      }else if(op==='deliver'){
        if(d.status==='IN_TRANSIT'){
          d.status='DELIVERED';d.deliveredAt=BASE;d.deliveredBy='logistics';deliverCount++;
        }else if(d.status!=='DELIVERED')return json({code:'INVALID_DELIVERY_STATUS',message:'',details:{}},409);
      }else if(op==='fail'){
        if(d.status==='IN_TRANSIT'){
          d.status='FAILED';d.failedAt=BASE;d.failedBy='logistics';
          d.failureReason=(JSON.parse(String(init?.body)) as {reason:string}).reason;failCount++;
        }else if(d.status!=='FAILED')return json({code:'INVALID_DELIVERY_STATUS',message:'',details:{}},409);
      }else if(op==='cancel'){
        if(d.status==='READY'){d.status='CANCELLED';d.cancelledAt=BASE;saleCancelled++;}
        else if(d.status!=='CANCELLED')return json({code:'INVALID_DELIVERY_STATUS',message:'',details:{}},409);
      }else {
        if(d.status!=='FAILED')return json({code:'INVALID_DELIVERY_STATUS',message:'',details:{}},409);
        if(!d.returnedToWarehouseAt){
          d.returnedToWarehouseAt=BASE;d.returnedToWarehouseBy='logistics';
          d.returnWarehouseId=(JSON.parse(String(init?.body)) as {warehouseId:string}).warehouseId;returnIn++;
        }
      }
      if(op==='dispatch'&&dispatchNetwork){
        dispatchNetwork=false;
        throw new TypeError('POST committed but response lost');
      }
      return json(d);
    }
    if(path==='/api/sales'&&method==='GET')
      return json({page:0,size:20,total:1,items:[{
        id:SALE,saleNumber:confirmedSale.saleNumber,
        fulfillmentType:options.saleType||'CITY_DELIVERY',
        status:options.saleStatus||'CONFIRMED',customerName:'Айбек',
      }]});
    if(path==='/api/sales/'+SALE&&method==='GET')
      return json({...confirmedSale,status:options.saleStatus||'CONFIRMED',
        fulfillmentType:options.saleType||'CITY_DELIVERY'});
    if(path==='/api/warehouses'&&method==='GET'){
      return json({page:0,size:100,totalElements:2,totalPages:1,items:[
        {id:WH,name:'Дом',code:'HOME',address:null,active:true,createdAt:BASE,updatedAt:BASE},
        {id:'44444444-4444-4444-8444-444444444444',name:'Недоступен',code:'OFF',address:null,
          active:false,createdAt:BASE,updatedAt:BASE},
      ]});
    }
    throw new Error('Unexpected HTTP '+method+' '+path);
  });
  vi.stubGlobal('fetch',fetchMock);
  return {fetchMock,data,counts:()=>({creates,dispatches,saleFulfilled,saleCancelled,saleOut,returnIn,deliverCount,failCount,dateChanges})};
}
function renderAt(route='/deliveries'){
  return render(<QueryClientProvider client={createQueryClient()}>
    <MemoryRouter initialEntries={[route]}><AppRouter/></MemoryRouter>
  </QueryClientProvider>);
}
const requests=(api:ReturnType<typeof mockBackend>,term:string)=>
  api.fetchMock.mock.calls.filter(([url])=>String(url).includes(term));
beforeEach(()=>{
  access.permissions=['DELIVERIES_READ','DELIVERIES_MANAGE','SALES_CANCEL','SALES_READ','INVENTORY_READ'];
  access.roles=['CUSTOM_LOGISTICS_ROLE'];
  sessionStorage.clear();
  authSession.activate('Basic logistics-user',{headerName:'X-CSRF-TOKEN',token:'test-csrf'});
});
afterEach(()=>{authSession.clear();sessionStorage.clear();vi.restoreAllMocks();vi.unstubAllGlobals();});

describe('F08 backend DTOs, filters and list',()=>{
  it('honors exactly five statuses and accurate Bishkek local date',()=>{
    expect(deliveryStatuses).toEqual(['READY','IN_TRANSIT','DELIVERED','FAILED','CANCELLED']);
    expect(bishkekToday(new Date('2026-10-10T21:30:00Z'))).toBe('2026-10-11');
    expect(nextBishkekDay(new Date('2026-10-10T21:30:00Z'))).toBe('2026-10-12');
    const today=applyQuick('today');
    expect(today.plannedFrom).toBe(bishkekToday());
    expect(today.plannedTo).toBe(today.plannedFrom);
    expect(applyQuick('upcoming').plannedFrom).toBe(nextBishkekDay());
    expect(applyQuick('READY').status).toBe('READY');
  });
  it('real list shows saleNumber, phone, city, plan and mobile summary without N+1',async()=>{
    const api=mockBackend();
    renderAt();
    await screen.findByRole('heading',{name:'Доставки',level:2});
    expect((await screen.findAllByText('#101')).length).toBeGreaterThan(0);
    expect(screen.getAllByText('+996700123456').length).toBeGreaterThan(0);
    expect(screen.getAllByText(/Тестовая 12/).length).toBeGreaterThan(0);
    expect(requests(api,'/api/sales/')).toHaveLength(0);
    expect(requests(api,'/api/deliveries/'+ID)).toHaveLength(0);
  });
  it('paginates 22 results with page=1 on server',async()=>{
    const all=Array.from({length:22},(_,i)=>delivery('READY',{
      id:'55555555-5555-4555-8555-'+String(i).padStart(12,'0'),
    }));
    const api=mockBackend({deliveries:all});
    renderAt();
    expect(await screen.findByText(/Всего доставок: 22/)).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole('button',{name:'Следующая'}));
    expect(await screen.findByText(/Страница 2 из 2/, undefined, { timeout: 5000 })).toBeInTheDocument();
    expect(requests(api,'/api/deliveries?').some(([url])=>String(url).includes('page=1'))).toBe(true);
  });
  it('quick READY + planned dates + city search go to actual GET query with page=0',async()=>{
    const api=mockBackend();
    renderAt();
    await screen.findByRole('heading',{name:'Доставки',level:2});
    await userEvent.setup().click(screen.getByRole('button',{name:'Готовые'}));
    fireEvent.change(screen.getByRole('searchbox',{name:'Поиск города или региона'}),
      {target:{value:'Бишкек'}});
    await waitFor(()=>expect(api.fetchMock.mock.calls.some(([url])=>{
      const x=new URL(String(url),'http://chairx.test');
      return x.pathname==='/api/deliveries'&&x.searchParams.get('status')==='READY'&&
        x.searchParams.get('cityRegion')==='Бишкек'&&x.searchParams.get('page')==='0';
    })).toBe(true));
    fireEvent.change(screen.getByLabelText('Плановая дата с'),{target:{value:'2026-10-15'}});
    fireEvent.change(screen.getByLabelText('Плановая дата по'),{target:{value:'2026-10-17'}});
    fireEvent.change(screen.getByLabelText('Создана с'),{target:{value:'2026-10-09'}});
    await waitFor(()=>expect(api.fetchMock.mock.calls.some(([url])=>{
      const x=new URL(String(url),'http://chairx.test');
      return x.pathname==='/api/deliveries'&&
        x.searchParams.get('plannedFrom')==='2026-10-15'&&
        x.searchParams.get('plannedTo')==='2026-10-17'&&
        x.searchParams.get('from')==='2026-10-09';
    })).toBe(true));
  });
  it('shows a true empty state and never uses private payment endpoints',async()=>{
    const api=mockBackend({deliveries:[]});
    renderAt();
    expect(await screen.findByText('Доставки не найдены')).toBeInTheDocument();
    expect(requests(api,'/api/payments')).toHaveLength(0);
  });
  it('respects custom READ only role and never shows create',async()=>{
    access.permissions=['DELIVERIES_READ'];access.roles=['LOGISTICS_VIEWER'];
    const api=mockBackend();
    renderAt();
    await screen.findByRole('heading',{name:'Доставки',level:2});
    expect(screen.queryByRole('link',{name:'+ Оформить доставку'})).not.toBeInTheDocument();
    expect(requests(api,'/api/sales')).toHaveLength(0);
  });
});
describe('FLOW-24 direct delivery links and least-privilege reads',()=>{
 it('resolves a human sale number on direct detail URL without list state',async()=>{
  const api=mockBackend();renderAt('/deliveries/'+ID);
  expect(await screen.findByRole('heading',{name:'Доставка · #101'})).toBeInTheDocument();
  expect(requests(api,'/api/sales/'+SALE)).toHaveLength(1);
  expect(screen.getByRole('link',{name:'Открыть связанную продажу'}))
   .toHaveAttribute('href','/sales/'+SALE);
 });
 it('does not fetch sale details without SALES_READ',async()=>{
  access.permissions=['DELIVERIES_READ'];
  const api=mockBackend();renderAt('/deliveries/'+ID);
  expect(await screen.findByRole('heading',{name:'Доставка'})).toBeInTheDocument();
  expect(requests(api,'/api/sales/'+SALE)).toHaveLength(0);
  expect(screen.queryByRole('link',{name:'Открыть связанную продажу'})).not.toBeInTheDocument();
 });
});
describe('F08 create delivery and non-idempotent network outcomes',()=>{
  it('validates phone, address, integer som and nullable name',()=>{
    const p:CreateDeliveryInput={saleId:SALE,recipientName:null,recipientPhone:'+996700123456',
      address:'Бишкек',cityRegion:null,deliveryCost:'0',carrierName:null,
      trackingNumber:null,comment:null,plannedDeliveryDate:null};
    expect(validateCreate(p)).toBeNull();
    expect(validateCreate({...p,recipientPhone:''})).toMatch(/Телефон/);
    expect(validateCreate({...p,address:''})).toMatch(/Адрес/);
    expect(validateCreate({...p,deliveryCost:'300.01'})).toMatch(/целое/);
    expect(validateCreate({...p,deliveryCost:'-1'})).toMatch(/целое/);
    expect(validateCreate({...p,deliveryCost:'10000000000000000000'})).toMatch(/целое/);
    expect(validateCreate({...p,recipientPhone:'+'.repeat(51)})).toMatch(/Телефон/);
  });
  it('selects a sale by readable number without any UUID input',async()=>{
    mockBackend({deliveries:[]});
    renderAt('/deliveries/new');
    const form=await screen.findByRole('form',{name:'Оформить доставку'});
    expect(within(form).queryByRole('textbox',{name:'UUID заказа'})).not.toBeInTheDocument();
    const select=await within(form).findByRole('button',{name:/#101.*Айбек/});
    await userEvent.setup().click(select);
    expect(await within(form).findByRole('link',{name:'#101'})).toHaveAttribute('href','/sales/'+SALE);
  });
  it('blocks duplicate delivery before POST and links to the existing document',async()=>{
    const api=mockBackend();
    renderAt('/deliveries/new?saleId='+SALE);
    const form=await screen.findByRole('form',{name:'Оформить доставку'});
    expect(await within(form).findByRole('link',{name:'Открыть доставку'})).toHaveAttribute('href','/deliveries/'+ID);
    expect(within(form).getByRole('button',{name:'Создать доставку'})).toBeDisabled();
    expect(api.counts().creates).toBe(0);
  });
  it('creates CITY_DELIVERY using real Sale UUID without client payment',async()=>{
    const api=mockBackend({deliveries:[]});
    renderAt('/deliveries/new?saleId='+SALE);
    const form=await screen.findByRole('form',{name:'Оформить доставку'});
    fireEvent.change(within(form).getByRole('textbox',{name:'Телефон *'}),{target:{value:'+996700123456'}});
    fireEvent.change(within(form).getByRole('textbox',{name:'Адрес *'}),{target:{value:'Бишкек, 12'}});
    fireEvent.change(within(form).getByRole('textbox',{name:'Стоимость доставки, сом *'}),
      {target:{value:'300'}});
    await userEvent.setup().click(within(form).getByRole('button',{name:'Создать доставку'}));
    expect(await screen.findByRole('heading',{name:/Доставка/})).toBeInTheDocument();
    expect(api.counts().creates).toBe(1);
    const post=api.fetchMock.mock.calls.find(([url,init])=>String(url)==='/api/deliveries'&&(init as RequestInit).method==='POST');
    const body=JSON.parse(String((post?.[1] as RequestInit).body)) as CreateDeliveryInput;
    expect(body).toMatchObject({saleId:SALE,recipientPhone:'+996700123456',
      address:'Бишкек, 12',deliveryCost:'300',plannedDeliveryDate:null,recipientName:null});
    expect(requests(api,'/api/payments')).toHaveLength(0);
  });
  it('creates REGION_DELIVERY with carrier, tracking and planned LocalDate',async()=>{
    const api=mockBackend({deliveries:[],saleType:'REGION_DELIVERY'});
    renderAt('/deliveries/new?saleId='+SALE);
    const f=await screen.findByRole('form',{name:'Оформить доставку'});
    fireEvent.change(within(f).getByRole('textbox',{name:'Имя получателя'}),{target:{value:'Бакыт'}});
    fireEvent.change(within(f).getByRole('textbox',{name:'Телефон *'}),{target:{value:'+996555777888'}});
    fireEvent.change(within(f).getByRole('textbox',{name:'Адрес *'}),{target:{value:'Ош, дом 1'}});
    fireEvent.change(within(f).getByRole('textbox',{name:'Город / регион'}),{target:{value:'Ош'}});
    fireEvent.change(within(f).getByRole('textbox',{name:'Стоимость доставки, сом *'}),
      {target:{value:'1000'}});
    fireEvent.change(within(f).getByRole('textbox',{name:'Перевозчик'}),{target:{value:'Cargo Osh'}});
    fireEvent.change(within(f).getByRole('textbox',{name:'Трек-номер'}),{target:{value:'OS-12'}});
    fireEvent.change(within(f).getByLabelText('Плановая дата'),{target:{value:'2026-10-16'}});
    await userEvent.setup().click(within(f).getByRole('button',{name:'Создать доставку'}));
    expect(await screen.findByRole('heading',{name:/Доставка/})).toBeInTheDocument();
    const posts=api.fetchMock.mock.calls.filter(([url,init])=>String(url)==='/api/deliveries'&&(init as RequestInit).method==='POST');
    expect(posts).toHaveLength(1);
    const body=JSON.parse(String((posts[0]?.[1] as RequestInit).body)) as CreateDeliveryInput;
    expect(body).toMatchObject({saleId:SALE,recipientName:'Бакыт',cityRegion:'Ош',
      carrierName:'Cargo Osh',trackingNumber:'OS-12',plannedDeliveryDate:'2026-10-16'});
  });
  it('blocks SELF_PICKUP with no POST and does not invent sales access',async()=>{
    const api=mockBackend({deliveries:[],saleType:'SELF_PICKUP'});
    renderAt('/deliveries/new?saleId='+SALE);
    expect(await screen.findByText(/Для этого заказа оформление доставки запрещено/)).toBeInTheDocument();
    expect(screen.getByRole('button',{name:'Создать доставку'})).toBeDisabled();
    expect(api.counts().creates).toBe(0);
  });
  it('does not read sales API without SALES_READ',async()=>{
    access.permissions=['DELIVERIES_READ','DELIVERIES_MANAGE'];
    const api=mockBackend({deliveries:[]});
    renderAt('/deliveries/new?saleId='+SALE);
    await screen.findByRole('form',{name:'Оформить доставку'});
    expect(screen.queryByRole('region',{name:'Выбор заказа'})).not.toBeInTheDocument();
    expect(requests(api,'/api/sales')).toHaveLength(0);
  });
  it('locks after connection loss after POST and does not auto-replay without idempotency key',async()=>{
    const api=mockBackend({deliveries:[],afterCreateNetwork:true});
    const first=renderAt('/deliveries/new?saleId='+SALE);
    const form=await screen.findByRole('form',{name:'Оформить доставку'});
    fireEvent.change(within(form).getByRole('textbox',{name:'Телефон *'}),{target:{value:'+996700123456'}});
    fireEvent.change(within(form).getByRole('textbox',{name:'Адрес *'}),{target:{value:'Бишкек'}});
    fireEvent.change(within(form).getByRole('textbox',{name:'Стоимость доставки, сом *'}),
      {target:{value:'300'}});
    await userEvent.setup().click(within(form).getByRole('button',{name:'Создать доставку'}));
    expect(await screen.findByText(/Нельзя безопасно повторить создание доставки/)).toBeInTheDocument();
    expect(api.counts().creates).toBe(1);
    expect(readPendingCreate('delivery-employee').kind).toBe('pending');
    first.unmount();
    renderAt('/deliveries/new');
    expect(await screen.findByText(/Нельзя безопасно повторить создание доставки/)).toBeInTheDocument();
    expect(requests(api,'/api/deliveries').filter(([,init])=>(init as RequestInit).method==='POST')).toHaveLength(1);
  });
});
describe('F08 detail, dispatch/deliver/fail/return and authorizations',()=>{
  it('loads contact detail/timeline using DELIVERIES_READ only; no sale or finance GET',async()=>{
    access.permissions=['DELIVERIES_READ'];
    const api=mockBackend();
    renderAt('/deliveries/'+ID);
    expect(await screen.findByRole('heading',{name:/Доставка/})).toBeInTheDocument();
    expect(screen.getByText('+996700123456')).toBeInTheDocument();
    expect(screen.getByRole('button',{name:'Копировать адрес'})).toBeInTheDocument();
    expect(screen.getByText('Cargo Bishkek')).toBeInTheDocument();
    expect(screen.getByRole('region',{name:'История этапов доставки'})).toBeInTheDocument();
    expect(screen.queryByRole('link',{name:'Открыть связанную продажу'})).not.toBeInTheDocument();
    expect(requests(api,'/api/sales')).toHaveLength(0);
    expect(requests(api,'/api/warehouses')).toHaveLength(0);
  });
  it('dispatch READY -> IN_TRANSIT once, performs Sale FULFILLED and SALE_OUT in backend only',async()=>{
    const api=mockBackend();
    renderAt('/deliveries/'+ID);
    await screen.findByRole('heading',{name:/Доставка/});
    await userEvent.setup().click(screen.getByRole('button',{name:'Отправить доставку'}));
    expect(api.counts().dispatches).toBe(0);
    await userEvent.setup().click(within(screen.getByRole('alertdialog')).getByRole('button',{name:'Подтвердить действие'}));
    await waitFor(()=>expect(api.counts().dispatches).toBe(1));
    expect(api.counts().saleOut).toBe(1);
    expect(api.counts().saleFulfilled).toBe(1);
    expect(requests(api,'/api/sales/'+SALE+'/fulfill')).toHaveLength(0);
    expect(requests(api,'/api/inventory/transfers')).toHaveLength(0);
    expect(await screen.findByRole('button',{name:'Подтвердить вручение'})).toBeInTheDocument();
  });
  it('IN_TRANSIT -> DELIVERED has no second stock movement or client payment',async()=>{
    const api=mockBackend({deliveries:[delivery('IN_TRANSIT')]});
    renderAt('/deliveries/'+ID);
    await screen.findByRole('heading',{name:/Доставка/});
    await userEvent.setup().click(screen.getByRole('button',{name:'Подтвердить вручение'}));
    await userEvent.setup().click(within(screen.getByRole('alertdialog')).getByRole('button',{name:'Подтвердить действие'}));
    await waitFor(()=>expect(api.counts().deliverCount).toBe(1));
    expect(api.counts().saleOut).toBe(0);
    expect(requests(api,'/api/payments')).toHaveLength(0);
    expect(await screen.findByText('Доставлена')).toBeInTheDocument();
  });
  it('IN_TRANSIT -> FAILED requires reason, does not return item automatically',async()=>{
    const api=mockBackend({deliveries:[delivery('IN_TRANSIT')]});
    renderAt('/deliveries/'+ID);
    await screen.findByRole('heading',{name:/Доставка/});
    expect(screen.getByRole('button',{name:'Зафиксировать неуспешную доставку'})).toBeDisabled();
    fireEvent.change(screen.getByRole('textbox',{name:'Причина неуспешной доставки'}),
      {target:{value:'Клиент не смог принять заказ'}});
    await userEvent.setup().click(screen.getByRole('button',{name:'Зафиксировать неуспешную доставку'}));
    await userEvent.setup().click(within(screen.getByRole('alertdialog')).getByRole('button',{name:'Подтвердить действие'}));
    await waitFor(()=>expect(api.counts().failCount).toBe(1));
    expect(api.counts().returnIn).toBe(0);
    expect(await screen.findByText('Клиент не смог принять заказ')).toBeInTheDocument();
  });
  it('FAILED -> RETURN_IN via real active warehouse; remains FAILED; no second return button',async()=>{
    const api=mockBackend({deliveries:[delivery('FAILED')]});
    renderAt('/deliveries/'+ID);
    await screen.findByRole('heading',{name:/Доставка/});
    fireEvent.change(await screen.findByRole('combobox',{name:'Склад возврата'}),{target:{value:WH}});
    await userEvent.setup().click(screen.getByRole('button',{name:'Вернуть товар на склад'}));
    await userEvent.setup().click(within(screen.getByRole('alertdialog')).getByRole('button',{name:'Подтвердить действие'}));
    await waitFor(()=>expect(api.counts().returnIn).toBe(1));
    expect(api.data[0]?.status).toBe('FAILED');
    expect(await screen.findByText('Товар возвращён на склад')).toBeInTheDocument();
    expect(screen.queryByRole('button',{name:'Вернуть товар на склад'})).not.toBeInTheDocument();
  });
  it('READY cancellation needs SALES_CANCEL, cancels linked sale once',async()=>{
    access.permissions=['DELIVERIES_READ','SALES_CANCEL'];
    const api=mockBackend();
    renderAt('/deliveries/'+ID);
    await screen.findByRole('heading',{name:/Доставка/});
    expect(screen.queryByRole('button',{name:'Отправить доставку'})).not.toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole('button',{name:'Отменить доставку и продажу'}));
    expect(screen.getByRole('alertdialog')).toHaveTextContent('также отменит связанную продажу');
    await userEvent.setup().click(within(screen.getByRole('alertdialog')).getByRole('button',{name:'Подтвердить действие'}));
    await waitFor(()=>expect(api.counts().saleCancelled).toBe(1));
    expect(api.data[0]?.status).toBe('CANCELLED');
  });
  it('planned date can change and clear without manipulating inventory',async()=>{
    const api=mockBackend();
    renderAt('/deliveries/'+ID);
    await screen.findByRole('heading',{name:/Доставка/});
    fireEvent.change(screen.getByLabelText('Изменить плановую дату'),{target:{value:'2026-10-19'}});
    await userEvent.setup().click(screen.getByRole('button',{name:'Сохранить плановую дату'}));
    await waitFor(()=>expect(api.counts().dateChanges).toBe(1));
    expect(api.data[0]?.plannedDeliveryDate).toBe('2026-10-19');
    await userEvent.setup().click(screen.getByRole('button',{name:'Очистить дату'}));
    await userEvent.setup().click(screen.getByRole('button',{name:'Сохранить плановую дату'}));
    await waitFor(()=>expect(api.counts().dateChanges).toBe(2));
    expect(api.data[0]?.plannedDeliveryDate).toBeNull();
  });
  it('no stock return without INVENTORY_READ and no warehouse query',async()=>{
    access.permissions=['DELIVERIES_READ','DELIVERIES_MANAGE'];
    const api=mockBackend({deliveries:[delivery('FAILED')]});
    renderAt('/deliveries/'+ID);
    await screen.findByRole('heading',{name:/Доставка/});
    expect(screen.getByText(/Требуется INVENTORY_READ/)).toBeInTheDocument();
    expect(screen.getByRole('button',{name:'Вернуть товар на склад'})).toBeDisabled();
    expect(requests(api,'/api/warehouses')).toHaveLength(0);
  });
  it('stale READY state prevents POST dispatch after concurrent cancellation',async()=>{
    const api=mockBackend({mutateBeforeAction:true});
    renderAt('/deliveries/'+ID);
    await screen.findByRole('heading',{name:/Доставка/});
    await userEvent.setup().click(screen.getByRole('button',{name:'Отправить доставку'}));
    await userEvent.setup().click(within(screen.getByRole('alertdialog')).getByRole('button',{name:'Подтвердить действие'}));
    expect(await screen.findByRole('alert')).toHaveTextContent(/изменена другим сотрудником/);
    expect(api.counts().dispatches).toBe(0);
    expect(requests(api,'/api/deliveries/'+ID+'/dispatch')).toHaveLength(0);
  });
  it('restores unknown dispatch after reload and reconciles with one SALE_OUT',async()=>{
    const api=mockBackend({afterDispatchNetwork:true});
    const first=renderAt('/deliveries/'+ID);
    await screen.findByRole('heading',{name:/Доставка/});
    await userEvent.setup().click(screen.getByRole('button',{name:'Отправить доставку'}));
    await userEvent.setup().click(within(screen.getByRole('alertdialog'))
      .getByRole('button',{name:'Подтвердить действие'}));
    expect(await screen.findByText(/Исход операции неизвестен/)).toBeInTheDocument();
    expect(api.counts().dispatches).toBe(1);
    first.unmount();
    renderAt('/deliveries/'+ID);
    await screen.findByRole('heading',{name:/Доставка/});
    expect(await screen.findByText(/Исход операции неизвестен/)).toBeInTheDocument();
    expect(screen.getByRole('button',{name:'Подтвердить вручение'})).toBeDisabled();
    await userEvent.setup().click(screen.getByRole('button',{name:'Проверить актуальное состояние'}));
    expect(await screen.findByText(/операция уже выполнена/)).toBeInTheDocument();
    expect(api.counts().dispatches).toBe(1);
    expect(api.counts().saleOut).toBe(1);
    expect(api.counts().saleFulfilled).toBe(1);
    expect(api.fetchMock.mock.calls.filter(([url,init])=>String(url)==='/api/deliveries/'+ID+'/dispatch'&&
      (init as RequestInit).method==='POST')).toHaveLength(1);
  });
  it('reconciles a lost dispatch after another employee has already marked delivery FAILED',async()=>{
    const api=mockBackend({afterDispatchNetwork:true});
    const first=renderAt('/deliveries/'+ID);
    await screen.findByRole('heading',{name:/Доставка/});
    await userEvent.setup().click(screen.getByRole('button',{name:'Отправить доставку'}));
    await userEvent.setup().click(within(screen.getByRole('alertdialog'))
      .getByRole('button',{name:'Подтвердить действие'}));
    expect(await screen.findByText(/Исход операции неизвестен/)).toBeInTheDocument();
    first.unmount();
    api.data[0]!.status='FAILED';
    api.data[0]!.failureReason='Клиент отсутствует';
    api.data[0]!.failedAt=BASE;
    renderAt('/deliveries/'+ID);
    expect(await screen.findByText(/Исход операции неизвестен/)).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole('button',{name:'Проверить актуальное состояние'}));
    expect(await screen.findByText(/операция уже выполнена/)).toBeInTheDocument();
    expect(api.counts().dispatches).toBe(1);
    expect(api.counts().saleOut).toBe(1);
  });
  it('refuses dispatch if the operation cannot be saved before POST',async()=>{
    const api=mockBackend();
    vi.spyOn(Storage.prototype,'setItem').mockImplementation(()=>{throw new Error('storage unavailable');});
    renderAt('/deliveries/'+ID);
    await screen.findByRole('heading',{name:/Доставка/});
    await userEvent.setup().click(screen.getByRole('button',{name:'Отправить доставку'}));
    await userEvent.setup().click(within(screen.getByRole('alertdialog'))
      .getByRole('button',{name:'Подтвердить действие'}));
    expect(await screen.findByText(/POST не отправлен/)).toBeInTheDocument();
    expect(api.counts().dispatches).toBe(0);
    expect(screen.getByRole('button',{name:'Отправить доставку'})).toBeDisabled();
  });
  it('recovers planned-date change after lost response across page reload',async()=>{
    const api=mockBackend({afterDateNetwork:true});
    const first=renderAt('/deliveries/'+ID);
    await screen.findByRole('heading',{name:/Доставка/});
    fireEvent.change(screen.getByLabelText('Изменить плановую дату'),{target:{value:'2026-10-19'}});
    await userEvent.setup().click(screen.getByRole('button',{name:'Сохранить плановую дату'}));
    expect(await screen.findByText(/Исход операции неизвестен/)).toBeInTheDocument();
    expect(api.counts().dateChanges).toBe(1);
    first.unmount();
    renderAt('/deliveries/'+ID);
    expect(await screen.findByText(/Исход операции неизвестен/)).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole('button',{name:'Проверить актуальное состояние'}));
    expect(await screen.findByText(/Плановая дата проверена/)).toBeInTheDocument();
    expect(api.counts().dateChanges).toBe(1);
  });
  it('backend 403 renders forbidden and does not leak data',async()=>{
    const api=mockBackend({errorOnce:{['GET /api/deliveries/'+ID]:403}});
    renderAt('/deliveries/'+ID);
    expect(await screen.findByRole('heading',{name:'Доступ запрещён'})).toBeInTheDocument();
    expect(requests(api,'/api/sales')).toHaveLength(0);
  });
});
