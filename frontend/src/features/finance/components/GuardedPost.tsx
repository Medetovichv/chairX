import { useEffect,useRef,useState,type ReactNode } from 'react';
import { useAuth } from '../../auth/AuthProvider';
import { userFacingApiError,ApiClientError } from '../../../shared/api/api-error';
import { ConfirmDialog } from '../../../shared/ui/confirm-dialog';
import { Button } from '../../../shared/ui/button';
import {
 readFinancePending,saveFinancePending,clearFinancePending,
 type PendingFinance,type ReadFinance
} from '../pending';
type Control<T>={start:(payload:T,description:string)=>void;locked:boolean;busy:boolean};
interface Props<T>{
 kind:string;scope:string;allowed:boolean;title:string;label:string;destructive?:boolean;
 operationKey:(payload:T)=>string;meta:(payload:T)=>Record<string,string>;
 post:(payload:T)=>Promise<unknown>;onDone:()=>Promise<void>|void;
 /** Only for backend idempotent POSTs with a stable key inside the original payload. */
 canReplay?:boolean;
 /** GET-only reconciliation. Return true only if backend proves the same financial operation. */
 reconcile?:(record:PendingFinance)=>Promise<boolean>;
 children:(ctl:Control<T>)=>ReactNode;
}
/**
 * Every financial POST is explicitly confirmed and persisted before transport.
 * No automatic POST retry. On reload a minimal journal survives in this tab.
 * Replays are possible only with the exact payload still held in memory and
 * a matching backend-supported idempotency key.
 */
export function GuardedPost<T>({
 kind,scope,allowed,title,label,destructive,operationKey,meta,post,onDone,reconcile,canReplay=false,children
}:Props<T>){
 const {user}=useAuth();
 const [initial]=useState<ReadFinance>(()=>readFinancePending(user?.id,kind,scope));
 const [journal,setJournal]=useState<ReadFinance>(initial);
 const [candidate,setCandidate]=useState<{payload:T;description:string}|null>(null);
 const [replay,setReplay]=useState<T|null>(null);
 const [busy,setBusy]=useState(false);
 const [error,setError]=useState(''),[notice,setNotice]=useState('');
 const running=useRef(false);
 const locked=busy||journal.kind!=='missing';
 useEffect(()=>{
  if(journal.kind==='missing'&&!busy)return;
  const warn=(e:BeforeUnloadEvent)=>{e.preventDefault();e.returnValue='';};
  window.addEventListener('beforeunload',warn);
  return()=>window.removeEventListener('beforeunload',warn);
 },[journal,busy]);
 function start(payload:T,description:string){
  if(!allowed||locked||running.current)return;
  setError('');setNotice('');
  setCandidate({payload,description});
 }
 function finish(){
  if(!user?.id||!clearFinancePending(user.id,kind,scope)){
   setError('Сервер подтвердил действие, но очистить запись восстановления не удалось. Проверьте состояние счёта.');
   setJournal({kind:'invalid'});
   return;
  }
  setJournal({kind:'missing'});setReplay(null);setCandidate(null);
  setNotice('Операция подтверждена сервером.');
 }
 async function send(payload:T,isReplay=false){
  if(running.current||!allowed||!user?.id)return;
  const key=operationKey(payload);
  const record:PendingFinance={key,scope,kind,meta:meta(payload)};
  // An exact retry requires the same original payload, same operation key
  // and a previously committed local pending marker.
  if(isReplay&&(!canReplay||!replay||JSON.stringify(replay)!==JSON.stringify(payload)||
     journal.kind!=='pending'||JSON.stringify(journal.record)!==JSON.stringify(record)))return;
  if(!isReplay&&journal.kind!=='missing')return;
  if(!saveFinancePending(user.id,record)){
   setJournal({kind:'invalid'});
   setError('Не удалось сохранить исходную операцию перед POST. Проведение денег заблокировано.');
   return;
  }
  running.current=true;setBusy(true);setCandidate(null);setError('');
  setJournal({kind:'pending',record});
  let succeeded=false;
  try{
   await post(payload);
   succeeded=true;
  }catch(cause){
   const ambiguous=!(cause instanceof ApiClientError)||cause.kind!=='http'||
    cause.status===undefined||cause.status>=500||cause.status===409;
   if(ambiguous||isReplay){
    if(canReplay)setReplay(payload);
    setError(userFacingApiError(cause)+' Результат неизвестен; новый финансовый POST заблокирован.');
   }else{
    // A definite client-side rejection on the first POST may be discarded.
    // An existing uncertain request is never unlocked by a later 4xx.
    if(clearFinancePending(user.id,kind,scope))setJournal({kind:'missing'});
    else setJournal({kind:'invalid'});
    setError(userFacingApiError(cause));
   }
  }finally{
   if(!succeeded){running.current=false;setBusy(false);}
  }
  if(!succeeded)return;
  finish();
  try{await onDone();}
  catch{
   setError('Операция подтверждена backend, но обновить данные не удалось. Обновите страницу — повторная проводка не нужна.');
  }finally{
   running.current=false;setBusy(false);
  }
 }
 async function check(){
  if(!reconcile||journal.kind!=='pending'||running.current)return;
  running.current=true;setBusy(true);setError('');
  try{
   const committed=await reconcile(journal.record);
   if(committed){
    // This read already proved that the original financial POST committed.
    // A later cache-refresh failure is not an ambiguous financial outcome.
    finish();
    try{await onDone();}
    catch{
     setError('Операция подтверждена backend, но обновить данные не удалось. Обновите страницу — повторная проводка не нужна.');
    }
   }else{
    setError('Исходная операция пока не подтверждена. Не повторяйте POST с новым ключом. При необходимости обратитесь к администратору.');
   }
  }catch(cause){setError(userFacingApiError(cause)+' Результат всё ещё неизвестен.');}
  finally{running.current=false;setBusy(false);}
 }
 if(!allowed)return null;
 return <section className="space-y-3">
  {children({start,locked,busy})}
  {error&&<p role="alert" className="rounded-xl bg-amber-50 p-3 text-sm text-amber-900">{error}</p>}
  {notice&&<p role="status" className="rounded-xl bg-emerald-50 p-3 text-sm text-emerald-800">{notice}</p>}
  {journal.kind!=='missing'&&<div role="alert" className="space-y-2 rounded-xl border border-amber-300 bg-amber-50 p-4 text-sm">
   <p className="font-semibold">Результат финансовой операции неизвестен</p>
   {journal.kind==='pending'&&<p className="break-all">Ключ операции: {journal.record.key}. Повторный POST с новым ключом запрещён.</p>}
   {journal.kind==='invalid'&&<p>Журнал операции недоступен или повреждён. Нужна ручная сверка.</p>}
   <div className="flex flex-wrap gap-2">
    {reconcile&&journal.kind==='pending'&&<Button type="button" variant="outline" disabled={busy}
       onClick={()=>void check()}>Сверить с backend</Button>}
    {canReplay&&replay&&journal.kind==='pending'&&
     <Button type="button" disabled={busy} onClick={()=>void send(replay,true)}>
       Повторить исходный запрос с прежним ключом</Button>}
   </div>
  </div>}
  <ConfirmDialog open={candidate!==null} onOpenChange={open=>{if(!open)setCandidate(null);}}
   title={title} description={candidate?.description??''} destructive={destructive}
   confirmLabel={label} busy={busy}
   onConfirm={()=>{const item=candidate;if(item)void send(item.payload);}}/>
 </section>;
}
