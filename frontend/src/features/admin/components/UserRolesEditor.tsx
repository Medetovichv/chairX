import {useState} from 'react';
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {useAuth} from '../../auth/AuthProvider';
import {adminApi,adminKeys} from '../api';
import {Button} from '../../../shared/ui/button';
import {ConfirmDialog} from '../../../shared/ui/confirm-dialog';
import type {AdminUser} from '../types';
export function UserRolesEditor({person}:{person:AdminUser}){
 const {user,logout,hasPermission}=useAuth(),qc=useQueryClient();
 const roles=useQuery({queryKey:adminKeys.roles,queryFn:adminApi.roles,
  enabled:hasPermission('ROLES_READ')&&hasPermission('ROLES_ASSIGN')});
 const [choice,setChoice]=useState(''),[removing,setRemoving]=useState<string|null>(null),[error,setError]=useState('');
 const action=useMutation({mutationFn:({id,remove}:{id:string;remove:boolean})=>
  remove?adminApi.removeRole(person.id,id):adminApi.assignRole(person.id,id)});
 async function save(id:string,remove:boolean){
  if(action.isPending)return;
  try{
   await action.mutateAsync({id,remove});setChoice('');setRemoving(null);
   if(user?.id===person.id){logout();return;}
   await Promise.all([qc.invalidateQueries({queryKey:adminKeys.users}),
    qc.invalidateQueries({queryKey:adminKeys.roles}),qc.invalidateQueries({queryKey:adminKeys.auditPrefix})]);
  }catch{setError('Изменение роли отклонено. Проверьте права, активность и защиту последнего ADMIN.');setRemoving(null);}
 }
 const options=roles.data??[];
 return <section aria-label="Управление ролями" className="space-y-3 rounded-2xl border border-slate-200 bg-white p-5">
  <h3 className="font-bold">Роли сотрудника</h3>
  {person.roles.map(code=><div key={code} className="flex items-center justify-between py-2">
   <strong>{code}</strong>{options.find(x=>x.code===code)&&
    <Button variant="outline" disabled={action.isPending} onClick={()=>setRemoving(options.find(x=>x.code===code)!.id)}>Снять роль</Button>}
  </div>)}
  {person.active&&<div className="flex flex-wrap gap-2">
   <select aria-label="Назначаемая роль" value={choice} onChange={e=>setChoice(e.target.value)}
    className="min-h-11 rounded-xl border border-slate-200 px-3">
    <option value="">Выберите роль</option>{options.filter(x=>!person.roles.includes(x.code)).map(x=>
     <option key={x.id} value={x.id}>{x.name} ({x.code})</option>)}
   </select><Button disabled={!choice||action.isPending} onClick={()=>void save(choice,false)}>Назначить</Button>
  </div>}
  {error&&<p role="alert" className="text-red-700">{error}</p>}
  <ConfirmDialog open={removing!==null} onOpenChange={x=>{if(!x)setRemoving(null);}}
   title="Снять роль?" description="Остальные роли сохранятся. Последнего активного администратора снять нельзя."
   destructive busy={action.isPending} onConfirm={()=>{if(removing)void save(removing,true);}}/>
 </section>;
}
