import { useRef,useState,type FormEvent } from 'react';
import { Link,useNavigate } from 'react-router';
import { useQuery,useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { adminApi,adminKeys } from '../api';
import { validateUser } from '../validation';
import { PageHeader } from '../../../shared/components/PageHeader';
import { Input } from '../../../shared/ui/input';
import { Button } from '../../../shared/ui/button';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState';

export function AdminUsersPage(){
 const {hasPermission}=useAuth(),client=useQueryClient(),navigate=useNavigate();
 const users=useQuery({queryKey:adminKeys.users,queryFn:adminApi.users});
 const [filter,setFilter]=useState(''),[onlyActive,setOnlyActive]=useState('all');
 const [creating,setCreating]=useState(false),[unknown,setUnknown]=useState(false);
 const [username,setUsername]=useState(''),[name,setName]=useState('');
 const [password,setPassword]=useState(''),[again,setAgain]=useState(''),[err,setErr]=useState('');
 const saving=useRef(false);
 const [submitting,setSubmitting]=useState(false);
 async function submit(e:FormEvent<HTMLFormElement>){
  e.preventDefault();if(saving.current||unknown)return;
  const validation=validateUser(username,name,password,again);
  if(validation){setErr(validation);return;}
  saving.current=true;setSubmitting(true);setErr('');
  try{
   // Do not pass passwords to TanStack mutations: its mutation cache retains
   // variables after settling. The private form state is cleared immediately.
   const person=await adminApi.createUser({username:username.trim().toLowerCase(),displayName:name.trim(),password});
   setPassword('');setAgain('');setCreating(false);
   await client.invalidateQueries({queryKey:adminKeys.users});
   navigate('/admin/users/'+person.id,{state:{notice:'Сотрудник создан без ролей. Назначьте ему роли.'}});
  }catch(x){
   setPassword('');setAgain('');
   if(x instanceof Error&&'kind' in x&&(['network','parse'].includes(String((x as {kind:string}).kind))||
     ('status' in x&&Number((x as {status:number}).status)>=500))){
    setUnknown(true);setErr('Результат создания неизвестен. Сначала проверьте список, не повторяйте запрос.');
   }else setErr('Создание отклонено. Проверьте логин и ограничения.');
  }finally{saving.current=false;setSubmitting(false);}
 }
 const visible=(users.data??[]).filter(x=>(onlyActive==='all'||(onlyActive==='active')===x.active)&&
 [x.username,x.displayName,...x.roles].join(' ').toLowerCase().includes(filter.toLowerCase()));
 return <div className="space-y-5">
  <PageHeader title="Сотрудники" description="Учётные записи и их роли"
    action={hasPermission('USERS_CREATE')?<Button onClick={()=>setCreating(true)}>+ Создать сотрудника</Button>:undefined}/>
  {creating&&<form aria-label="Создание сотрудника" onSubmit={e=>void submit(e)}
   className="space-y-3 rounded-2xl border border-slate-200 bg-white p-5">
   <h3 className="font-bold">Новый сотрудник</h3>
   <p className="text-sm text-slate-500">Создание и назначение ролей — разные операции.</p>
   <div className="grid gap-3 sm:grid-cols-2">
    <label>Логин<Input aria-label="Логин" required maxLength={100} value={username} onChange={e=>setUsername(e.target.value)}/></label>
    <label>Имя<Input aria-label="Имя сотрудника" required maxLength={200} value={name} onChange={e=>setName(e.target.value)}/></label>
    <label>Начальный пароль<Input aria-label="Начальный пароль" type="password" autoComplete="new-password" required minLength={12} maxLength={128}
     value={password} onChange={e=>setPassword(e.target.value)}/></label>
    <label>Повтор пароля<Input aria-label="Повтор пароля" type="password" autoComplete="new-password" required minLength={12} maxLength={128}
     value={again} onChange={e=>setAgain(e.target.value)}/></label>
   </div>
   {err&&<p role="alert" className="text-red-700">{err}</p>}
   {unknown&&<Button type="button" variant="outline" onClick={()=>void client.invalidateQueries({queryKey:adminKeys.users})}>Проверить список</Button>}
   <div className="flex gap-2"><Button type="submit" disabled={submitting||unknown}>Создать</Button>
    <Button type="button" variant="outline" disabled={submitting} onClick={()=>{setCreating(false);setUnknown(false);setPassword('');setAgain('');}}>Закрыть</Button></div>
  </form>}
  <section className="space-y-3 rounded-2xl border border-slate-200 bg-white p-5">
   <div className="flex flex-wrap gap-3">
    <Input type="search" aria-label="Поиск сотрудника" className="max-w-xs" placeholder="Имя, логин или роль"
     value={filter} onChange={e=>setFilter(e.target.value)}/>
    <select aria-label="Статус сотрудника" value={onlyActive} onChange={e=>setOnlyActive(e.target.value)}
     className="min-h-11 rounded-xl border border-slate-200 px-3"><option value="all">Все</option>
     <option value="active">Активные</option><option value="inactive">Неактивные</option></select>
   </div>
   {users.isPending?<LoadingState label="Загружаем сотрудников…"/>:
    users.isError?<ErrorState error={users.error} onRetry={()=>void users.refetch()}/>:
    visible.length===0?<p>Сотрудники не найдены.</p>:
    visible.map(x=><article key={x.id} className="entity-clickable-row group flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 px-3 py-3 transition-colors">
     <div><b><Link className="entity-stretched-link text-blue-700 hover:underline" to={'/admin/users/'+encodeURIComponent(x.id)} aria-label={'Открыть сотрудника '+x.displayName}>{x.displayName}</Link></b><p className="text-sm">{x.username} · {x.active?'Активен':'Неактивен'}</p>
      <p className="text-sm text-slate-500">{x.roles.join(', ')||'Без ролей'}</p></div>
    </article>)}
  </section>
 </div>;
}
