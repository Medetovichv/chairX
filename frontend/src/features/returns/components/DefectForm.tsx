import { useRef,useState,type FormEvent } from 'react';
import { useQuery,useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { inventoryApi,inventoryKeys } from '../../inventory/api';
import { SaleVariantPicker } from '../../sales/components/SaleVariantPicker';
import {supplierApi,purchaseKeys} from '../../purchases/api';
import { Input } from '../../../shared/ui/input';
import { ReturnChairLabel } from './ReturnChairLabel';
import { Button } from '../../../shared/ui/button';
import { ApiClientError,userFacingApiError } from '../../../shared/api/api-error';
import { defectsApi,returnKeys } from '../api';
import { positive,validateDefect } from '../validation';
import type { OpenDefectInput } from '../types';
import {persistPending,readPending,clearPending} from '../pending-operation';
export function DefectForm({onCreated}:{onCreated:()=>void}){
 const {hasPermission,user}=useAuth(),client=useQueryClient();
 const [warehouseId,setWarehouseId]=useState(''),[variantId,setVariantId]=useState('');
 const [supplierId,setSupplierId]=useState('');
 const [count,setCount]=useState('1'),[description,setDescription]=useState('');
 const [error,setError]=useState('');
 const [blocked,setBlocked]=useState(()=>readPending(user?.id,'defect').kind!=='missing');
 const busy=useRef(false);
 const allowed=hasPermission('DEFECTS_MANAGE'),warehouseRead=hasPermission('INVENTORY_READ');
 const catalogRead=hasPermission('CATALOG_READ');
 const canChoose=warehouseRead&&catalogRead;
 const warehouses=useQuery({queryKey:inventoryKeys.warehouses,queryFn:inventoryApi.warehouses,enabled:allowed&&warehouseRead});
 const supplierRead=hasPermission('SUPPLIERS_READ');
 const suppliers=useQuery({queryKey:purchaseKeys.supplierAll,queryFn:supplierApi.all,
   enabled:allowed&&supplierRead});
 async function submit(e:FormEvent){
  e.preventDefault();if(busy.current||blocked||!allowed||!canChoose)return;
  const data:OpenDefectInput={warehouseId,productVariantId:variantId,
    supplierId:supplierRead&&supplierId.trim()?supplierId:null,purchaseReceiptItemId:null,
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
  className="space-y-5 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm sm:p-6">
  <h3 className="text-lg font-bold text-slate-950">Новое сообщение о браке</h3>
  <p className="text-sm text-slate-600">Выберите кресло и склад, опишите неисправность.
   После регистрации товар будет заблокирован по правилам backend.</p>
  {!canChoose&&<p role="alert" className="rounded-xl bg-amber-50 p-3 text-sm text-amber-900">
    Для регистрации через интерфейс нужны CATALOG_READ и INVENTORY_READ.
    Обратитесь к администратору за разрешениями. Технические идентификаторы вводить не нужно.
  </p>}
  <div className="grid gap-5 lg:grid-cols-2">
   <section aria-label="Шаг 1. Кресло" className="space-y-3 rounded-xl border border-slate-200 p-4">
    <h4 className="font-bold">1. Кресло и склад</h4>
    {catalogRead&&<SaleVariantPicker value={variantId} disabled={blocked}
      onSelect={variant=>{setVariantId(variant.id);setError('');}}/>}
    {!catalogRead&&<p className="text-sm text-slate-600">Каталог недоступен для вашей роли.</p>}
    {catalogRead&&variantId&&<p className="text-sm">Выбрано: <ReturnChairLabel variantId={variantId}/></p>}
    {warehouseRead?<label className="block space-y-1 text-sm font-semibold">Склад *
      <select aria-label="Склад брака" value={warehouseId} onChange={e=>setWarehouseId(e.target.value)}
        disabled={blocked||warehouses.isPending||warehouses.isError}
        className="min-h-11 w-full rounded-xl border border-slate-200 bg-white p-2">
       <option value="">Выберите склад</option>
       {warehouses.data?.filter(w=>w.active).map(w=><option value={w.id} key={w.id}>{w.name}</option>)}
      </select>
     </label>:<p className="text-sm text-slate-600">Склады недоступны для вашей роли.</p>}
    {warehouseRead&&warehouses.isError&&<p role="alert" className="text-sm text-red-700">
      Не удалось загрузить склады.
      <Button type="button" variant="outline" onClick={()=>void warehouses.refetch()}>Повторить</Button>
    </p>}
    <label className="block space-y-1 text-sm font-semibold">Количество *
     <Input aria-label="Количество брака" value={count} disabled={blocked}
       onChange={e=>setCount(e.target.value)} inputMode="numeric"/>
    </label>
   </section>
   <section aria-label="Шаг 2. Неисправность" className="space-y-3 rounded-xl border border-slate-200 p-4">
    <h4 className="font-bold">2. Описание неисправности</h4>
    <label className="block space-y-1 text-sm font-semibold">Что неисправно? *
     <textarea aria-label="Описание брака" value={description} maxLength={4000} disabled={blocked}
       onChange={e=>setDescription(e.target.value)}
       className="w-full rounded-xl border border-slate-200 p-3" rows={5}/>
    </label>
    {supplierRead&&<label className="block space-y-1 text-sm font-semibold">Поставщик (необязательно)
      <select aria-label="Поставщик брака" value={supplierId} disabled={blocked||suppliers.isPending||suppliers.isError}
       onChange={e=>setSupplierId(e.target.value)}
       className="min-h-11 w-full rounded-xl border border-slate-200 bg-white p-2">
       <option value="">Происхождение не указано</option>
       {suppliers.data?.filter(x=>x.active).map(x=><option key={x.id} value={x.id}>{x.name}</option>)}
      </select>
     </label>}
    {supplierRead&&suppliers.isError&&<p role="alert" className="text-sm text-red-700">
      Список поставщиков не загружен.
      <Button type="button" variant="outline" onClick={()=>void suppliers.refetch()}>Повторить</Button>
    </p>}
    <p className="text-xs text-slate-600">
      Привязка к отдельной позиции поступления пока не имеет безопасного поиска в интерфейсе.
      Поле необязательно и не заполняется автоматически.
    </p>
   </section>
  </div>
  <p className="text-xs text-slate-600">
    Возвраты с состоянием «Заблокирован» уже блокируют товар —
    не регистрируйте тот же физический экземпляр повторно.
  </p>
  {blocked&&<p role="alert" className="rounded-xl bg-amber-50 p-3 text-sm">
    Повторная регистрация заблокирована до сверки с backend.
    Перезагрузка страницы не снимает блокировку операции.
  </p>}
  {error&&<p role="alert" className="rounded-xl bg-red-50 p-3 text-sm text-red-700">{error}</p>}
  <Button disabled={blocked||!canChoose||warehouses.isPending||warehouses.isError}
    type="submit">Зарегистрировать брак</Button>
 </form>;
}
