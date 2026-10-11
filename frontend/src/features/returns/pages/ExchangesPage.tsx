import { useEffect,useRef,useState } from 'react';
import { useNavigate,useSearchParams } from 'react-router';
import { useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { ApiClientError,userFacingApiError } from '../../../shared/api/api-error';
import { PageHeader } from '../../../shared/components/PageHeader';
import { ForbiddenPage } from '../../../pages/ForbiddenPage';
import { Button } from '../../../shared/ui/button';
import { Input } from '../../../shared/ui/input';
import { ReturnsTabs } from '../components/ReturnsTabs';
import { ExchangeForm } from '../components/ExchangeForm';
import { exchangesApi } from '../api';
import { persistPending,readPending,clearPending } from '../pending-operation';
import { salesKeys } from '../../sales/api';
import { inventoryKeys } from '../../inventory/api';
import { uuid } from '../validation';
import type { ExchangeInput } from '../types';
function exchangeIdFromLink(value:string):string {
 try {
  const url=new URL(value,window.location.origin);
  const match = new RegExp('^/returns/exchanges/([0-9a-f-]+)/?$','i').exec(url.pathname);
  return match&&uuid(match[1])?match[1]:'';
 }catch{return '';}
}
export function ExchangesPage(){
 const {hasPermission,user}=useAuth(),read=hasPermission('EXCHANGES_READ'),create=hasPermission('EXCHANGES_CREATE');
 const [params]=useSearchParams(),navigate=useNavigate(),client=useQueryClient();
 const [lookup,setLookup]=useState(''),[stored]=useState(()=>readPending(user?.id,'exchange'));
 const [locked,setLocked]=useState(stored.kind!=='missing'),[pending,setPending]=useState<ExchangeInput|null>(null);
 const [error,setError]=useState(''),[busy,setBusy]=useState(false);
 const inFlight=useRef(false);
 useEffect(()=>{if(!locked&&!busy)return;const fn=(e:BeforeUnloadEvent)=>{e.preventDefault();e.returnValue='';};
   window.addEventListener('beforeunload',fn);return()=>window.removeEventListener('beforeunload',fn);},[locked,busy]);
 async function submit(input:Omit<ExchangeInput,'idempotencyKey'>,retry=false){
  if(inFlight.current||!create||locked&&!retry)return;
  const payload=retry?pending:{...input,idempotencyKey:crypto.randomUUID()};
  if(!payload||!user?.id)return;
  if(!retry&&!persistPending(user.id,'exchange',payload.idempotencyKey,payload.returnId)){
   setLocked(true);setError('Не удалось сохранить ключ обмена.');return;
  }
  inFlight.current=true;setBusy(true);setError('');
  try{
   const result=await exchangesApi.create(payload);
   if(!clearPending(user.id,'exchange')){setLocked(true);setError('Обмен создан, но ключ восстановления не удалось очистить.');}
   await Promise.all([
    client.invalidateQueries({queryKey:['exchanges']}),
    client.invalidateQueries({queryKey:salesKeys.lists}),
    client.invalidateQueries({queryKey:inventoryKeys.overviewPrefix}),
    client.invalidateQueries({queryKey:inventoryKeys.balancePrefix}),
   ]);
   if(read)navigate('/returns/exchanges/'+result.id,{replace:true});
   else{setLocked(true);setError('Обмен создан. Для просмотра требуется EXCHANGES_READ.');}
  }catch(cause){
   setError(userFacingApiError(cause));
   if(!(cause instanceof ApiClientError)||cause.kind!=='http'||cause.status===409||cause.status===undefined||cause.status>=500){
    setPending(payload);setLocked(true);
    setError('Результат создания обмена неизвестен. Не создавайте второй заказ. '+userFacingApiError(cause));
   }else if(!clearPending(user.id,'exchange'))setLocked(true);
  }finally{inFlight.current=false;setBusy(false);}
 }
 if(!read&&!create)return <ForbiddenPage/>;
 return <div className="space-y-5">
  <PageHeader title="Обмены" description="Оформляйте обмен из карточки возврата. Существующий обмен можно открыть по сохранённой ссылке."/>
  <ReturnsTabs/>
  {read&&<form className="flex flex-wrap items-end gap-2 rounded-xl bg-white p-4"
    onSubmit={e=>{e.preventDefault();if(exchangeIdFromLink(lookup))navigate('/returns/exchanges/'+exchangeIdFromLink(lookup));}}>
    <label className="space-y-1 text-sm font-semibold">Ссылка на существующий обмен
      <Input aria-label="Ссылка на обмен" value={lookup} onChange={e=>setLookup(e.target.value.trim())}/>
    </label><Button type="submit" disabled={!exchangeIdFromLink(lookup)}>Открыть обмен</Button>
    <p className="w-full text-xs text-slate-500">Вставьте ссылку из истории браузера или карточки обмена. Поиск по всем обменам пока недоступен.</p>
   </form>}
  {error&&<p role="alert" className="rounded-xl bg-amber-50 p-3 text-sm">{error}</p>}
  {locked&&<section aria-label="Неопределённый обмен" className="space-y-2 rounded-xl bg-amber-50 p-4 text-sm">
    <p className="font-bold">Исход создания обмена требует проверки.</p>
    <p>Новый запрос запрещён до сверки результата. Для восстановления обратитесь к администратору.</p>
    {stored.kind==='pending'&&<p>Данные незавершённой операции сохранены для безопасной сверки.</p>}
    {pending&&<Button disabled={busy} onClick={()=>void submit(pending,true)}>Повторить исходный запрос</Button>}
  </section>}
  {create&&!locked&&<ExchangeForm initialReturnId={uuid(params.get('returnId')??'')?params.get('returnId')!:''}
    disabled={busy} onSubmit={submit}/>}
  {!create&&<p>Нет EXCHANGES_CREATE: создание обмена недоступно.</p>}
 </div>;
}
,'i').exec(url.pathname);
  return match&&uuid(match[1])?match[1]:'';
 }catch{return '';}
}
export function ExchangesPage(){
 const {hasPermission,user}=useAuth(),read=hasPermission('EXCHANGES_READ'),create=hasPermission('EXCHANGES_CREATE');
 const [params]=useSearchParams(),navigate=useNavigate(),client=useQueryClient();
 const [lookup,setLookup]=useState(''),[stored]=useState(()=>readPending(user?.id,'exchange'));
 const [locked,setLocked]=useState(stored.kind!=='missing'),[pending,setPending]=useState<ExchangeInput|null>(null);
 const [error,setError]=useState(''),[busy,setBusy]=useState(false);
 const inFlight=useRef(false);
 useEffect(()=>{if(!locked&&!busy)return;const fn=(e:BeforeUnloadEvent)=>{e.preventDefault();e.returnValue='';};
   window.addEventListener('beforeunload',fn);return()=>window.removeEventListener('beforeunload',fn);},[locked,busy]);
 async function submit(input:Omit<ExchangeInput,'idempotencyKey'>,retry=false){
  if(inFlight.current||!create||locked&&!retry)return;
  const payload=retry?pending:{...input,idempotencyKey:crypto.randomUUID()};
  if(!payload||!user?.id)return;
  if(!retry&&!persistPending(user.id,'exchange',payload.idempotencyKey,payload.returnId)){
   setLocked(true);setError('Не удалось сохранить ключ обмена.');return;
  }
  inFlight.current=true;setBusy(true);setError('');
  try{
   const result=await exchangesApi.create(payload);
   if(!clearPending(user.id,'exchange')){setLocked(true);setError('Обмен создан, но ключ восстановления не удалось очистить.');}
   await Promise.all([
    client.invalidateQueries({queryKey:['exchanges']}),
    client.invalidateQueries({queryKey:salesKeys.lists}),
    client.invalidateQueries({queryKey:inventoryKeys.overviewPrefix}),
    client.invalidateQueries({queryKey:inventoryKeys.balancePrefix}),
   ]);
   if(read)navigate('/returns/exchanges/'+result.id,{replace:true});
   else{setLocked(true);setError('Обмен создан. Для просмотра требуется EXCHANGES_READ.');}
  }catch(cause){
   setError(userFacingApiError(cause));
   if(!(cause instanceof ApiClientError)||cause.kind!=='http'||cause.status===409||cause.status===undefined||cause.status>=500){
    setPending(payload);setLocked(true);
    setError('Результат создания обмена неизвестен. Не создавайте второй заказ. '+userFacingApiError(cause));
   }else if(!clearPending(user.id,'exchange'))setLocked(true);
  }finally{inFlight.current=false;setBusy(false);}
 }
 if(!read&&!create)return <ForbiddenPage/>;
 return <div className="space-y-5">
  <PageHeader title="Обмены" description="Оформляйте обмен из карточки возврата. Существующий обмен можно открыть по сохранённой ссылке."/>
  <ReturnsTabs/>
  {read&&<form className="flex flex-wrap items-end gap-2 rounded-xl bg-white p-4"
    onSubmit={e=>{e.preventDefault();if(exchangeIdFromLink(lookup))navigate('/returns/exchanges/'+exchangeIdFromLink(lookup));}}>
    <label className="space-y-1 text-sm font-semibold">Ссылка на существующий обмен
      <Input aria-label="Ссылка на обмен" value={lookup} onChange={e=>setLookup(e.target.value.trim())}/>
    </label><Button type="submit" disabled={!exchangeIdFromLink(lookup)}>Открыть обмен</Button>
    <p className="w-full text-xs text-slate-500">Вставьте ссылку из истории браузера или карточки обмена. Поиск по всем обменам пока недоступен.</p>
   </form>}
  {error&&<p role="alert" className="rounded-xl bg-amber-50 p-3 text-sm">{error}</p>}
  {locked&&<section aria-label="Неопределённый обмен" className="space-y-2 rounded-xl bg-amber-50 p-4 text-sm">
    <p className="font-bold">Исход создания обмена требует проверки.</p>
    <p>Новый запрос запрещён до сверки результата. Для восстановления обратитесь к администратору.</p>
    {stored.kind==='pending'&&<p>Данные незавершённой операции сохранены для безопасной сверки.</p>}
    {pending&&<Button disabled={busy} onClick={()=>void submit(pending,true)}>Повторить исходный запрос</Button>}
  </section>}
  {create&&!locked&&<ExchangeForm initialReturnId={uuid(params.get('returnId')??'')?params.get('returnId')!:''}
    disabled={busy} onSubmit={submit}/>}
  {!create&&<p>Нет EXCHANGES_CREATE: создание обмена недоступно.</p>}
 </div>;
}
