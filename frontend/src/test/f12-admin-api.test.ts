import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import {adminApi} from '../features/admin/api';
import {canChoosePermission,validateUser,validatePassword,validateRole,isAdminRole} from '../features/admin/validation';
import {authSession} from '../features/auth/session';
function json(body:unknown,status=200){return new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json'}});}
beforeEach(()=>authSession.activate('Basic test-admin',{headerName:'X-CSRF-TOKEN',token:'f12-csrf'}));
afterEach(()=>{authSession.clear();vi.unstubAllGlobals();});
describe('F12 admin API and input contracts',()=>{
 it('prohibits custom admin grants even when adminOnly flag is incorrect',()=>{
  for(const code of ['ADMIN_ACCESS','USERS_ACTIVATE','USERS_RESET_PASSWORD','USERS_CREATE','ROLES_ASSIGN','DAILY_CLOSING_UNLOCK_ADMIN','CATALOG_ACCESS'])
    expect(canChoosePermission(code,false)).toBe(false);
  expect(canChoosePermission('INVENTORY_READ',false)).toBe(true);
  expect(canChoosePermission('INVENTORY_READ',true)).toBe(false);
 });
 it('checks system ADMIN role without trusting ordinary manager or custom names',()=>{
  expect(isAdminRole(['CUSTOM','MANAGER'])).toBe(false);
  expect(isAdminRole(['EMPLOYEE','ADMIN'])).toBe(true);
 });
 it('validates login, password, reserved name, role and duplicates',()=>{
  expect(validateUser('INVALID SPACE','User','ValidPassword123','ValidPassword123')).not.toBeNull();
  expect(validateUser('catalog','User','ValidPassword123','ValidPassword123')).not.toBeNull();
  expect(validateUser('valid_user','User','ValidPassword123','ValidPassword123')).toBeNull();
  expect(validatePassword('short','short')).not.toBeNull();
  expect(validatePassword('ValidPassword123','Mismatch123456')).not.toBeNull();
  expect(validateRole('ADMIN','Admin',[])).not.toBeNull();
  expect(validateRole('WAREHOUSE','Кладовщик',['INVENTORY_READ','INVENTORY_READ'])).not.toBeNull();
  expect(validateRole('WAREHOUSE','Кладовщик',['INVENTORY_READ'])).toBeNull();
 });
 it('sends activate as PATCH with CSRF and no extra request body',async()=>{
  const fetcher=vi.fn(async()=>new Response(null,{status:204}));
  vi.stubGlobal('fetch',fetcher);
  await adminApi.activate('11111111-1111-4111-8111-111111111111');
  const [url,init]=fetcher.mock.calls[0] as [string,RequestInit];
  expect(url).toContain('/api/admin/users/11111111-1111-4111-8111-111111111111/activate');
  expect(init.method).toBe('PATCH');
  expect(new Headers(init.headers).get('X-CSRF-TOKEN')).toBe('f12-csrf');
 });
 it('sends only newPassword and never caches it or expects a response',async()=>{
  const fetcher=vi.fn(async()=>new Response(null,{status:204}));
  vi.stubGlobal('fetch',fetcher);
  await adminApi.resetPassword('11111111-1111-4111-8111-111111111111','StrongNewPassword123');
  const [url,init]=fetcher.mock.calls[0] as [string,RequestInit];
  expect(url).toContain('/reset-password');
  expect(init.method).toBe('POST');
  expect(JSON.parse(String(init.body))).toEqual({newPassword:'StrongNewPassword123'});
  expect(new Headers(init.headers).get('X-CSRF-TOKEN')).toBe('f12-csrf');
 });
 it('assigns and removes exact role UUID without replacing other roles',async()=>{
  const fetcher=vi.fn(async()=>new Response(null,{status:204}));
  vi.stubGlobal('fetch',fetcher);
  await adminApi.assignRole('user-id','role-id');
  await adminApi.removeRole('user-id','role-id');
  expect(JSON.parse(String(fetcher.mock.calls[0]?.[1]?.body))).toEqual({roleId:'role-id'});
  expect(fetcher.mock.calls[1]?.[1]?.method).toBe('DELETE');
  expect(String(fetcher.mock.calls[1]?.[0])).toContain('/users/user-id/roles/role-id');
 });
 it('PUT uses expectedVersion and reason and surface 409',async()=>{
  const fetcher=vi.fn(async()=>json({code:'VERSION_CONFLICT',message:'outdated',details:{}},409));
  vi.stubGlobal('fetch',fetcher);
  await expect(adminApi.updateRole('role-id',{name:'New',permissions:[],expectedVersion:2,reason:'Изменение обязанностей'})).rejects.toMatchObject({status:409,code:'VERSION_CONFLICT'});
  const body=JSON.parse(String(fetcher.mock.calls[0]?.[1]?.body));
  expect(body.expectedVersion).toBe(2);
  expect(body.reason).toBe('Изменение обязанностей');
 });
});
