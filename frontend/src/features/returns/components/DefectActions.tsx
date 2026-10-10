import { useRef,useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { Button } from '../../../shared/ui/button';
import { ConfirmDialog } from '../../../shared/ui/confirm-dialog';
import { ApiClientError,userFacingApiError } from '../../../shared/api/api-error';
import { inventoryKeys } from '../../inventory/api';
import { defectsApi,returnKeys } from '../api';
import { clearPending,persistPending,readPending } from '../pending-operation';
import type { Defect } from '../types';
type Action='wait'|'resolve'|'writeoff';
export function DefectActions({defect,onChanged}:{defect:Defect;onChanged:(value:Defect)=>void}){
 const {hasPermission,user}=useAuth(),client=useQueryClient();
 const manage=hasPermission('DEFECTS_MANAGE'),write=hasPermission('DEFECTS_WRITE_OFF');
 const [action,setAction]=useState<Action|null>(null);
 const [note,setNote]=useState('');
 const [error,setError]=useState(''),[notice,setNotice]=useState('');
 const [lock,setLock]=useState(()=>{
  return ['wait','resolve','writeoff'].some(x=>readPending(user?.id,x as Action).kind!=='missing');
 });
 const [busy,setBusy]=useState(false),running=useRef(false);
 const open=defect.status==='OPEN'||defect.status==='WAITING_PARTS';
 async function execute(value:Action){
  if(running.current||lock||!user?.id)return;
  if(value==='writeoff'&&!write||value==='wait'&&!manage||value==='resolve'&&!manage)return;
  if(value!=='wait'&&(!note.trim()||note.length>4000)){setError('Результат обязателен (максимум 4000 символов).');return;}
  running.current=true;setBusy(true);setAction(null);setError('');
  const key=crypto.randomUUID();
  try{
   // The backend owns the transaction; preflight catches obvious stale cards.
   const fresh=await defectsApi.get(defect.id);
   if(fresh.status!==defect.status){onChanged(fresh);setError('Дефект изменён. Проверьте новую запись.');return;}
   if(!persistPending(user.id,value,key,defect.id)){setLock(true);setError('Хранилище операции недоступно; запрос не отправлен.');return;}
   const result=value==='wait'?await defectsApi.wait(defect.id):
    value==='resolve'?await defectsApi.resolve(defect.id,note.trim()):await defectsApi.writeOff(defect.id,note.trim());
   if(!clearPending(user.id,value)){setLock(true);setError('Операция выполнена, но запись восстановления осталась.');}
   onChanged(result);setNotice('Изменения подтверждены backend.');setNote('');
   await Promise.all([client.invalidateQueries({queryKey:returnKeys.defects}),
    client.invalidateQueries({queryKey:inventoryKeys.overviewPrefix}),
    client.invalidateQueries({queryKey:inventoryKeys.balancePrefix}),
    client.invalidateQueries({queryKey:inventoryKeys.movementsPrefix})]);
  }catch(cause){
   setError(userFacingApiError(cause));
   if(!(cause instanceof ApiClientError)||cause.kind!=='http'||cause.status===undefined||cause.status>=500||cause.status===409){
    setLock(true);
    setError('Результат операции неизвестен. Не повторяйте блокировку/списание. Проверьте статус на сервере.');
   }else if(!clearPending(user.id,value))setLock(true);
  }finally{running.current=false;setBusy(false);}
 }
 async function reconcile(){
  setBusy(true);
  try{
   const d=await defectsApi.get(defect.id);
   onChanged(d);
   // Never automatically clear a pending operation after an unverified GET:
   // two distinct operations may end in the same final status.
   setError('Текущий статус загружен. Для снятия блокировки необходима ручная сверка записи и складских движений.');
  }catch(e){setError(userFacingApiError(e));}
  finally{setBusy(false);}
 }
 if(!manage&&!write)return null;
 return <section aria-label="Операции с браком" className="space-y-3 rounded-xl border bg-white p-4">
  <h3 className="text-lg font-semibold">Действия</h3>
  {error&&<p role="alert" className="rounded-xl bg-amber-50 p-3 text-sm">{error}</p>}
  {notice&&<p role="status" className="rounded-xl bg-green-50 p-3 text-sm">{notice}</p>}
  {lock&&<div className="space-y-2 rounded-xl bg-amber-50 p-3 text-sm">
   <p>Действия заблокированы: обнаружена незавершённая операция в текущей вкладке. Не создавайте второе списание.</p>
   <Button variant="outline" disabled={busy} onClick={()=>void reconcile()}>Загрузить актуальный статус</Button>
  </div>}
  {open&&<div className="flex flex-wrap gap-2">
   {manage&&defect.status==='OPEN'&&<Button variant="outline" disabled={lock||busy}
    onClick={()=>setAction('wait')}>Ожидает запчасти</Button>}
   {manage&&<Button disabled={lock||busy} onClick={()=>setAction('resolve')}>Исправлено</Button>}
   {write&&<Button variant="destructive" disabled={lock||busy}
    onClick={()=>setAction('writeoff')}>Списать товар</Button>}
  </div>}
  {open&&(manage||write)&&<label className="block space-y-2 text-sm font-semibold">
    Результат ремонта / причина списания
    <textarea aria-label="Результат обработки брака" value={note} onChange={e=>setNote(e.target.value)}
      maxLength={4000} disabled={lock||busy} className="w-full rounded-xl border p-3" rows={3}/>
   </label>}
  <ConfirmDialog open={action!==null} onOpenChange={open=>{if(!open)setAction(null);}}
    title={action==='writeoff'?'Необратимо списать неисправный товар?':
      action==='resolve'?'Подтвердить ремонт?':'Ожидание запчастей?'}
    description={action==='writeoff'?
      'Вариация: '+defect.productVariantId+'. Склад: '+defect.warehouseId+
      '. Количество: '+defect.quantity+'. Проблема: '+defect.description+
      '. Backend выполнит WRITE_OFF и FIFO списание. Операция необратима.':
      action==='resolve'?'Backend снимет блокировку товара после исправления.':
      'Товар останется заблокированным в ожидании запчастей.'}
    destructive={action==='writeoff'} confirmLabel="Подтвердить действие"
    busy={busy} onConfirm={()=>{if(action)void execute(action);}}/>
 </section>;
}
