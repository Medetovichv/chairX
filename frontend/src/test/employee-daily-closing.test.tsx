import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import {render,screen,within,waitFor,fireEvent} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter} from 'react-router';
import {QueryClientProvider} from '@tanstack/react-query';
import {createQueryClient} from '../shared/api/query';
import {DailyClosingPage} from '../pages/DailyClosingPage';
import {authSession} from '../features/auth/session';
import {bishkekToday} from '../features/finance/validation';
const perms=vi.hoisted(()=>({values:['DAILY_CLOSING_READ','DAILY_CLOSING_WRITE'] as string[],roles:['EMPLOYEE'] as string[]}));
vi.mock('../features/auth/AuthProvider',()=>({
 useAuth:()=>({user:{id:'mine',username:'alice',displayName:'Alice',
     roles:perms.roles,permissions:perms.values},
     hasPermission:(name:string)=>perms.values.includes(name)}),
}));
const day=bishkekToday();
const reply=(data:unknown,status=200)=>new Response(JSON.stringify(data),
  {status,headers:{'Content-Type':'application/json'}});
const personal=(closed=false)=>({
 businessDate:day,closed,canClose:!closed,
 expectedCash:'300',expectedBank:'0',
 actualCash:null,actualBank:null,expectedTotal:'300',actualTotal:closed?'300':null,totalNote:null,
 cashNote:null,bankNote:null,orders:1,chairs:2,salesValue:'12000',
 sales:[{saleId:'one',saleNumber:'CX-1',quantity:2,total:'12000',
  completedAt:'2026-10-11T03:00:00Z'}],expenses:[],expenseCash:'0',expenseBank:'0',collectionsChangedAfterClose:false,
});
const renderPage=()=>render(<QueryClientProvider client={createQueryClient()}>
 <MemoryRouter><DailyClosingPage/></MemoryRouter>
</QueryClientProvider>);
beforeEach(()=>{
 perms.values=['DAILY_CLOSING_READ','DAILY_CLOSING_WRITE'];perms.roles=['EMPLOYEE'];
 authSession.activate('Basic test-private',{headerName:'X-CSRF-TOKEN',token:'csrf'});
 sessionStorage.clear();
});
afterEach(()=>{authSession.clear();vi.unstubAllGlobals();vi.restoreAllMocks();sessionStorage.clear();});
describe('Employee daily closing isolation',()=>{
 it('never requests global company balances, reports or other employees on read',async()=>{
  const urls:string[]=[];
  vi.stubGlobal('fetch',vi.fn(async(url:RequestInfo|URL)=>{
   const path=String(url); urls.push(path);
   if(path==='/api/finance/my-closings')return reply([]);
   if(path==='/api/finance/my-closings/'+day)return reply(personal());
   throw Error('Private screen must not request '+path);
  }));
  renderPage();
  expect(await screen.findByText('Закрытие дня')).toBeInTheDocument();
  expect(await screen.findByText(/Расчётная сумма к подтверждению/)).toBeInTheDocument();
  expect(screen.getAllByText(/CX-1/).length).toBeGreaterThan(0);
  expect(urls.every(u=>u.startsWith('/api/finance/my-closings'))).toBe(true);
 });
 it('shows qualifying sales before expenses and the close confirmation',async()=>{
  vi.stubGlobal('fetch',vi.fn(async(url:RequestInfo|URL)=>{
   const path=String(url);
   if(path==='/api/finance/my-closings')return reply([]);
   if(path==='/api/finance/my-closings/'+day)return reply(personal());
   throw Error('Unexpected '+path);
  }));
  renderPage();
  const sales=await screen.findByRole('table',{name:'Оплаченные выданные товары дня'});
  const expenses=screen.getByRole('region',{name:'Расходы сотрудников за день'});
  const confirm=screen.getByRole('form',{name:'Операционное закрытие дня'});
  expect(sales.compareDocumentPosition(expenses)&Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  expect(expenses.compareDocumentPosition(confirm)&Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
 });
 it('renders mobile order cards, four summaries and filters by phone',async()=>{
  const fixture={...personal(),sales:[{saleId:'one',saleNumber:'CX-1',
   quantity:2,total:'12000',phone:'+996555123456',products:'Комфорт / Чёрный',
   deliveryStatus:'DELIVERED',completedAt:'2026-10-11T03:00:00Z'}]};
  vi.stubGlobal('fetch',vi.fn(async(url:RequestInfo|URL)=>{
   const path=String(url);
   if(path==='/api/finance/my-closings')return reply([]);
   if(path==='/api/finance/my-closings/'+day)return reply(fixture);
   throw Error('Unexpected '+path);
  }));
  renderPage();
  expect(await screen.findByRole('region',{name:'Показатели дня'})).toBeInTheDocument();
  const cards=screen.getByLabelText('Карточки продаж');
  expect(within(cards).getByText('CX-1')).toBeInTheDocument();
  expect(within(cards).getByText('Комфорт / Чёрный · 2 шт.')).toBeInTheDocument();
  fireEvent.change(screen.getByRole('textbox',{name:'Поиск продаж'}),
   {target:{value:'+996555123456'}});
  expect(within(cards).getByText('CX-1')).toBeInTheDocument();
  fireEvent.change(screen.getByRole('textbox',{name:'Поиск продаж'}),
   {target:{value:'not found'}});
  expect(within(cards).queryByText('CX-1')).not.toBeInTheDocument();
 });
 it('shows fully paid regional deliveries in READY and IN_TRANSIT with fees and full-payment date',async()=>{
  const fixture={...personal(),orders:2,chairs:2,salesValue:'18500',
   sales:[
    {saleId:'region-ready',saleNumber:'CX-REG-READY',quantity:1,
     total:'9000',goodsTotal:'8500',deliveryTotal:'500',
     fulfillment:'REGION_DELIVERY',deliveryStatus:'READY',paymentStatus:'PAID',
     products:'Кресло А',phone:'+996700000001',address:'Бишкек',
     completedAt:'2026-10-10T18:30:00Z'},
    {saleId:'region-transit',saleNumber:'CX-REG-TRANSIT',quantity:1,
     total:'9500',goodsTotal:'8500',deliveryTotal:'1000',
     fulfillment:'REGION_DELIVERY',deliveryStatus:'IN_TRANSIT',paymentStatus:'PAID',
     products:'Кресло Б',phone:'+996700000002',address:'Ош',
     completedAt:'2026-10-11T03:00:00Z'},
   ]};
  vi.stubGlobal('fetch',vi.fn(async(url:RequestInfo|URL)=>{
   const path=String(url);
   if(path==='/api/finance/my-closings')return reply([]);
   if(path==='/api/finance/my-closings/'+day)return reply(fixture);
   throw Error('Unexpected '+path);
  }));
  renderPage();
  const table=await screen.findByRole('table',{name:'Оплаченные выданные товары дня'});
  const ready=within(table).getByRole('row',{name:/CX-REG-READY/});
  const transit=within(table).getByRole('row',{name:/CX-REG-TRANSIT/});
  expect(ready).toHaveTextContent('Готовится к отправке');
  expect(ready).toHaveTextContent('8 500 сом');
  expect(ready).toHaveTextContent('500 сом');
  expect(ready).toHaveTextContent('9 000 сом');
  expect(ready).toHaveTextContent('Полная оплата: 11.10.2026');
  expect(transit).toHaveTextContent('В пути');
  expect(transit).toHaveTextContent('1 000 сом');
  expect(transit).toHaveTextContent('9 500 сом');
  expect(transit).toHaveTextContent('Полная оплата: 11.10.2026');
  const cards=screen.getByLabelText('Карточки продаж');
  expect(within(cards).getByText('CX-REG-READY').closest('article')).toHaveTextContent('Готовится к отправке');
  expect(within(cards).getByText('CX-REG-TRANSIT').closest('article')).toHaveTextContent('Полная оплата: 11.10.2026');
 });
 it('supports custom role grants for operational closing without requiring EMPLOYEE role name',async()=>{
  perms.roles=['CUSTOM_DAILY_OPERATOR'];
  vi.stubGlobal('fetch',vi.fn(async(url:RequestInfo|URL)=>{
   const path=String(url);
   if(path==='/api/finance/my-closings')return reply([]);
   if(path==='/api/finance/my-closings/'+day)return reply(personal());
   throw Error('Unexpected '+path);
  }));
  renderPage();
  expect(await screen.findByRole('form',{name:'Операционное закрытие дня'})).toBeInTheDocument();
 });
 it('keeps the expense creation form collapsed until explicitly requested',async()=>{
  perms.values=['DAILY_CLOSING_READ','DAILY_CLOSING_WRITE','EXPENSES_CREATE'];
  vi.stubGlobal('fetch',vi.fn(async(url:RequestInfo|URL)=>{
   const path=String(url);
   if(path==='/api/finance/my-closings')return reply([]);
   if(path==='/api/finance/my-closings/'+day)return reply(personal());
   throw Error('Unexpected '+path);
  }));
  renderPage();
  const button=await screen.findByRole('button',{name:'+ Добавить расход'});
  expect(screen.queryByRole('form',{name:'Добавить расход при закрытии'})).not.toBeInTheDocument();
  await userEvent.setup().click(button);
  expect(screen.getByRole('form',{name:'Добавить расход при закрытии'})).toBeInTheDocument();
  await userEvent.setup().click(screen.getByRole('button',{name:'Свернуть форму'}));
  expect(screen.queryByRole('form',{name:'Добавить расход при закрытии'})).not.toBeInTheDocument();
 });
 it('checks for races before one personal POST with no company mutations',async()=>{
  const requests:{path:string;method:string;body?:string}[]=[];
  let closed=false;
  vi.stubGlobal('fetch',vi.fn(async(url:RequestInfo|URL,init?:RequestInit)=>{
   const path=String(url);const method=init?.method??'GET';
   requests.push({path,method,body:String(init?.body??'')});
   if(path==='/api/finance/my-closings')return reply(closed?[day]:[]);
   if(path==='/api/finance/my-closings/'+day&&method==='GET')return reply(personal(closed));
   if(path==='/api/finance/my-closings/'+day&&method==='POST'){
    closed=true;return reply(personal(true),201);
   }
   throw Error('Unexpected '+path);
  }));
  renderPage();
  const form=await screen.findByRole('form',{name:'Операционное закрытие дня'});
  fireEvent.change(within(form).getByRole('textbox',{name:'Фактическая общая сумма'}),{target:{value:'300'}});
  await userEvent.setup().click(within(form).getByRole('button',{name:'Подтвердить и закрыть день'}));
  expect(screen.getByRole('alertdialog')).toBeInTheDocument();
  await userEvent.setup().click(within(screen.getByRole('alertdialog'))
   .getByRole('button',{name:'Закрыть операционный день'}));
  await waitFor(()=>expect(requests.filter(r=>r.method==='POST')).toHaveLength(1));
  const post=requests.find(r=>r.method==='POST')!;
  expect(post.path).toBe('/api/finance/my-closings/'+day);
  expect(JSON.parse(post.body || '')).toEqual({
   expectedCash:'300',expectedBank:'0',actualCash:null,actualBank:null,
   cashNote:null,bankNote:null,actualTotal:'300',totalNote:null,
  });
  expect(requests.some(r=>r.path.startsWith('/api/finance/closings'))).toBe(false);
  expect(await screen.findByText('Операционный день закрыт')).toBeInTheDocument();
 });
 it('stops before POST when amount changed during preflight',async()=>{
  let n=0;const posts:string[]=[];
  vi.stubGlobal('fetch',vi.fn(async(url:RequestInfo|URL,init?:RequestInit)=>{
   const path=String(url);
   if(init?.method==='POST'){posts.push(path);return reply(personal(true),201);}
   if(path==='/api/finance/my-closings')return reply([]);
   if(path==='/api/finance/my-closings/'+day){
    n++;return reply(n>=2?{...personal(),expectedCash:'400'}:personal());
   }
   throw Error('Unexpected '+path);
  }));
  renderPage();
  const form=await screen.findByRole('form',{name:'Операционное закрытие дня'});
  fireEvent.change(within(form).getByRole('textbox',{name:'Фактическая общая сумма'}),{target:{value:'300'}});

  await userEvent.setup().click(within(form).getByRole('button',{name:'Подтвердить и закрыть день'}));
  await userEvent.setup().click(within(screen.getByRole('alertdialog'))
    .getByRole('button',{name:'Закрыть операционный день'}));
  await waitFor(()=>expect(screen.getByRole('alert')).toHaveTextContent('Данные изменились'));
  expect(posts).toHaveLength(0);
 });
 it('does not fetch the personal report without the required grant',()=>{
  perms.values=[];
  const fetchMock=vi.fn();vi.stubGlobal('fetch',fetchMock);
  renderPage();
  expect(screen.getByText('Доступ запрещён')).toBeInTheDocument();
  expect(fetchMock).not.toHaveBeenCalled();
 });
});
