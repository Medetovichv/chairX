import { useRef,useState,type FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { returnsApi,returnKeys } from '../api';
import { SaleItemsEditor,type SaleEditorRow } from '../../sales/components/SaleItemsEditor';
import { Button } from '../../../shared/ui/button';
import { ConfirmDialog } from '../../../shared/ui/confirm-dialog';
import { Input } from '../../../shared/ui/input';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState';
import { validateExchange,uuid,positive } from '../validation';
import type { ExchangeInput } from '../types';
import type { FulfillmentType } from '../../sales/types';
export function ExchangeForm({initialReturnId,onSubmit,disabled=false}:{
 initialReturnId:string;onSubmit:(input:Omit<ExchangeInput,'idempotencyKey'>)=>Promise<void>;disabled?:boolean;
}){
 const {hasPermission}=useAuth();
 const [returnId,setReturnId]=useState(initialReturnId);
 const [kind,setKind]=useState<FulfillmentType>('SELF_PICKUP');
 const [rows,setRows]=useState<SaleEditorRow[]>([]);
 const [error,setError]=useState('');
 const [candidate,setCandidate]=useState<Omit<ExchangeInput,'idempotencyKey'>|null>(null);
 const original=useQuery({queryKey:returnKeys.detail(returnId),queryFn:()=>returnsApi.get(returnId),
   enabled:hasPermission('RETURNS_READ')&&uuid(returnId)});
 const creating=useRef(false);
 async function submit(e:FormEvent){
  e.preventDefault();if(disabled||creating.current)return;
  const data:ExchangeInput={idempotencyKey:crypto.randomUUID(),returnId,
   fulfillmentType:kind,items:rows.map(r=>({
    productVariantId:r.productVariantId,warehouseId:r.warehouseId,quantity:Number(r.quantity),
    unitSalePrice:r.unitSalePrice.trim(),
   }))};
  if(rows.some(r=>!positive(r.quantity))){setError('Количество должно быть целым положительным.');return;}
  const problem=validateExchange(data);
  if(problem){setError(problem);return;}
  const {idempotencyKey:unused,...plain}=data;
  void unused;
  setError('');
  setCandidate(plain);
 }
 async function confirm(){
  if(!candidate||creating.current)return;
  creating.current=true;
  const draft=candidate;setCandidate(null);
  try{await onSubmit(draft);}
  finally{creating.current=false;}
 }
 return <form aria-label="Создать обмен" className="space-y-4 rounded-xl border bg-white p-4" onSubmit={e=>void submit(e)}>
  <label className="block space-y-1 text-sm font-semibold">UUID физического возврата *
   <Input aria-label="UUID возврата для обмена" value={returnId} disabled={disabled}
    onChange={e=>setReturnId(e.target.value.trim())}/>
  </label>
  {original.isPending&&hasPermission('RETURNS_READ')&&uuid(returnId)&&<LoadingState label="Загружаем исходный возврат…"/>}
  {original.isError&&<ErrorState error={original.error} onRetry={()=>void original.refetch()}/>}
  {original.data&&<div className="rounded-xl bg-slate-50 p-3 text-sm">
   <p>Возврат {original.data.id} · Позиции: {original.data.items.length}</p>
   <p>Причина: {original.data.reason}</p>
  </div>}
  {!hasPermission('RETURNS_READ')&&<p className="text-sm text-amber-800">
   Нет RETURNS_READ: введите известный UUID возврата; backend проверит, что он существует и не использован.
  </p>}
  <label className="block space-y-1 text-sm font-semibold">Тип выдачи нового товара
   <select aria-label="Тип выдачи обмена" className="min-h-11 w-full rounded-xl border p-2"
    value={kind} disabled={disabled} onChange={e=>setKind(e.target.value as FulfillmentType)}>
    <option value="SELF_PICKUP">Самовывоз</option>
    <option value="CITY_DELIVERY">Городская доставка</option>
    <option value="REGION_DELIVERY">Региональная доставка</option>
   </select>
  </label>
  <SaleItemsEditor rows={rows} onChange={setRows} disabled={disabled}/>
  <p className="text-xs text-slate-500">
   Новую продажу создаёт ExchangeService. Доплата или возврат денег не проводятся в F09.
  </p>
  {error&&<p role="alert" className="text-sm text-red-700">{error}</p>}
  <Button type="submit" disabled={disabled||candidate!==null}>Проверить и создать обмен</Button>
  <ConfirmDialog open={candidate!==null} onOpenChange={open=>{if(!open)setCandidate(null);}}
    title="Подтвердить создание обмена?"
    description={candidate?'Возврат '+candidate.returnId+
      '. Новых позиций: '+candidate.items.length+
      '. Тип выдачи: '+candidate.fulfillmentType+
      '. Backend создаст новую продажу и складской резерв. Денежную разницу рассчитает сервер.':
      ''}
    confirmLabel="Создать обмен" onConfirm={()=>void confirm()}/>
 </form>;
}
