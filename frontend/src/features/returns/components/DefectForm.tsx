import { useRef,useState,type FormEvent } from 'react';
import { useQuery,useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { inventoryApi,inventoryKeys } from '../../inventory/api';
import { SaleVariantPicker } from '../../sales/components/SaleVariantPicker';
import {supplierApi,purchaseKeys} from '../../purchases/api';
import { Input } from '../../../shared/ui/input';
import { Button } from '../../../shared/ui/button';
import { ApiClientError,userFacingApiError } from '../../../shared/api/api-error';
import { defectsApi,returnKeys } from '../api';
import { positive,validateDefect } from '../validation';
import type { OpenDefectInput } from '../types';
import {persistPending,readPending,clearPending} from '../pending-operation';
export function DefectForm({onCreated}:{onCreated:()=>void}){
 const {hasPermission,user}=useAuth(),client=useQueryClient();
 const [warehouseId,setWarehouseId]=useState(''),[variantId,setVariantId]=useState('');
 const [supplierId,setSupplierId]=useState(''),[receiptId,setReceiptId]=useState('');
 const [count,setCount]=useState('1'),[description,setDescription]=useState('');
 const [error,setError]=useState('');
 const [blocked,setBlocked]=useState(()=>readPending(user?.id,'defect').kind!=='missing');
 const busy=useRef(false);
 const allowed=hasPermission('DEFECTS_MANAGE'),warehouseRead=hasPermission('INVENTORY_READ');
 const warehouses=useQuery({queryKey:inventoryKeys.warehouses,queryFn:inventoryApi.warehouses,enabled:allowed&&warehouseRead});
 const supplierRead=hasPermission('SUPPLIERS_READ');
 const suppliers=useQuery({queryKey:purchaseKeys.supplierAll,queryFn:supplierApi.all,
   enabled:allowed&&supplierRead});
 async function submit(e:FormEvent){
  e.preventDefault();if(busy.current||blocked||!allowed)return;
  const data:OpenDefectInput={warehouseId,productVariantId:variantId,
    supplierId:supplierId.trim()||null,purchaseReceiptItemId:receiptId.trim()||null,
    quantity:Number(count),description:description.trim()};
  const problem=validateDefect(data);
  if(problem||!positive(count)){setError(problem||'Количество некорректно.');return;}
  // POST /api/defects has no idempotencyKey: store a safe marker before POST.
  // On ambiguous outcomes never retry with a new request.
  if(!user?.id||!persistPending(user.id,'defect',crypto.randomUUID(),data.productVariantId)){
    setBlocked(true);setError('Не удалось записать операцию в хранилище. POST не отправлен.');return;
  }
  busy.current=true;setError('');
  try{
   const result=await defectsApi.open(data);
   if(!clearPending(user.id,'defect')){setBlocked(true);setError('Дефект создан, но ключ восстановления не удалось очистить.');}
   await Promise.all([
    client.invalidateQueries({queryKey:returnKeys.defects}),
    client.invalidateQueries({queryKey:inventoryKeys.overviewPrefix}),
    client.invalidateQueries({queryKey:inventoryKeys.balancePrefix}),
   ]);
   void result;onCreated();
  }catch(cause){
   setError(userFacingApiError(cause));
   if(!(cause instanceof ApiClientError)||cause.kind!=='http'||cause.status===undefined||cause.status>=500||cause.status===409){
    setBlocked(true);
    setError('Результат регистрации брака неизвестен. Не создавайте повторную блокировку. Проверьте список дефектов.');
   }else if(!clearPending(user.id,'defect'))setBlocked(true);
  }finally{busy.current=false;}
 }
 if(!allowed)return null;
 return <form aria-label="Регистрация дефекта" onSubmit={e=>void submit(e)}
  className="space-y-4 rounded-xl border bg-white p-4">
  <h3 className="text-lg font-bold">Новый дефект</h3>
  <label className="block space-y-1 text-sm font-semibold">Склад *
   {warehouseRead?<select aria-label="Склад брака" value={warehouseId} onChange={e=>setWarehouseId(e.target.value)}
    disabled={blocked} className="min-h-11 w-full rounded-xl border p-2">
    <option value="">Выберите склад</option>{warehouses.data?.filter(w=>w.active).map(w=><option value={w.id} key={w.id}>{w.name}</option>)}
   </select>:<Input aria-label="UUID склада брака" value={warehouseId} disabled={blocked} onChange={e=>setWarehouseId(e.target.value)}/>}
  </label>
  <label className="block space-y-1 text-sm font-semibold">UUID вариации *
    <Input aria-label="UUID вариации брака" value={variantId} disabled={blocked} onChange={e=>setVariantId(e.target.value)}/>
  </label>
  {hasPermission('CATALOG_READ')&&<SaleVariantPicker value={variantId} onSelect={v=>setVariantId(v.id)} disabled={blocked}/>}
  <label className="block space-y-1 text-sm font-semibold">Количество *
   <Input aria-label="Количество брака" value={count} disabled={blocked} onChange={e=>setCount(e.target.value)} inputMode="numeric"/>
  </label>
  <label className="block space-y-1 text-sm font-semibold">Описание неисправности *
   <textarea aria-label="Описание брака" value={description} maxLength={4000} disabled={blocked}
    onChange={e=>setDescription(e.target.value)} className="w-full rounded-xl border p-3" rows={3}/>
  </label>
  <label className="block space-y-1 text-sm font-semibold">Поставщик (необязательно)
   {supplierRead?<select aria-label="Поставщик брака" value={supplierId} disabled={blocked||suppliers.isPending}
     onChange={e=>setSupplierId(e.target.value)}
     className="min-h-11 w-full rounded-xl border bg-white p-2">
     <option value="">Происхождение не указано</option>
     {suppliers.data?.filter(x=>x.active).map(x=><option key={x.id} value={x.id}>{x.name}</option>)}
   </select>:<Input aria-label="UUID поставщика брака" value={supplierId} disabled={blocked}
     onChange={e=>setSupplierId(e.target.value.trim())} placeholder="Реальный UUID поставщика"/>}
  </label>
  {supplierRead&&suppliers.isError&&<p role="alert" className="text-sm text-red-700">
    Список поставщиков не загружен. <Button type="button" variant="outline" onClick={()=>void suppliers.refetch()}>Повторить</Button>
  </p>}
  <label className="block space-y-1 text-sm font-semibold">UUID позиции поступления (необязательно)
   <Input aria-label="UUID поступления брака" value={receiptId} disabled={blocked} onChange={e=>setReceiptId(e.target.value)}/>
  </label>
  <p className="text-xs text-slate-600">Если происхождение неизвестно, оставьте оба поля пустыми. RETURN BLOCKED уже блокирует товар — не открывайте повторный Defect на те же единицы.</p>
  {blocked&&<p className="rounded-xl bg-amber-50 p-3 text-sm">Повторная регистрация заблокирована до ручной сверки с backend. Перезагрузка страницы не сбрасывает запись в текущей вкладке.</p>}
  {error&&<p role="alert" className="rounded-xl bg-amber-50 p-3 text-sm">{error}</p>}
  <Button disabled={blocked} type="submit">Зарегистрировать брак</Button>
 </form>;
}
