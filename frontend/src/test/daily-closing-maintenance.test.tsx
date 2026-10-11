import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent, within, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { QueryClientProvider } from '@tanstack/react-query';
import { createQueryClient } from '../shared/api/query';
import { authSession } from '../features/auth/session';
import { ClosingMaintenance, closingUpdateIssue } from '../features/daily-closing/ClosingMaintenance';
import { bishkekToday } from '../features/finance/validation';
import type { ClosingAccess, ClosingReport } from '../features/daily-closing/types';

const permissions=vi.hoisted(()=>({list:[] as string[]}));
vi.mock('../features/auth/AuthProvider',()=>({
  useAuth:()=>({
    user:{id:'f11-manager',username:'f11',displayName:'F11 manager',
      roles:['CUSTOM_ROLE'],permissions:permissions.list},
    hasPermission:(p:string)=>permissions.list.includes(p),
  }),
}));
const day=bishkekToday();
const json=(v:unknown,status=200)=>new Response(JSON.stringify(v),
  {status,headers:{'Content-Type':'application/json'}});
const report:ClosingReport={
  id:'44444444-4444-4444-8444-444444444444',businessDate:day,
  createdAt:'2026-10-11T02:00:00Z',createdBy:'employee',version:7,
  cash:{expected:'5000',actual:'5000',difference:'0',note:null,
    originalExpected:'5000',recalculated:false,requiresReview:false},
  bank:{expected:'2000',actual:'2000',difference:'0',note:null,
    originalExpected:'2000',recalculated:false,requiresReview:false},
};
const access:ClosingAccess={
  businessDate:day,reportExists:true,reportStatus:'CLOSED',
  accessStatus:'OPEN',canEdit:true,canUnlock:false,
  unlockedUntil:null,remainingSeconds:0,
};
function mount(state=access,document:ClosingReport|undefined=report) {
  return render(<QueryClientProvider client={createQueryClient()}>
    <MemoryRouter><ClosingMaintenance date={day} access={state}
      report={document} onChanged={async()=>{}}/></MemoryRouter>
  </QueryClientProvider>);
}
beforeEach(()=>{
  permissions.list=[];
  sessionStorage.clear();
  authSession.activate('Basic f11-maintenance',{headerName:'X-CSRF-TOKEN',token:'csrf'});
});
afterEach(()=>{
  authSession.clear();sessionStorage.clear();vi.unstubAllGlobals();vi.restoreAllMocks();
});
describe('F11 maintenance: RBAC, optimistic edit and corrections',()=>{
  it('rejects stale version, blank reason and unnoted original or effective discrepancies',()=>{
    const base={expectedVersion:7,actualCash:'5000',actualBank:'2000',
      cashNote:null,bankNote:null,reason:'Исправлена ошибка подсчёта'};
    expect(closingUpdateIssue(base,report)).toBeNull();
    expect(closingUpdateIssue({...base,expectedVersion:6},report)).toMatch(/версия/);
    expect(closingUpdateIssue({...base,reason:''},report)).toMatch(/причина/);
    expect(closingUpdateIssue({...base,actualCash:'4800'},report)).toMatch(/CASH/);
    expect(closingUpdateIssue({...base,actualCash:'4800',cashNote:'Недостача'},report)).toBeNull();
    const revised={...report,cash:{...report.cash,expected:'4800',recalculated:true}};
    expect(closingUpdateIssue(base,revised)).toMatch(/CASH/);
  });
  it('does not disclose audit events and avoids privileged GET without audit permission',()=>{
    permissions.list=['DAILY_CLOSING_READ'];
    const request=vi.fn();vi.stubGlobal('fetch',request);
    mount();
    expect(screen.queryByText('Аудит закрытия')).not.toBeInTheDocument();
    expect(request).not.toHaveBeenCalled();
  });
  it('sends the exact current version and mandatory reason to the existing PUT endpoint',async()=>{
    permissions.list=['DAILY_CLOSING_WRITE'];
    const updates:Record<string,unknown>[]=[];
    vi.stubGlobal('fetch',vi.fn(async(url:RequestInfo|URL,init?:RequestInit)=>{
      if(String(url)==='/api/finance/closings/'+day&&init?.method==='PUT'){
        const payload=JSON.parse(String(init.body)) as Record<string,unknown>;
        updates.push(payload);return json({...report,version:8});
      }
      throw Error('Unexpected '+String(url));
    }));
    mount();
    const form=screen.getByRole('form',{name:'Исправление закрытия'});
    fireEvent.change(within(form).getByRole('textbox',{name:'Причина исправления отчёта'}),
      {target:{value:'Исправили акт пересчёта'}});
    await userEvent.setup().click(within(form).getByRole('button',{name:'Проверить изменение'}));
    expect(screen.getByRole('alertdialog')).toHaveTextContent('версия 7');
    await userEvent.setup().click(within(screen.getByRole('alertdialog'))
      .getByRole('button',{name:'Сохранить исправление'}));
    await waitFor(()=>expect(updates).toHaveLength(1));
    expect(updates[0]).toEqual({
      expectedVersion:7,actualCash:'5000',actualBank:'2000',
      cashNote:null,bankNote:null,reason:'Исправили акт пересчёта',
    });
  });
  it('unlocks only a backend-eligible date and sends reason, not a client-selected expiry',async()=>{
    permissions.list=['DAILY_CLOSING_UNLOCK_MANAGER'];
    const grants:Record<string,unknown>[]=[];
    const locked={...access,reportExists:false,reportStatus:'NOT_CREATED' as const,
      canEdit:false,canUnlock:true,accessStatus:'LOCKED' as const};
    vi.stubGlobal('fetch',vi.fn(async(url:RequestInfo|URL,init?:RequestInit)=>{
      if(String(url)==='/api/finance/closings/'+day+'/unlock'&&init?.method==='POST'){
        grants.push(JSON.parse(String(init.body)));
        return json({id:'55555555-5555-4555-8555-555555555555',
          businessDate:day,expiresAt:'2026-10-11T12:00:00Z'});
      }
      throw Error('Unexpected '+String(url));
    }));
    mount(locked,undefined);
    fireEvent.change(screen.getByRole('textbox',{name:'Причина разблокировки'}),
      {target:{value:'Поздний документ'}});
    await userEvent.setup().click(screen.getByRole('button',{name:'Запросить разблокировку'}));
    await userEvent.setup().click(within(screen.getByRole('alertdialog'))
      .getByRole('button',{name:'Разблокировать'}));
    await waitFor(()=>expect(grants).toHaveLength(1));
    expect(grants[0]).toEqual({reason:'Поздний документ'});
  });
  it('shows history only to DAILY_CLOSING_AUDIT_READ and never reveals before/after to ordinary users',async()=>{
    permissions.list=['DAILY_CLOSING_AUDIT_READ'];
    const calls:string[]=[];
    vi.stubGlobal('fetch',vi.fn(async(url:RequestInfo|URL)=>{
      calls.push(String(url));
      return json([{action:'REPORT_UPDATED',actor:'manager',
        at:'2026-10-11T02:00:00Z',before:'{}',after:'{}'}]);
    }));
    mount();
    expect(await screen.findByText(/REPORT_UPDATED/)).toBeInTheDocument();
    expect(calls).toEqual(['/api/finance/closings/'+day+'/history']);
  });
  it('uses a stable idempotencyKey and exact day for a documented late expense',async()=>{
    permissions.list=['DAILY_CLOSING_WRITE','DAILY_CLOSING_EXPENSE_CORRECT'];
    const posts:Record<string,unknown>[]=[];
    vi.stubGlobal('fetch',vi.fn(async(url:RequestInfo|URL,init?:RequestInit)=>{
      if(String(url)==='/api/finance/closings/'+day+'/expenses'&&init?.method==='POST'){
        const body=JSON.parse(String(init.body)) as Record<string,unknown>;
        posts.push(body);return json({id:'77777777-7777-4777-8777-777777777777',...body,
          createdBy:'f11',createdAt:'2026-10-11T02:00:00Z'},201);
      }
      throw Error('Unexpected '+String(url));
    }));
    mount();
    const form=screen.getByRole('form',{name:'Расход за закрытую дату'});
    fireEvent.change(within(form).getByRole('textbox',{name:'Сумма позднего расхода'}),
      {target:{value:'1500'}});
    await userEvent.setup().click(within(form).getByRole('button',{name:'Проверить поздний расход'}));
    expect(screen.getByRole('alertdialog')).toHaveTextContent(day);
    await userEvent.setup().click(within(screen.getByRole('alertdialog'))
      .getByRole('button',{name:'Провести расход'}));
    await waitFor(()=>expect(posts).toHaveLength(1));
    expect(posts[0]).toMatchObject({expenseDate:day,amount:'1500',
      paymentMethod:'CASH',category:'OTHER'});
    expect(posts[0]?.idempotencyKey).toMatch(/^[0-9a-f-]{36}$/);
  });
});
