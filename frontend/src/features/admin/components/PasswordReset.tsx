import {useState,useRef} from 'react';
import {useMutation} from '@tanstack/react-query';
import {useAuth} from '../../auth/AuthProvider';
import {Input} from '../../../shared/ui/input';
import {Button} from '../../../shared/ui/button';
import {ConfirmDialog} from '../../../shared/ui/confirm-dialog';
import {adminApi} from '../api';
import {validatePassword} from '../validation';
export function PasswordReset({id,onSaved}:{id:string;onSaved:()=>void}){
 const {user,logout}=useAuth();
 const [password,setPassword]=useState(''),[repeat,setRepeat]=useState('');
 const [confirm,setConfirm]=useState(false),[error,setError]=useState('');
 const pending=useRef(false);
 const reset=useMutation({mutationFn:()=>adminApi.resetPassword(id,password)});
 async function submit(){
  if(pending.current)return;
  const issue=validatePassword(password,repeat);
  if(issue){setError(issue);setConfirm(false);return;}
  pending.current=true;setError('');
  try{
   await reset.mutateAsync();
   setPassword('');setRepeat('');setConfirm(false);
   if(user?.id===id)logout();else onSaved();
  }catch{
   // A lost response may follow a committed reset; do not retain the old
   // credential in memory after an uncertain self-password change.
   if(user?.id===id){setPassword('');setRepeat('');setConfirm(false);logout();return;}
   setError('Результат сброса неизвестен либо запрос отклонён. Проверьте права и журнал аудита.');
   setPassword('');setRepeat('');setConfirm(false);
  }finally{pending.current=false;}
 }
 return <section aria-label="Сброс пароля" className="space-y-3 rounded-2xl border border-slate-200 bg-white p-5">
  <h3 className="font-bold">Сброс пароля</h3>
  <p className="text-sm">Установите новый пароль (12–128 символов). Предыдущий станет недействительным.</p>
  <div className="grid gap-3 sm:grid-cols-2">
   <Input aria-label="Новый пароль" type="password" autoComplete="new-password" value={password} maxLength={128} onChange={e=>setPassword(e.target.value)}/>
   <Input aria-label="Подтверждение нового пароля" type="password" autoComplete="new-password" value={repeat} maxLength={128} onChange={e=>setRepeat(e.target.value)}/>
  </div>
  {error&&<p role="alert" className="text-red-700">{error}</p>}
  <Button variant="outline" disabled={reset.isPending} onClick={()=>{
   const issue=validatePassword(password,repeat);if(issue)setError(issue);else setConfirm(true);
  }}>Сбросить пароль</Button>
  {user?.id===id&&<p className="text-sm text-amber-800">После сброса собственного пароля потребуется повторный вход.</p>}
  <ConfirmDialog open={confirm} onOpenChange={setConfirm} title="Сбросить пароль сотрудника?"
   description="Предыдущий пароль будет недействителен. Передавайте новый только через защищённый канал."
   confirmLabel="Сбросить" destructive busy={reset.isPending} onConfirm={()=>void submit()}/>
 </section>;
}
