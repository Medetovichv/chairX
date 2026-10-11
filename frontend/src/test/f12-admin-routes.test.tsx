import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import {render,screen,within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter} from 'react-router';
import {QueryClientProvider} from '@tanstack/react-query';
import {createQueryClient} from '../shared/api/query';
import {authSession} from '../features/auth/session';
import {AppRouter} from '../app/router/AppRouter';
const profile=vi.hoisted(()=>({roles:['ADMIN'] as string[],permissions:['USERS_READ','USERS_CREATE','ROLES_READ','ROLES_CREATE','ROLES_ASSIGN'] as string[]}));
vi.mock('../features/auth/AuthProvider',()=>({useAuth:()=>({
  isAuthenticated:true,status:'authenticated',
  user:{id:'current-id',username:'admin',displayName:'Admin',roles:profile.roles,permissions:profile.permissions},
  hasPermission:(x:string)=>profile.permissions.includes(x),
  hasAnyPermission:(list:readonly string[])=>list.some(x=>profile.permissions.includes(x)),
  login:vi.fn(),logout:vi.fn(),
})}));
const ID='11111111-1111-4111-8111-111111111111';
function json(body:unknown,status=200){return new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json'}});}
function fakeApi(){
 let active=false;
 let roleCodes=['EMPLOYEE'];
 const calls=vi.fn(async(input:RequestInfo|URL,init?:RequestInit)=>{
  const path=new URL(String(input),'http://chairx.test').pathname,method=init?.method??'GET';
  if(path==='/api/admin/users'&&method==='GET')return json([
    {id:ID,username:'cashier1',displayName:'Сотрудник',active,roles:[...roleCodes]}]);
  if(path==='/api/admin/users'&&method==='POST')return json({id:ID,username:'cashier1',displayName:'Сотрудник',active:true,roles:[]},201);
  if(path==='/api/admin/users/'+ID+'/activate'&&method==='PATCH'){
    active=true;return new Response(null,{status:204});
  }
  if(path==='/api/admin/users/'+ID+'/deactivate'&&method==='PATCH'){
    active=false;return new Response(null,{status:204});
  }
  if(path==='/api/admin/users/'+ID+'/reset-password'&&method==='POST')
    return new Response(null,{status:204});
  if(path==='/api/admin/users/'+ID+'/roles'&&method==='POST'){
    const id=JSON.parse(String(init?.body)).roleId;
    const code=id==='role-2'?'CUSTOM_CASH':'EMPLOYEE';
    if(!roleCodes.includes(code))roleCodes.push(code);
    return new Response(null,{status:204});
  }
  if(path.startsWith('/api/admin/users/'+ID+'/roles/')&&method==='DELETE'){
    roleCodes=roleCodes.filter(x=>x!==(path.endsWith('role-2')?'CUSTOM_CASH':'EMPLOYEE'));
    return new Response(null,{status:204});
  }
  if(path==='/api/admin/users/'+ID)
    return json({id:ID,username:'cashier1',displayName:'Сотрудник',active,roles:[...roleCodes]});
  if(path==='/api/admin/roles')return json([
    {id:'role-1',code:'EMPLOYEE',name:'Сотрудник',systemRole:true,permissions:['INVENTORY_READ'],assignedUsersCount:1,version:0},
    {id:'role-2',code:'CUSTOM_CASH',name:'Кассир',systemRole:false,permissions:['PAYMENTS_CREATE'],assignedUsersCount:0,version:2}]);
  if(path==='/api/admin/permissions')return json([
    {code:'INVENTORY_READ',description:'Просмотр остатков',group:'INVENTORY',sensitive:false,adminOnly:false},
    {code:'USERS_ACTIVATE',description:'Активация сотрудников',group:'ADMINISTRATION',sensitive:false,adminOnly:true}]);
  if(path==='/api/admin/audit')return json({items:[],page:0,size:20,total:0});
  return json({code:'NOT_FOUND',message:'not found',details:{}},404);
 });
 vi.stubGlobal('fetch',calls);return calls;
}
function renderAt(url:string){return render(<QueryClientProvider client={createQueryClient()}>
  <MemoryRouter initialEntries={[url]}><AppRouter/></MemoryRouter></QueryClientProvider>);}
beforeEach(()=>{
 profile.roles=['ADMIN'];profile.permissions=['USERS_READ','USERS_CREATE','ROLES_READ','ROLES_CREATE','ROLES_ASSIGN'];
 authSession.activate('Basic fake',{headerName:'X-CSRF-TOKEN',token:'test'});
});
afterEach(()=>{authSession.clear();vi.unstubAllGlobals();});
describe('F12 route access and real data',()=>{
 it('denies ADMIN-like custom roles before private requests',()=>{
  profile.roles=['CUSTOM_ADMIN'];profile.permissions=['USERS_READ','ROLES_READ'];
  const calls=fakeApi();renderAt('/admin/users');
  expect(screen.getByRole('heading',{name:'Доступ запрещён'})).toBeInTheDocument();
  expect(calls).not.toHaveBeenCalled();
 });
 it('lists inactive employees from real API and supports local filtering',async()=>{
  const calls=fakeApi();renderAt('/admin/users');
  expect(await screen.findByText(/cashier1/)).toBeInTheDocument();
  expect(screen.getByText(/Неактивен/)).toBeInTheDocument();
  await userEvent.setup().type(screen.getByRole('searchbox',{name:'Поиск сотрудника'}),'not-found');
  expect(screen.getByText('Сотрудники не найдены.')).toBeInTheDocument();
  expect(calls.mock.calls.some(call=>call[0]==='/api/admin/users')).toBe(true);
 });
 it('creates an employee separately from roles and navigates to the detail',async()=>{
  const calls=fakeApi();renderAt('/admin/users');
  const user=userEvent.setup();
  await screen.findByText(/cashier1/);
  await user.click(screen.getByRole('button',{name:'+ Создать сотрудника'}));
  const form=screen.getByRole('form',{name:'Создание сотрудника'});
  await user.type(within(form).getByRole('textbox',{name:'Логин'}),'cashier1');
  await user.type(within(form).getByRole('textbox',{name:'Имя сотрудника'}),'Сотрудник');
  await user.type(within(form).getByLabelText('Начальный пароль'),'InitialStrong123');
  await user.type(within(form).getByLabelText('Повтор пароля'),'InitialStrong123');
  await user.click(within(form).getByRole('button',{name:'Создать'}));
  expect(await screen.findByRole('heading',{name:'Сотрудник'})).toBeInTheDocument();
  expect(screen.queryByText('UUID: '+ID)).not.toBeInTheDocument();
  const posts=calls.mock.calls.filter(call=>call[1]?.method==='POST');
  expect(posts).toHaveLength(1);
  expect(JSON.parse(String(posts[0]?.[1]?.body))).toEqual({username:'cashier1',displayName:'Сотрудник',password:'InitialStrong123'});
 });

 it('activates an employee and refreshes the displayed status from the API',async()=>{
  profile.permissions=['USERS_READ','USERS_ACTIVATE','USERS_DEACTIVATE'];
  const calls=fakeApi();renderAt('/admin/users/'+ID);
  expect(await screen.findByText('Статус: Неактивен')).toBeInTheDocument();
  await userEvent.setup().click(screen.getByRole('button',{name:'Активировать'}));
  await userEvent.setup().click(within(screen.getByRole('alertdialog')).getByRole('button',{name:'Подтвердить'}));
  expect(await screen.findByText('Статус: Активен')).toBeInTheDocument();
  expect(screen.getByRole('button',{name:'Деактивировать'})).toBeInTheDocument();
  expect(calls.mock.calls.some(c=>String(c[0]).endsWith('/activate')&&c[1]?.method==='PATCH')).toBe(true);
 });
 it('assigns another role and removes only that role without losing the first',async()=>{
  profile.permissions=['USERS_READ','USERS_ACTIVATE','ROLES_READ','ROLES_ASSIGN'];
  const calls=fakeApi();renderAt('/admin/users/'+ID);
  await screen.findByText('Роли: EMPLOYEE');
  const user=userEvent.setup();
  await user.click(screen.getByRole('button',{name:'Активировать'}));
  await user.click(within(screen.getByRole('alertdialog')).getByRole('button',{name:'Подтвердить'}));
  await screen.findByText('Статус: Активен');
  await screen.findByRole('option',{name:'Кассир (CUSTOM_CASH)'});
  await user.selectOptions(screen.getByRole('combobox',{name:'Назначаемая роль'}),'role-2');
  await user.click(screen.getByRole('button',{name:'Назначить'}));
  expect(await screen.findByText('Роли: EMPLOYEE, CUSTOM_CASH')).toBeInTheDocument();
  const remove=within(screen.getByRole('region',{name:'Управление ролями'}))
    .getAllByRole('button',{name:'Снять роль'});
  await user.click(remove[1]!);
  await user.click(within(screen.getByRole('alertdialog')).getByRole('button',{name:'Подтвердить'}));
  expect(await screen.findByText('Роли: EMPLOYEE')).toBeInTheDocument();
  const post=calls.mock.calls.find(c=>c[1]?.method==='POST'&&String(c[0]).endsWith('/roles'));
  expect(JSON.parse(String(post?.[1]?.body))).toEqual({roleId:'role-2'});
  expect(calls.mock.calls.some(c=>c[1]?.method==='DELETE'&&String(c[0]).endsWith('/roles/role-2'))).toBe(true);
 });
 it('allows confirmed password reset without showing or retaining entered password',async()=>{
  profile.permissions=['USERS_READ','USERS_RESET_PASSWORD'];
  const calls=fakeApi();renderAt('/admin/users/'+ID);
  await screen.findByRole('heading',{name:'Сброс пароля'});
  const user=userEvent.setup();
  const pass=screen.getByLabelText('Новый пароль');
  expect(pass).toHaveAttribute('type','password');
  await user.type(pass,'StrongResetPass123');
  await user.type(screen.getByLabelText('Подтверждение нового пароля'),'StrongResetPass123');
  await user.click(screen.getByRole('button',{name:'Сбросить пароль'}));
  await user.click(within(screen.getByRole('alertdialog')).getByRole('button',{name:'Сбросить'}));
  expect(await screen.findByText(/Новый пароль установлен/)).toBeInTheDocument();
  expect(pass).toHaveValue('');
  const req=calls.mock.calls.find(c=>c[1]?.method==='POST'&&String(c[0]).endsWith('/reset-password'));
  expect(JSON.parse(String(req?.[1]?.body))).toEqual({newPassword:'StrongResetPass123'});
 });
 it('loads dynamic custom roles and catalog permission options',async()=>{
  fakeApi();renderAt('/admin/roles');
  expect(await screen.findByText(/Кассир \(CUSTOM_CASH\)/)).toBeInTheDocument();
  await userEvent.setup().click(screen.getByRole('button',{name:'+ Создать роль'}));
  const box=screen.getByRole('region',{name:'Новая роль'});
  expect(within(box).getByRole('textbox',{name:'Код роли'})).toBeInTheDocument();
  expect(await screen.findByText('Просмотр остатков')).toBeInTheDocument();
  expect(screen.getByText('Недоступно пользовательским ролям')).toBeInTheDocument();
 });
 it('hides write controls for admin without corresponding write permission',async()=>{
  profile.permissions=['USERS_READ','ROLES_READ'];
  fakeApi();renderAt('/admin/users');
  await screen.findByText(/cashier1/);
  expect(screen.queryByRole('button',{name:'+ Создать сотрудника'})).not.toBeInTheDocument();
 });
});
