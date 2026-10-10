import { useEffect,useState,type FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { salesApi,salesKeys } from '../../sales/api';
import { inventoryApi,inventoryKeys } from '../../inventory/api';
import { Button } from '../../../shared/ui/button';
import { Input } from '../../../shared/ui/input';
import { ErrorState } from '../../../shared/components/ErrorState';
import { LoadingState } from '../../../shared/components/LoadingState';
import { returnsApi,returnKeys } from '../api';
import { uuid,positive,conditionLabels,validateReturn } from '../validation';
import type { ReturnInput,ReturnCondition } from '../types';

export function ReturnForm({initialSaleId,onSubmit,disabled=false}:{
 initialSaleId:string;onSubmit:(input:Omit<ReturnInput,'idempotencyKey'>)=>Promise<void>;disabled?:boolean;
}){
 const {hasPermission}=useAuth();
 const [saleId,setSaleId]=useState(initialSaleId);
 const [warehouseId,setWarehouseId]=useState('');
 const [quantities,setQuantities]=useState<Record<string,string>>({});
 const [conditions,setConditions]=useState<Record<string,ReturnCondition>>({});
 const [reason,setReason]=useState(''),[comment,setComment]=useState(''),[error,setError]=useState('');
 const canSale=hasPermission('SALES_READ');
 const canInventory=hasPermission('INVENTORY_READ');
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
  <label className="block space-y-1 text-sm font-semibold">UUID продажи *
   <Input aria-label="UUID продажи" value={saleId} disabled={disabled} onChange={e=>setSaleId(e.target.value.trim())}/>
  </label>
  {!canSale&&<p className="rounded-xl bg-amber-50 p-3 text-sm">Необходимо SALES_READ, чтобы выбрать saleItemId и безопасно проверить статус продажи.</p>}
  {sale.isPending&&canSale&&uuid(saleId)&&<LoadingState label="Загружаем позиции продажи…"/>}
  {sale.isError&&<ErrorState error={sale.error} onRetry={()=>void sale.refetch()}/>}
  {sale.data&&<p className="rounded-xl bg-slate-50 p-3 text-sm">
    {sale.data.saleNumber} · {sale.data.status} · {sale.data.fulfillmentType??'Способ не указан'}
  </p>}
  {sale.data&&sale.data.fulfillmentType!=='SELF_PICKUP'&&!summary.isPending&&!eligible&&
   <p role="alert" className="rounded-xl bg-amber-50 p-3 text-sm">Для возврата доставки необходим статус DELIVERED. READY / IN_TRANSIT / FAILED возвращаются через F08, не через эту форму.</p>}
  {sale.data?.status==='FULFILLED'&&<div className="space-y-2">
    {history.isPending?<LoadingState label="Проверяем все предыдущие возвраты…"/>:
      history.isError?<ErrorState error={history.error} onRetry={()=>void history.refetch()}/>:
      sale.data.items.map(i=>{
       const already=history.data.reduce((n,r)=>n+r.items.filter(x=>x.saleItemId===i.id).reduce((sum,x)=>sum+x.quantity,0),0);
       const remaining=Math.max(0,i.quantity-already);
       return <div key={i.id} className="space-y-2 rounded-xl border p-3 text-sm">
        <p className="break-all font-semibold">Вариация: {i.productVariantId}</p>
        <p className="break-all text-xs text-slate-500">saleItemId: {i.id} · Исходный склад: {i.warehouseId}</p>
        <p>Продано: {i.quantity} · Возвращено: {already} · Можно вернуть: <strong>{remaining}</strong></p>
        <div className="grid gap-3 sm:grid-cols-2">
         <label className="space-y-1">Количество к возврату
          <Input aria-label={'Количество возврата '+i.id} inputMode="numeric" value={quantities[i.id]??'0'}
           disabled={disabled||!safeToReturn||remaining===0} onChange={e=>setQuantities(s=>({...s,[i.id]:e.target.value}))}/>
         </label>
         <label className="space-y-1">Состояние товара
          <select aria-label={'Состояние '+i.id} value={conditions[i.id]??'SELLABLE'}
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
    </select>:<Input aria-label="Склад возврата UUID" value={warehouseId} disabled={disabled}
      onChange={e=>setWarehouseId(e.target.value.trim())} placeholder="UUID склада; проверит backend"/>}
  </label>
  <label className="block space-y-1 text-sm font-semibold">Причина *
   <textarea aria-label="Причина возврата" value={reason} maxLength={500} disabled={disabled} onChange={e=>setReason(e.target.value)}
    className="w-full rounded-xl border p-3" rows={2}/>
  </label>
  <label className="block space-y-1 text-sm font-semibold">Комментарий
   <textarea aria-label="Комментарий возврата" value={comment} maxLength={1000} disabled={disabled} onChange={e=>setComment(e.target.value)}
    className="w-full rounded-xl border p-3" rows={2}/>
  </label>
  {error&&<p role="alert" className="rounded-xl bg-red-50 p-3 text-sm text-red-800">{error}</p>}
  <p className="text-xs text-slate-600">SELLABLE вернётся в доступный остаток; BLOCKED уже создаёт RETURN_IN и блокирует товар. Новый Defect не создаётся автоматически.</p>
  <Button type="submit" disabled={disabled||!safeToReturn}>Подтвердить физический возврат</Button>
 </form>;
}
