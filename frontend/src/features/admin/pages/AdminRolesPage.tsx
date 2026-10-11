import {useRef,useState} from 'react';
import {Link,useNavigate} from 'react-router';
import {useQuery,useMutation,useQueryClient} from '@tanstack/react-query';
import {useAuth} from '../../auth/AuthProvider';
import {PageHeader} from '../../../shared/components/PageHeader';
import {LoadingState} from '../../../shared/components/LoadingState';
import {ErrorState} from '../../../shared/components/ErrorState';
import {Button} from '../../../shared/ui/button';
import {Input} from '../../../shared/ui/input';
import {adminApi,adminKeys} from '../api';
import {validateRole} from '../validation';
import {PermissionSelector} from '../components/PermissionSelector';
export function AdminRolesPage(){
 const {hasPermission}=useAuth(),qc=useQueryClient(),navigate=useNavigate();
 const roles=useQuery({queryKey:adminKeys.roles,queryFn:adminApi.roles});
 const permissions=useQuery({queryKey:adminKeys.permissions,queryFn:adminApi.permissions,
  enabled:hasPermission('ROLES_READ')});
 const [creating,setCreating]=useState(false),[code,setCode]=useState(''),[name,setName]=useState('');
 const [selected,setSelected]=useState<string[]>([]),[error,setError]=useState(''),[unknown,setUnknown]=useState(false);
 const lock=useRef(false);
 const create=useMutation({mutationFn:adminApi.createRole});
 async function save(){
  if(lock.current||unknown)return;
  const check=validateRole(code,name,selected);
  if(check){setError(check);return;}
  lock.current=true;setError('');
  try{
   const role=await create.mutateAsync({code:code.trim().toUpperCase(),name:name.trim(),permissions:selected});
   await Promise.all([qc.invalidateQueries({queryKey:adminKeys.roles}),qc.invalidateQueries({queryKey:adminKeys.auditPrefix})]);
   setCreating(false);navigate('/admin/roles/'+role.id);
  }catch(err){
   if(err instanceof Error&&'kind' in err&&(['network','parse'].includes(String((err as {kind:string}).kind))||
    ('status' in err&&Number((err as {status:number}).status)>=500))){
    setUnknown(true);setError('Результат создания неизвестен. Проверьте список ролей перед повтором.');
   }else setError('Роль не создана. Проверьте код, разрешения и возможные конфликты.');
  }finally{lock.current=false;}
 }
 return <div className="space-y-5">
  <PageHeader title="Роли и разрешения" description="Пользовательские роли создаются вручную; системные роли остаются защищёнными."
   action={hasPermission('ROLES_CREATE')?<Button onClick={()=>setCreating(true)}>+ Создать роль</Button>:undefined}/>
  {creating&&hasPermission('ROLES_CREATE')&&<section aria-label="Новая роль" className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5">
   <h3 className="font-bold">Создание пользовательской роли</h3>
   <div className="grid gap-3 sm:grid-cols-2">
    <label>Код роли<Input aria-label="Код роли" maxLength={100} value={code} onChange={e=>setCode(e.target.value.toUpperCase())}/></label>
    <label>Название роли<Input aria-label="Название роли" maxLength={200} value={name} onChange={e=>setName(e.target.value)}/></label>
   </div>
   {permissions.isPending?<LoadingState label="Загружаем permissions…"/>:
    permissions.isError?<ErrorState error={permissions.error} onRetry={()=>void permissions.refetch()}/>:
    <PermissionSelector all={permissions.data??[]} selected={selected} onChange={setSelected}/>}
   {error&&<p role="alert" className="text-red-700">{error}</p>}
   {unknown&&<Button variant="outline" onClick={()=>void roles.refetch()}>Сверить список ролей</Button>}
   <div className="flex gap-2">
    <Button disabled={create.isPending||permissions.isPending||unknown} onClick={()=>void save()}>Создать роль</Button>
    <Button variant="outline" disabled={create.isPending} onClick={()=>{setCreating(false);setUnknown(false);}}>Закрыть</Button>
   </div>
  </section>}
  {roles.isPending?<LoadingState label="Загружаем роли…"/>:
   roles.isError?<ErrorState error={roles.error} onRetry={()=>void roles.refetch()}/>:
   <section className="divide-y divide-slate-100 rounded-2xl border border-slate-200 bg-white p-5">
    {(roles.data??[]).map(role=><article key={role.id} className="entity-clickable-row group flex flex-wrap items-center justify-between gap-3 px-3 py-3 transition-colors">
     <div><h3 className="font-bold"><Link className="entity-stretched-link text-blue-700 hover:underline" to={'/admin/roles/'+encodeURIComponent(role.id)} aria-label={'Открыть роль '+role.name}>{role.name}</Link> ({role.code})</h3>
      <p className="text-sm text-slate-500">{role.systemRole?'Системная':'Пользовательская'} · Сотрудников: {role.assignedUsersCount} · Разрешений: {role.permissions.length}</p></div>
    </article>)}
   </section>}
 </div>;
}
