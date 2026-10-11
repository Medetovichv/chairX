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
 const calls=vi.fn(async(input:RequestInfo|URL,init?:RequestInit)=>{
  const path=String(input).split('?')[0],method=init?.method??'GET';
  if(path==='/api/admin/users'&&method==='GET')return json([
    {id:ID,username:'cashier1',displayName:'Сотрудник',active:false,roles:['EMPLOYEE']}]);
  if(path==='/api/admin/users'&&method==='POST')return json({id:ID,username:'cashier1',displayName:'Сотрудник',active:true,roles:[]},201);
  if(path==='/api/admin/users/'+ID)return json({id:ID,username:'cashier1',displayName:'Сотрудник',active:false,roles:['EMPLOYEE']});
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
  expect(await screen.findByText('cashier1')).toBeInTheDocument();
  expect(screen.getByText(/Неактивен/)).toBeInTheDocument();
  await userEvent.setup().type(screen.getByRole('searchbox',{name:'Поиск сотрудника'}),'not-found');
  expect(screen.getByText('Сотрудники не найдены.')).toBeInTheDocument();
  expect(calls.mock.calls.some(call=>call[0]==='/api/admin/users')).toBe(true);
 });
 it('creates an employee separately from roles and navigates to the detail',async()=>{
  const calls=fakeApi();renderAt('/admin/users');
  const user=userEvent.setup();
  await screen.findByText('cashier1');
  await user.click(screen.getByRole('button',{name:'+ Создать сотрудника'}));
  const form=screen.getByRole('form',{name:'Создание сотрудника'});
  await user.type(within(form).getByRole('textbox',{name:'Логин'}),'cashier1');
  await user.type(within(form).getByRole('textbox',{name:'Имя сотрудника'}),'Сотрудник');
  await user.type(within(form).getByLabelText('Начальный пароль'),'InitialStrong123');
  await user.type(within(form).getByLabelText('Повтор пароля'),'InitialStrong123');
  await user.click(within(form).getByRole('button',{name:'Создать'}));
  expect(await screen.findByText('UUID: '+ID)).toBeInTheDocument();
  const posts=calls.mock.calls.filter(call=>call[1]?.method==='POST');
  expect(posts).toHaveLength(1);
  expect(JSON.parse(String(posts[0]?.[1]?.body))).toEqual({username:'cashier1',displayName:'Сотрудник',password:'InitialStrong123'});
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
  await screen.findByText('cashier1');
  expect(screen.queryByRole('button',{name:'+ Создать сотрудника'})).not.toBeInTheDocument();
 });
});
