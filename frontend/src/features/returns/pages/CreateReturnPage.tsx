import { useEffect,useRef,useState } from 'react';
import { Link,useNavigate,useSearchParams } from 'react-router';
import { useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { PageHeader } from '../../../shared/components/PageHeader';
import { ForbiddenPage } from '../../../pages/ForbiddenPage';
import { Button } from '../../../shared/ui/button';
import { ApiClientError,userFacingApiError } from '../../../shared/api/api-error';
import { ReturnForm } from '../components/ReturnForm';
import { returnsApi,returnKeys } from '../api';
import { persistPending,readPending,clearPending } from '../pending-operation';
import { inventoryKeys } from '../../inventory/api';
import { salesKeys } from '../../sales/api';
import { uuid } from '../validation';
import type { ReturnInput } from '../types';
export function CreateReturnPage(){
 const {hasPermission,user}=useAuth(),allowed=hasPermission('RETURNS_CREATE');
 const client=useQueryClient(),nav=useNavigate(),[params]=useSearchParams();
 const [stored]=useState(()=>readPending(user?.id,'return'));
 const [pending,setPending]=useState<ReturnInput|null>(null);
 const [locked,setLocked]=useState(stored.kind!=='missing'),[error,setError]=useState('');
 const [busy,setBusy]=useState(false),inFlight=useRef(false);
 const saleId=params.get('saleId')??'';
 useEffect(()=>{
  if(!busy&&!locked)return;
  const warn=(e:BeforeUnloadEvent)=>{e.preventDefault();e.returnValue='';};
  window.addEventListener('beforeunload',warn);return()=>window.removeEventListener('beforeunload',warn);
 },[busy,locked]);
 const refresh=async()=>{
  await Promise.all([
   client.invalidateQueries({queryKey:returnKeys.lists}),
   client.invalidateQueries({queryKey:['returns','history']}),
   client.invalidateQueries({queryKey:salesKeys.lists}),
   client.invalidateQueries({queryKey:inventoryKeys.overviewPrefix}),
   client.invalidateQueries({queryKey:inventoryKeys.balancePrefix}),
   client.invalidateQueries({queryKey:inventoryKeys.movementsPrefix}),
  ]);
 };
 async function submit(input:Omit<ReturnInput,'idempotencyKey'>,retry=false){
  if(inFlight.current||!allowed||locked&&!retry)return;
  const payload=retry?pending:{...input,idempotencyKey:crypto.randomUUID()};
  if(!payload||!user?.id)return;
  if(!retry&&!persistPending(user.id,'return',payload.idempotencyKey,payload.saleId)){
   setLocked(true);setError('Не удалось сохранить ключ операции. Возврат не отправлен.');return;
  }
  inFlight.current=true;setBusy(true);setError('');
  try{
   const r=await returnsApi.create(payload);
   if(!clearPending(user.id,'return')){
    setLocked(true);setError('Возврат выполнен, но запись восстановления осталась. Выполните сверку.');
   }
   await refresh();
   if(hasPermission('RETURNS_READ'))nav('/returns/'+r.id,{replace:true});
   else {setLocked(true);setError('Возврат создан: '+r.id+'. Для просмотра требуется RETURNS_READ.');}
  }catch(cause){
   setError(userFacingApiError(cause));
   if(!(cause instanceof ApiClientError)||cause.kind!=='http'||cause.status===undefined||cause.status>=500||cause.status===409){
    setPending(payload);setLocked(true);
    setError('Результат возврата неизвестен. Не создавайте другую операцию. '+userFacingApiError(cause));
   }else{
    if(!clearPending(user.id,'return'))setLocked(true);
   }
  }finally{inFlight.current=false;setBusy(false);}
 }
 if(!allowed)return <ForbiddenPage/>;
 return <div className="space-y-5">
  <PageHeader title="Новый возврат" description="Частичный или полный физический возврат после выполненной продажи."/>
  {hasPermission('RETURNS_READ')&&<Link className="font-semibold text-indigo-700 underline" to="/returns">← Список возвратов</Link>}
  {error&&<p role="alert" className="rounded-xl bg-amber-50 p-4 text-sm text-amber-900">{error}</p>}
  {locked&&<section aria-label="Неизвестный результат возврата" className="space-y-3 rounded-xl bg-amber-50 p-4 text-sm">
   <h3 className="font-bold">Операция возврата заблокирована до сверки</h3>
   <p>Ключ сохранён в текущей вкладке без текста причины, комментариев и персональных данных. После перезагрузки нужно проверить возврат через список или администратора.</p>
   {stored.kind==='pending'&&<p className="break-all">Исходный ключ: {stored.data.key} · Продажа: {stored.data.entityId}</p>}
   {pending&&<Button disabled={busy} onClick={()=>void submit(pending,true)}>Повторить тот же запрос и ключ</Button>}
   {hasPermission('RETURNS_READ')&&<Link to="/returns" className="font-semibold text-indigo-700 underline">Проверить возвраты</Link>}
  </section>}
  {!locked&&<ReturnForm initialSaleId={uuid(saleId)?saleId:''} disabled={busy} onSubmit={submit}/>}
 </div>;
}
