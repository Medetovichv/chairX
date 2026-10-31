import { useEffect,useState,type FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { useAuth } from '../../auth/AuthProvider';
import { salesApi,salesKeys } from '../../sales/api';
import { displaySaleNumber } from '../../../shared/lib/format';
import { saleStatusNames,fulfillmentNames } from '../../sales/validation';
import { inventoryApi,inventoryKeys } from '../../inventory/api';
import { Button } from '../../../shared/ui/button';
import { Input } from '../../../shared/ui/input';
import { ErrorState } from '../../../shared/components/ErrorState';
import { LoadingState } from '../../../shared/components/LoadingState';
import { returnsApi,returnKeys } from '../api';
import { uuid,positive,conditionLabels,validateReturn } from '../validation';
import type { ReturnInput,ReturnCondition } from '../types';
import { ReturnChairLabel } from './ReturnChairLabel';

export function ReturnForm({initialSaleId,onSubmit,disabled=false}:{
 initialSaleId:string;onSubmit:(input:Omit<ReturnInput,'idempotencyKey'>)=>Promise<void>;disabled?:boolean;
}){
 const {hasPermission}=useAuth();
 const [saleId,setSaleId]=useState(initialSaleId);
 const [search,setSearch]=useState('');
 const [term,setTerm]=useState('');
 const [page,setPage]=useState(0);
 useEffect(()=>{
   const timer=setTimeout(()=>setTerm(search.trim().replace(/^#/,'')),300);
   return ()=>clearTimeout(timer);
 },[search]);
 const [warehouseId,setWarehouseId]=useState('');
 const [quantities,setQuantities]=useState<Record<string,string>>({});
 const [conditions,setConditions]=useState<Record<string,ReturnCondition>>({});
 const [reason,setReason]=useState(''),[comment,setComment]=useState(''),[error,setError]=useState('');
 const canSale=hasPermission('SALES_READ');
 const canInventory=hasPermission('INVENTORY_READ');
 const salePicker=useQuery({
  queryKey:salesKeys.list({status:'FULFILLED',from:'',to:'',number:term,page}),
  queryFn:()=>salesApi.list({status:'FULFILLED',from:'',to:'',number:term,page}),
  enabled:canSale&&!saleId,
 });
 const sale=useQuery({queryKey:salesKeys.detail(saleId),queryFn:()=>salesApi.get(saleId),
  enabled:canSale&&uuid(saleId)});
 const summary=useQuery({
  queryKey:[...salesKeys.lists,'return-eligibility',sale.data?.saleNumber??''],
  queryFn:()=>salesApi.list({status:'',from:'',to:'',number:sale.data!.saleNumber,page:0}),
  enabled:canSale&&!!sale.data&&sale.data.fulfillmentType!=='SELF_PICKUP',
 });
 const history=useQuery({queryKey:returnKeys.history(saleId),queryFn:()=>returnsApi.history(saleId),
  enabled:hasPermission('RETURNS_READ')&&canSale&&sale.data?.status==='FULFILLED'});
 const warehouses=useQuery({queryKey:inventoryKeys.warehouses,queryFn:inventoryApi.warehouses,enabled:canInventory});
 useEffect(()=>{setQuantities({});setConditions({});},[saleId]);
 const eligible=sale.data?.status==='FULFILLED'&&(
  sale.data.fulfillmentType==='SELF_PICKUP'||
  summary.isSuccess&&summary.data.items.some(s=>s.id===saleId&&s.deliveryStatus==='DELIVERED'));
 const safeToReturn=!!eligible&&history.isSuccess;
 async function submit(e:FormEvent){
  e.preventDefault();
  if(disabled)return;
  const items=(sale.data?.items??[]).filter(i=>quantities[i.id]&&quantities[i.id]!=='0').map(i=>({
   saleItemId:i.id,quantity:Number(quantities[i.id]),condition:conditions[i.id]??'SELLABLE',
  }));
  const data:ReturnInput={saleId,warehouseId,idempotencyKey:crypto.randomUUID(),items,
   reason:reason.trim(),comment:comment.trim()||null};
  const problem=validateReturn(data);
  if(problem||!safeToReturn||items.some(i=>!positive(String(i.quantity)))){
   setError(problem||'Продажа, доставка или история возвратов не подтверждены сервером.');
   return;
  }
  for(const item of items){
   const sold=sale.data?.items.find(i=>i.id===item.saleItemId);
   const returned=history.data.reduce((n,r)=>n+r.items.filter(i=>i.saleItemId===item.saleItemId).reduce((sum,i)=>sum+i.quantity,0),0);
   if(!sold||item.quantity>sold.quantity-returned){setError('Количество превышает оставшееся по этой позиции.');return;}
  }
  setError('');
  const {idempotencyKey:unused,...payload}=data;
  void unused;
  await onSubmit(payload);
 }
 return <form aria-label="Создание возврата" className="space-y-4 rounded-xl border bg-white p-4"
    onSubmit={e=>void submit(e)}>
  <section aria-label="Выбор продажи для возврата"
    className="space-y-3 rounded-2xl border border-slate-200 bg-white p-4 sm:p-5">
   <h3 className="font-bold text-slate-900">1. Выберите выполненную продажу</h3>
   {!canSale&&<p role="alert" className="rounded-xl bg-amber-50 p-3 text-sm">
     Необходимо SALES_READ, чтобы выбрать заказ и безопасно проверить статус продажи.
   </p>}
   {canSale&&saleId&&<div className="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-blue-50 p-3">
     <div className="min-w-0 text-sm">
       <span className="text-slate-600">Заказ: </span>
       {sale.data?<Link className="font-semibold text-blue-700 hover:underline"
         to={'/sales/'+encodeURIComponent(saleId)}>{displaySaleNumber(sale.data.saleNumber)}</Link>:
         <span>Загружаем сведения о заказе…</span>}
     </div>
     <Button type="button" variant="outline" size="sm" disabled={disabled}
       onClick={()=>{setSaleId('');setError('');setPage(0);}}>Выбрать другой</Button>
   </div>}
   {canSale&&!saleId&&<>
     <label className="block space-y-2 text-sm font-semibold">Поиск по номеру заказа
       <Input type="search" aria-label="Поиск выполненной продажи" value={search} maxLength={100}
         disabled={disabled} placeholder="Введите номер продажи"
         onChange={e=>{setSearch(e.target.value);setPage(0);}}/>
     </label>
     {salePicker.isPending?<LoadingState label="Ищем выполненные продажи…"/>:
       salePicker.isError?<ErrorState error={salePicker.error}
         onRetry={()=>void salePicker.refetch()}/>:
       <div className="space-y-2">
         {salePicker.data?.items.length===0&&<p className="text-sm text-slate-600">
           Выполненные продажи по запросу не найдены.</p>}
         {salePicker.data?.items.map(candidate=>
           <Button key={candidate.id} type="button" variant="outline"
             disabled={disabled} className="w-full justify-start"
             onClick={()=>{setSaleId(candidate.id);setError('');}}>
             {displaySaleNumber(candidate.saleNumber)} · {candidate.customerName||'Клиент не указан'}
           </Button>)}
         {!!salePicker.data&&salePicker.data.total>20&&
           <div className="flex items-center justify-between gap-2 text-sm">
             <Button type="button" variant="outline" size="sm" disabled={page===0}
               onClick={()=>setPage(n=>Math.max(0,n-1))}>Назад</Button>
             <span>Страница {page+1}</span>
             <Button type="button" variant="outline" size="sm"
               disabled={(page+1)*20>=salePicker.data.total}
               onClick={()=>setPage(n=>n+1)}>Далее</Button>
           </div>}
       </div>}
     <p className="text-xs text-slate-600">
       Показываются только выполненные продажи. Возможность возврата и остаток количества
       дополнительно проверяются по заказу и истории возвратов.
     </p>
   </>}
  </section>
  {sale.isPending&&canSale&&uuid(saleId)&&<LoadingState label="Загружаем позиции продажи…"/>}
  {sale.isError&&<ErrorState error={sale.error} onRetry={()=>void sale.refetch()}/>}
  {sale.data&&<p className="rounded-xl bg-slate-50 p-3 text-sm">
    {displaySaleNumber(sale.data.saleNumber)} · {saleStatusNames[sale.data.status]} ·
    {sale.data.fulfillmentType?fulfillmentNames[sale.data.fulfillmentType]:'Способ не указан'}
  </p>}
  {sale.data&&sale.data.fulfillmentType!=='SELF_PICKUP'&&!summary.isPending&&!eligible&&
   <p role="alert" className="rounded-xl bg-amber-50 p-3 text-sm">Для возврата доставки необходим статус DELIVERED. READY / IN_TRANSIT / FAILED возвращаются через F08, не через эту форму.</p>}
  {sale.data?.status==='FULFILLED'&&<div className="space-y-2">
    {history.isPending?<LoadingState label="Проверяем все предыдущие возвраты…"/>:
      history.isError?<ErrorState error={history.error} onRetry={()=>void history.refetch()}/>:
      sale.data.items.map((i,index)=>{
       const already=history.data.reduce((n,r)=>n+r.items.filter(x=>x.saleItemId===i.id).reduce((sum,x)=>sum+x.quantity,0),0);
       const remaining=Math.max(0,i.quantity-already);
       const warehouse=warehouses.data?.find(w=>w.id===i.warehouseId);
       return <div key={i.id} className="space-y-3 rounded-xl border border-slate-200 bg-white p-4 text-sm">
        <div>
          <p className="text-xs font-semibold text-slate-500">Позиция {index+1}</p>
          <p className="mt-1 font-semibold text-slate-950">
            <ReturnChairLabel variantId={i.productVariantId}/>
          </p>
          {canInventory&&<p className="mt-1 text-xs text-slate-600">
            Продано со склада: <strong>{warehouse?.name??'Название склада недоступно'}</strong>
          </p>}
        </div>
        <p>Продано: {i.quantity} · Возвращено: {already} · Можно вернуть: <strong>{remaining}</strong></p>
        <div className="grid gap-3 sm:grid-cols-2">
         <label className="space-y-1">Количество к возврату
          <Input aria-label={'Количество возврата — позиция '+(index+1)} inputMode="numeric" value={quantities[i.id]??'0'}
           disabled={disabled||!safeToReturn||remaining===0} onChange={e=>setQuantities(s=>({...s,[i.id]:e.target.value}))}/>
         </label>
         <label className="space-y-1">Состояние товара
          <select aria-label={'Состояние товара — позиция '+(index+1)} value={conditions[i.id]??'SELLABLE'}
           disabled={disabled||!safeToReturn||remaining===0}
           onChange={e=>setConditions(s=>({...s,[i.id]:e.target.value as ReturnCondition}))}
           className="min-h-11 w-full rounded-xl border bg-white p-2">
           {(['SELLABLE','BLOCKED'] as const).map(c=><option key={c} value={c}>{conditionLabels[c]}</option>)}
          </select>
         </label>
        </div>
       </div>;
      })}
   </div>}
  <label className="block space-y-1 text-sm font-semibold">Склад возврата *
    {canInventory?<select aria-label="Склад возврата" value={warehouseId} disabled={disabled||warehouses.isPending}
     onChange={e=>setWarehouseId(e.target.value)} className="min-h-11 w-full rounded-xl border p-2">
     <option value="">Выберите активный склад</option>
     {warehouses.data?.filter(w=>w.active).map(w=><option key={w.id} value={w.id}>{w.name}</option>)}
    </select>:<p role="alert" className="text-sm text-amber-800">
      Для выбора склада возврата необходимо INVENTORY_READ. Попросите администратора предоставить доступ.
    </p>}
  </label>
  <label className="block space-y-1 text-sm font-semibold">Причина *
   <select aria-label="Причина возврата" value={reason} disabled={disabled} onChange={e=>setReason(e.target.value)}
    className="min-h-11 w-full rounded-xl border bg-white p-3">
    <option value="">Выберите причину</option>
    {['Не понравился товар','Не соответствует ожиданиям','Не подошёл размер или удобство','Цвет или модель не подошли','Обнаружен недостаток','Ошибочный заказ','Другая причина'].map(value=><option key={value} value={value}>{value}</option>)}
   </select>
  </label>
  <label className="block space-y-1 text-sm font-semibold">Комментарий
   <textarea aria-label="Комментарий возврата" value={comment} maxLength={1000} disabled={disabled} onChange={e=>setComment(e.target.value)}
    className="w-full rounded-xl border p-3" rows={2}/>
  </label>
  {error&&<p role="alert" className="rounded-xl bg-red-50 p-3 text-sm text-red-800">{error}</p>}
  <p className="text-xs text-slate-600">SELLABLE вернётся в доступный остаток; BLOCKED уже создаёт RETURN_IN и блокирует товар. Новый Defect не создаётся автоматически.</p>
  <Button type="submit" disabled={disabled||!safeToReturn||!canInventory}>
    Подтвердить физический возврат
  </Button>
 </form>;
}
