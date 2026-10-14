import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { QueryClientProvider } from '@tanstack/react-query';
import { createQueryClient } from '../shared/api/query';
import { authSession } from '../features/auth/session';
import { DailyClosingPage } from '../pages/DailyClosingPage';
import { bishkekToday } from '../features/finance/validation';
import { closingInputIssue, validClosingDate } from '../features/daily-closing/validation';
import { postVerifiedClosing } from '../features/daily-closing/verified-close';
import type { ClosingAccess, ClosingReport, CompletedSales } from '../features/daily-closing/types';

const access = vi.hoisted(() => ({ permissions: ['FINANCE_READ'] as string[] }));
vi.mock('../features/auth/AuthProvider', () => ({
  useAuth: () => ({
    user: {id:'operator-f11',username:'f11',displayName:'Operator',
      roles:['ADMIN'],permissions:access.permissions},
    hasPermission: (name:string) => access.permissions.includes(name),
  }),
}));
const day=bishkekToday();
const account=(expected:string,actual=expected)=>({
  expected,actual,difference:'0',note:null,
  originalExpected:expected,recalculated:false,requiresReview:false,
});
function report():ClosingReport {
  return {id:'55555555-5555-4555-8555-555555555555',businessDate:day,
    createdAt:'2026-10-11T02:00:00Z',createdBy:'f11',version:0,
    cash:account('5000'),bank:account('2000')};
}
function accessResult(exists:boolean,canEdit=false):ClosingAccess {
  return {businessDate:day,reportExists:exists,reportStatus:exists?'CLOSED':'NOT_CREATED',
    accessStatus:canEdit?'OPEN':'LOCKED',canEdit,canUnlock:false,
    unlockedUntil:null,remainingSeconds:0};
}
function salesResult(closed=false,available=false):CompletedSales {
  return {businessDate:day,closed,snapshotAvailable:available,preliminary:!closed,
    items:[],totals:{orders:closed&&!available?null:0,chairs:closed&&!available?null:0,
      value:closed&&!available?null:'0'},lateCompletionCount:null};
}
const json=(data:unknown,status=200)=>new Response(JSON.stringify(data),
  {status,headers:{'Content-Type':'application/json'}});
