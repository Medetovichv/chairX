import {useState} from 'react';
import {Link,useParams} from 'react-router';
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {useAuth} from '../../auth/AuthProvider';
import {adminApi,adminKeys} from '../api';
import {PageHeader} from '../../../shared/components/PageHeader';
import {LoadingState} from '../../../shared/components/LoadingState';
import {ErrorState} from '../../../shared/components/ErrorState';
import {Button} from '../../../shared/ui/button';
import {ConfirmDialog} from '../../../shared/ui/confirm-dialog';
import {PasswordReset} from '../components/PasswordReset';
import {UserRolesEditor} from '../components/UserRolesEditor';
export function AdminUserDetailPage(){
 const {userId}=useParams(),id=userId??'',{user:me,hasPermission,logout}=useAuth(),qc=useQueryClient();
 const item=useQuery({queryKey:adminKeys.user(id),queryFn:()=>adminApi.user(id),enabled:Boolean(id)});
 const [operation,setOperation]=useState<'activate'|'deactivate'|null>(null);
 const [error,setError]=useState(''),[notice,setNotice]=useState('');
 const status=useMutation({mutationFn:(a:'activate'|'deactivate')=>a==='activate'?adminApi.activate(id):adminApi.deactivate(id)});
 async function complete(){
  if(!operation||status.isPending)return;
  try{
   await status.mutateAsync(operation);setOperation(null);
   if(me?.id===id){logout();return;}
   await Promise.all([qc.invalidateQueries({queryKey:adminKeys.users}),
    qc.invalidateQueries({queryKey:adminKeys.user(id)}),qc.invalidateQueries({queryKey:adminKeys.auditPrefix})]);
   setNotice('Статус сотрудника обновлён.');
  }catch{setError('Операция отклонена: проверьте права, существование сотрудника и защиту последнего ADMIN.');setOperation(null);}
 }
 if(item.isPending)return <LoadingState label="Загружаем сотрудника…"/>;
 if(item.isError)return <ErrorState error={item.error} onRetry={()=>void item.refetch()}/>;
 if(!item.data)return <p>Сотрудник не найден</p>;
 const p=item.data;
 return <div className="space-y-5">
  <Link to="/admin/users" className="text-indigo-700">← Все сотрудники</Link>
  <PageHeader title={p.displayName} description={p.username}/>
  {error&&<p role="alert" className="rounded-xl bg-red-50 p-3">{error}</p>}
  {notice&&<p role="status" className="rounded-xl bg-green-50 p-3">{notice}</p>}
  <section className="space-y-3 rounded-2xl border border-slate-200 bg-white p-5">
   <p className="break-all text-sm">UUID: {p.id}</p><p>Статус: {p.active?'Активен':'Неактивен'}</p>
   <p>Роли: {p.roles.join(', ')||'Не назначены'}</p>
   {(p.active?hasPermission('USERS_DEACTIVATE'):hasPermission('USERS_ACTIVATE'))&&
    <Button variant="outline" disabled={status.isPending}
     onClick={()=>setOperation(p.active?'deactivate':'activate')}>
     {p.active?'Деактивировать':'Активировать'}</Button>}
  </section>
  {hasPermission('ROLES_ASSIGN')&&hasPermission('ROLES_READ')&&<UserRolesEditor person={p}/>}
  {hasPermission('USERS_RESET_PASSWORD')&&<PasswordReset id={p.id} onSaved={()=>{
    setNotice('Новый пароль установлен. Передайте его защищённым каналом.');
    void qc.invalidateQueries({queryKey:adminKeys.auditPrefix});
  }}/>}
  <ConfirmDialog open={operation!==null} onOpenChange={v=>{if(!v)setOperation(null);}}
   title={operation==='activate'&&p.roles.includes('ADMIN')?'Активировать сотрудника с ролью ADMIN?':
    operation==='activate'?'Активировать сотрудника?':'Деактивировать сотрудника?'}
   description={p.displayName+' ('+p.username+'). Роли: '+(p.roles.join(', ')||'нет')+'. '+
    (operation==='deactivate'?'После деактивации вход в ChairX невозможен.':'Назначенные роли сохранятся.')}
   destructive={operation==='deactivate'} busy={status.isPending}
   confirmLabel="Подтвердить" onConfirm={()=>void complete()}/>
 </div>;
}
