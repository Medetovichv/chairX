import {useState,useEffect} from 'react';
import {Link,useParams} from 'react-router';
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {useAuth} from '../../auth/AuthProvider';
import {adminApi,adminKeys} from '../api';
import {PermissionSelector} from '../components/PermissionSelector';
import {canChoosePermission} from '../validation';
import {PageHeader} from '../../../shared/components/PageHeader';
import {LoadingState} from '../../../shared/components/LoadingState';
import {ErrorState} from '../../../shared/components/ErrorState';
import {Button} from '../../../shared/ui/button';
import {Input} from '../../../shared/ui/input';
import {ConfirmDialog} from '../../../shared/ui/confirm-dialog';
export function AdminRoleDetailPage(){
 const {roleId}=useParams(),id=roleId??'',{hasPermission,logout}=useAuth(),qc=useQueryClient();
 const role=useQuery({queryKey:adminKeys.role(id),queryFn:()=>adminApi.role(id),enabled:Boolean(id)});
 const all=useQuery({queryKey:adminKeys.permissions,queryFn:adminApi.permissions,enabled:hasPermission('ROLES_READ')});
 const [editing,setEditing]=useState(false),[name,setName]=useState(''),[selected,setSelected]=useState<string[]>([]);
 const [reason,setReason]=useState(''),[confirm,setConfirm]=useState(false);
 const [error,setError]=useState(''),[conflicted,setConflicted]=useState(false);
 const [version,setVersion]=useState(0);
 const update=useMutation({mutationFn:()=>adminApi.updateRole(id,{name:name.trim(),permissions:selected,reason:reason.trim(),expectedVersion:version})});
 useEffect(()=>{if(role.data&&!editing){setName(role.data.name);setSelected(role.data.permissions);setVersion(role.data.version);}},[role.data,editing]);
 async function save(){
  if(update.isPending)return;
  setConfirm(false);setError('');
  try{
   if(!name.trim()||name.trim().length>200||!reason.trim()||reason.length>500){
    setError('Укажите название роли и причину изменения (до 500 символов).');return;
   }
   const permitted=new Set((all.data??[]).filter(p=>canChoosePermission(p.code,p.adminOnly)).map(p=>p.code));
   if(selected.some(code=>!permitted.has(code))){setError('Выбрано запрещённое разрешение.');return;}
   await update.mutateAsync();setEditing(false);
   await Promise.all([qc.invalidateQueries({queryKey:adminKeys.roles}),
    qc.invalidateQueries({queryKey:adminKeys.role(id)}),qc.invalidateQueries({queryKey:adminKeys.auditPrefix})]);
   logout(); // Fail-safe: updated effective rights could include the current admin.
  }catch(e){
   const conflict=e instanceof Error&&'code' in e&&(e as {code:string}).code==='VERSION_CONFLICT';
   setConflicted(conflict);
   setError(conflict?'VERSION_CONFLICT: роль изменена другим администратором. Несохранённые изменения сохранены в форме.':
    'Обновление роли отклонено. Проверьте права и данные.');
  }
 }
 if(role.isPending)return <LoadingState label="Загружаем роль…"/>;
 if(role.isError)return <ErrorState error={role.error} onRetry={()=>void role.refetch()}/>;
 if(!role.data)return <p>Роль не найдена</p>;
 const r=role.data;
 const mayEdit=hasPermission('ROLES_UPDATE')&&r.code!=='ADMIN';
 return <div className="space-y-5">
  <Link to="/admin/roles" className="text-indigo-700">← Все роли</Link>
  <PageHeader title={r.name} description={'Код: '+r.code+' · '+(r.systemRole?'Системная роль':'Пользовательская роль')}/>
  <section className="space-y-3 rounded-2xl border border-slate-200 bg-white p-5">
   <p>Сотрудников: {r.assignedUsersCount} · Версия: {r.version}</p>
   <p className="text-sm">Разрешения: {r.permissions.join(', ')||'нет'}</p>
   {r.code==='ADMIN'&&<p className="text-amber-800">Системную роль ADMIN изменять нельзя.</p>}
   {mayEdit&&!editing&&<Button onClick={()=>setEditing(true)}>Редактировать</Button>}
  </section>
  {mayEdit&&editing&&<section aria-label="Редактирование роли" className="space-y-4 rounded-2xl border border-indigo-200 bg-white p-5">
   <label>Название роли<Input value={name} maxLength={200} onChange={e=>setName(e.target.value)}/></label>
   <label>Причина изменения<Input aria-label="Причина изменения" value={reason} maxLength={500} onChange={e=>setReason(e.target.value)}/></label>
   {all.isPending?<LoadingState label="Загружаем permissions…"/>:
    all.isError?<ErrorState error={all.error} onRetry={()=>void all.refetch()}/>:
    <PermissionSelector all={all.data??[]} selected={selected} onChange={setSelected}/>}
   {conflicted&&<div className="rounded-xl bg-amber-50 p-3 text-sm">
    <p>Нельзя применять прежнюю версию автоматически. Перечитайте роль и вручную решите, что сохранить.</p>
    <Button variant="outline" onClick={()=>void role.refetch()}>Прочитать актуальную роль (несохранённая форма не изменится)</Button>
    <Button variant="outline" onClick={()=>{
      if(role.data){setName(role.data.name);setSelected(role.data.permissions);setVersion(role.data.version);setConflicted(false);}
    }}>Начать заново с актуальной версией</Button>
   </div>}
   {error&&<p role="alert" className="text-red-700">{error}</p>}
   {r.systemRole&&<p className="text-amber-800">Изменение системной роли затронет сотрудников.</p>}
   <div className="flex gap-2">
    <Button disabled={update.isPending||conflicted||all.isPending} onClick={()=>setConfirm(true)}>Сохранить</Button>
    <Button variant="outline" disabled={update.isPending} onClick={()=>{setEditing(false);setConflicted(false);}}>Отмена</Button>
   </div>
  </section>}
  <ConfirmDialog open={confirm} onOpenChange={setConfirm} title="Изменить разрешения роли?"
   description={'Роль: '+r.code+'. Сохранение повлияет на права сотрудников и потребует повторного входа.'}
   busy={update.isPending} confirmLabel="Сохранить изменения" onConfirm={()=>void save()}/>
 </div>;
}