function view(){
  return render(<QueryClientProvider client={createQueryClient()}>
    <MemoryRouter><DailyClosingPage/></MemoryRouter>
  </QueryClientProvider>);
}
beforeEach(()=>{
  access.permissions=['FINANCE_READ'];
  sessionStorage.clear();
  authSession.activate('Basic f11-test',{headerName:'X-CSRF-TOKEN',token:'csrf'});
});
afterEach(()=>{
  authSession.clear();sessionStorage.clear();vi.unstubAllGlobals();vi.restoreAllMocks();
});
describe('F11 Daily Closing backend contract and guarded workflow',()=>{
 it('requires exact whole som and an explanation for each CASH/BANK discrepancy',()=>{
  const preview={businessDate:day,expectedCash:'5000',expectedBank:'2000'};
  const draft={actualCash:'4900',actualBank:'2000',cashNote:null,bankNote:null};
  expect(closingInputIssue(draft,preview)).toMatch(/CASH/);
  expect(closingInputIssue({...draft,cashNote:'По пересчёту'},preview)).toBeNull();
  expect(closingInputIssue({...draft,actualCash:'5000',actualBank:'1999'},preview)).toMatch(/BANK/);
  expect(closingInputIssue({...draft,actualCash:'5000',actualBank:'2000'},preview)).toBeNull();
  expect(closingInputIssue({...draft,actualCash:'5.50'},preview)).toMatch(/целыми/);
  expect(closingInputIssue({...draft,actualCash:'0',actualBank:'0'},undefined)).toMatch(/backend/);
  expect(validClosingDate('2099-01-01',day)).toBe(false);
  expect(validClosingDate(day,day)).toBe(true);
 });
 it('reads company report only with FINANCE_READ and never invents an old sales snapshot',async()=>{
  const calls:string[]=[];
  vi.stubGlobal('fetch',vi.fn(async(url:RequestInfo|URL)=>{
    const path=String(url);calls.push(path);
    if(path==='/api/finance/closings')return json([day]);
    if(path.endsWith('/access'))return json(accessResult(true));
    if(path.endsWith('/sales'))return json(salesResult(true,false));
    if(path==='/api/finance/closings/'+day)return json(report());
    throw Error('Unexpected protected GET '+path);
  }));
  view();
  expect(await screen.findByText(/День закрыт/)).toBeInTheDocument();
  expect(await screen.findByText(/нет сохранённого снимка продаж/)).toBeInTheDocument();
  expect(screen.queryByRole('button',{name:'Завершить финансовый день'})).not.toBeInTheDocument();
  expect(calls.every(path=>path.startsWith('/api/finance/closings'))).toBe(true);
 });
 it('does not send GET without report read permission',()=>{
  access.permissions=[];
  const fetchMock=vi.fn();vi.stubGlobal('fetch',fetchMock);
  view();
  expect(screen.getByText('Доступ запрещён')).toBeInTheDocument();
  expect(fetchMock).not.toHaveBeenCalled();
 });
 it('confirms calculated balances once without manual CASH/BANK input',async()=>{
  access.permissions=['FINANCE_READ','DAILY_CLOSING_WRITE'];
  let closed=false;const posts:Record<string,unknown>[]=[];
  vi.stubGlobal('fetch',vi.fn(async(url:RequestInfo|URL,init?:RequestInit)=>{
    const path=String(url);
    if(path==='/api/finance/closings'&&(!init||init.method==='GET'))return json(closed?[day]:[]);
    if(path.endsWith('/access'))return json(accessResult(closed,!closed));
    if(path.endsWith('/preview'))return json({businessDate:day,expectedCash:'5000',expectedBank:'2000'});
    if(path.endsWith('/sales'))return json(salesResult(closed,closed));
    if(path==='/api/finance/closings/'+day&&init?.method==='POST'){
      posts.push(JSON.parse(String(init.body)));closed=true;return json(report(),201);
    }
    if(path==='/api/finance/closings/'+day&&(!init||init.method==='GET'))return json(report());
    throw Error('Unexpected '+path);
  }));
  view();
  const finish=await screen.findByRole('button',{name:'Завершить финансовый день'});
  await waitFor(()=>expect(finish).toBeEnabled());
  expect(screen.queryByRole('textbox',{name:'Факт CASH'})).not.toBeInTheDocument();
  await userEvent.setup().click(finish);
  expect(screen.getByRole('alertdialog')).toHaveTextContent(day);
  await userEvent.setup().click(within(screen.getByRole('alertdialog'))
    .getByRole('button',{name:'Зафиксировать отчёт'}));
  await waitFor(()=>expect(posts).toHaveLength(1));
  expect(posts[0]).toEqual({actualCash:'5000',actualBank:'2000',cashNote:null,bankNote:null});
  expect(await screen.findByText(/День закрыт/)).toBeInTheDocument();
  expect(screen.queryByRole('button',{name:'Завершить финансовый день'})).not.toBeInTheDocument();
 });

 it('does not POST after displayed CASH/BANK expectation changed in a concurrent session',async()=>{
  access.permissions=['FINANCE_READ','DAILY_CLOSING_WRITE'];
  const calls:string[]=[]; const posts:string[]=[];
  vi.stubGlobal('fetch',vi.fn(async(url:RequestInfo|URL,init?:RequestInit)=>{
    const path=String(url);calls.push(path);
    if(init?.method==='POST'){posts.push(path);return json(report(),201);}
    if(path.endsWith('/access'))return json(accessResult(false,true));
    if(path.endsWith('/preview'))return json({
      businessDate:day,expectedCash:'5100',expectedBank:'2000',
    });
    throw Error('Unexpected '+path);
  }));
  await expect(postVerifiedClosing(day,{
    actualCash:'5000',actualBank:'2000',cashNote:null,bankNote:null,
    approvedExpectedCash:'5000',approvedExpectedBank:'2000',
  })).rejects.toMatchObject({code:'CLOSING_EXPECTED_BALANCE_CHANGED',status:400});
  expect(posts).toHaveLength(0);
  expect(calls).toContain('/api/finance/closings/'+day+'/preview');
 });
 it('does not POST when access changes to closed after the operator opened confirmation',async()=>{
  access.permissions=['FINANCE_READ','DAILY_CLOSING_WRITE'];
  const posts:string[]=[];
  vi.stubGlobal('fetch',vi.fn(async(url:RequestInfo|URL,init?:RequestInit)=>{
    const path=String(url);
    if(init?.method==='POST'){posts.push(path);return json(report(),201);}
    if(path.endsWith('/access'))return json(accessResult(true,false));
    if(path.endsWith('/preview'))return json({
      businessDate:day,expectedCash:'5000',expectedBank:'2000',
    });
    throw Error('Unexpected '+path);
  }));
  await expect(postVerifiedClosing(day,{
    actualCash:'5000',actualBank:'2000',cashNote:null,bankNote:null,
    approvedExpectedCash:'5000',approvedExpectedBank:'2000',
  })).rejects.toMatchObject({code:'CLOSING_PREFLIGHT_DENIED_OR_CHANGED',status:400});
  expect(posts).toHaveLength(0);
 });
 it('never sends closing POST when the preflight network request fails',async()=>{
  const post=vi.fn();
  vi.stubGlobal('fetch',vi.fn(async(_url:RequestInfo|URL,init?:RequestInit)=>{
    if(init?.method==='POST')return post();
    throw Error('Connection lost before POST');
  }));
  await expect(postVerifiedClosing(day,{
    actualCash:'5000',actualBank:'2000',cashNote:null,bankNote:null,
    approvedExpectedCash:'5000',approvedExpectedBank:'2000',
  })).rejects.toMatchObject({code:'CLOSING_PREFLIGHT_UNAVAILABLE',status:400});
  expect(post).not.toHaveBeenCalled();
 });
 it('shows revised expectation and explicitly requires reconciliation',async()=>{
  vi.stubGlobal('fetch',vi.fn(async(url:RequestInfo|URL)=>{
    const path=String(url);
    if(path==='/api/finance/closings')return json([day]);
    if(path.endsWith('/access'))return json(accessResult(true));
    if(path.endsWith('/sales'))return json(salesResult(true,true));
    if(path==='/api/finance/closings/'+day)return json({
      ...report(),cash:{...account('4500','5000'),difference:'500',originalExpected:'5000',
        recalculated:true,requiresReview:true,note:'Первичный пересчёт'},
    });
    throw Error('Unexpected '+path);
  }));
  view();
  expect(await screen.findByText(/Требуется сверка/)).toBeInTheDocument();
  expect(screen.getByText(/Исходное при закрытии/)).toHaveTextContent('5');
 });
 it('blocks closing if date access denies the operation',async()=>{
  access.permissions=['FINANCE_READ','DAILY_CLOSING_WRITE'];
  const posts:string[]=[];
  vi.stubGlobal('fetch',vi.fn(async(url:RequestInfo|URL,init?:RequestInit)=>{
    const path=String(url);
    if(init?.method==='POST')posts.push(path);
    if(path==='/api/finance/closings')return json([]);
    if(path.endsWith('/access'))return json(accessResult(false,false));
    if(path.endsWith('/preview'))return json({businessDate:day,expectedCash:'5000',expectedBank:'2000'});
    if(path.endsWith('/sales'))return json(salesResult());
    throw Error('Unexpected '+path);
  }));
  view();
  const finish=await screen.findByRole('button',{name:'Завершить финансовый день'});
  expect(finish).toBeDisabled();
  expect(posts).toHaveLength(0);
 });
});
