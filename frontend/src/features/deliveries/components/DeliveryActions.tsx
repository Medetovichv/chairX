import { useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { inventoryApi, inventoryKeys } from '../../inventory/api';
import { apiClient } from '../../../shared/api/client';
import { ApiClientError, userFacingApiError } from '../../../shared/api/api-error';
import { Button } from '../../../shared/ui/button';
import { Input } from '../../../shared/ui/input';
import { ConfirmDialog } from '../../../shared/ui/confirm-dialog';
import { ErrorState } from '../../../shared/components/ErrorState';
import { deliveryApi } from '../api';
import { sameDelivery, validLocalDate } from '../validation';
import type { DeliveryResponse } from '../types';
type Action='dispatch'|'deliver'|'fail'|'cancel'|'return';
interface Intent {action:Action;reason?:string;warehouseId?:string}
function reached(d:DeliveryResponse, x:Intent):boolean{
  if(x.action==='dispatch')return d.status==='IN_TRANSIT'||d.status==='DELIVERED';
  if(x.action==='deliver')return d.status==='DELIVERED';
  if(x.action==='fail')return d.status==='FAILED'&&d.failureReason===x.reason;
  if(x.action==='cancel')return d.status==='CANCELLED';
  return d.status==='FAILED'&&!!d.returnedToWarehouseAt&&d.returnWarehouseId===x.warehouseId;
}
export function DeliveryActions({delivery:d,onChanged}:{
  delivery:DeliveryResponse;onChanged:(d:DeliveryResponse)=>Promise<void>;
}){
  const {hasPermission}=useAuth();
  const manage=hasPermission('DELIVERIES_MANAGE');
  const canCancel=hasPermission('SALES_CANCEL');
  const canInventory=hasPermission('INVENTORY_READ');
  const [intent,setIntent]=useState<Intent|null>(null);
  const [uncertain,setUncertain]=useState<Intent|null>(null);
  const [pendingDate,setPendingDate]=useState<string|null|undefined>(undefined);
  const [date,setDate]=useState(d.plannedDeliveryDate??'');
  const [reason,setReason]=useState('');
  const [warehouse,setWarehouse]=useState('');
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState('');
  const [notice,setNotice]=useState('');
  const running=useRef(false);
  const selectWarehouse=manage&&canInventory&&d.status==='FAILED'&&!d.returnedToWarehouseAt;
  const warehouses=useQuery({queryKey:inventoryKeys.warehouses,queryFn:inventoryApi.warehouses,
    enabled:selectWarehouse});
  const canPlan=manage&&(d.status==='READY'||d.status==='IN_TRANSIT');
  const canDispatch=manage&&d.status==='READY';
  const canDeliver=manage&&d.status==='IN_TRANSIT';
  const canFail=manage&&d.status==='IN_TRANSIT';
  const canReturn=manage&&d.status==='FAILED'&&!d.returnedToWarehouseAt;
  const canAbort=canCancel&&d.status==='READY';
  useEffect(()=>setDate(d.plannedDeliveryDate??''),[d.id,d.plannedDeliveryDate]);
  useEffect(()=>{
    if(!uncertain&&!busy)return;
    const warn=(e:BeforeUnloadEvent)=>{e.preventDefault();e.returnValue='';};
    window.addEventListener('beforeunload',warn);
    return()=>window.removeEventListener('beforeunload',warn);
  },[uncertain,busy]);
  const explanation:Record<Action,string>={
    dispatch:'Товар будет выдан со склада: backend освободит резерв, создаст SALE_OUT и переведёт продажу в FULFILLED. Повторный fulfill выполнять нельзя.',
    deliver:'Подтверждайте только фактическое вручение клиенту. Второго складского списания и регистрации оплаты не будет.',
    fail:'Неуспех не возвращает товар на склад. Причина останется в карточке; для физического возврата есть отдельная операция.',
    cancel:'Отмена READY-доставки также отменит связанную продажу и освободит её резерв.',
    return:'Физический возврат на выбранный склад создаст RETURN_IN и восстановит cost layers на сервере. Это не возврат денег.',
  };
  async function send(action:Intent){
    if(running.current||uncertain)return;
    running.current=true;setBusy(true);setError('');setNotice('');setIntent(null);
    try{
      // Fail closed if another employee updated the status since it was read.
      const fresh=await deliveryApi.get(d.id);
      if(!sameDelivery(d,fresh)){
        await onChanged(fresh);
        setError('Доставка изменена другим сотрудником. Проверьте новые данные и повторно подтвердите действие.');
        return;
      }
      const result=action.action==='dispatch'?await deliveryApi.dispatch(d.id):
        action.action==='deliver'?await deliveryApi.deliver(d.id):
        action.action==='cancel'?await deliveryApi.cancel(d.id):
        action.action==='fail'?await deliveryApi.fail(d.id,action.reason!):
        await deliveryApi.returnToWarehouse(d.id,action.warehouseId!);
      await onChanged(result);
      setNotice('Операция подтверждена сервером.');
      setReason('');setWarehouse('');
    }catch(cause){
      setError(userFacingApiError(cause));
      if(!(cause instanceof ApiClientError)||cause.kind!=='http'||cause.status===undefined||cause.status>=500){
        setUncertain(action);
      }else if(cause instanceof ApiClientError&&cause.status===409){
        // A competing operation may have completed. Force an explicit reread.
        setUncertain(action);
      }
    }finally{running.current=false;setBusy(false);}
  }
  async function check(){
    if(running.current)return;
    running.current=true;setBusy(true);
    try{
      const fresh=await deliveryApi.get(d.id);
      await onChanged(fresh);
      if(uncertain&&reached(fresh,uncertain)){
        setNotice('По данным backend операция уже выполнена.');
        setUncertain(null);setError('');
      }else setError('Результат не подтверждён. Не повторяйте необратимый POST без ручной сверки.');
      if(pendingDate!==undefined){
        if(fresh.plannedDeliveryDate===pendingDate){setPendingDate(undefined);setUncertain(null);
          setError('');setNotice('Плановая дата проверена по данным backend.');}
        else setError('Изменение даты не подтверждено. Сначала проверьте журнал и сверьте данные.');
      }
    }catch(cause){setError(userFacingApiError(cause));}
    finally{running.current=false;setBusy(false);}
  }
  async function changeDate(){
    if(running.current||uncertain||pendingDate!==undefined||!canPlan)return;
    if(date&&!validLocalDate(date)){setError('Некорректная плановая дата.');return;}
    const target=date||null;
    running.current=true;setBusy(true);setError('');setNotice('');
    try {
      const fresh=await deliveryApi.get(d.id);
      if(!sameDelivery(d,fresh)){
        await onChanged(fresh);setError('Данные доставки изменились. Проверьте новую плановую дату.');return;
      }
      const result=await deliveryApi.plannedDate(d.id,target);
      await onChanged(result);setNotice('Плановая дата изменена.');
    }catch(cause){
      setError(userFacingApiError(cause));
      if(!(cause instanceof ApiClientError)||cause.kind!=='http'||cause.status===undefined||cause.status>=500){
        setPendingDate(target);
      }
    }finally{running.current=false;setBusy(false);}
  }
  if(!manage&&!canCancel)return null;
  const disabled=busy||uncertain!==null||pendingDate!==undefined;
  return <section aria-label="Управление доставкой" className="space-y-4 rounded-2xl border border-slate-200 bg-white p-4">
    <h3 className="text-lg font-bold">Действия</h3>
    {error&&<p role="alert" className="rounded-xl bg-red-50 p-3 text-sm text-red-800">{error}</p>}
    {notice&&<p role="status" className="rounded-xl bg-emerald-50 p-3 text-sm text-emerald-800">{notice}</p>}
    {(uncertain||pendingDate!==undefined)&&<div className="space-y-2 rounded-xl bg-amber-50 p-3 text-sm text-amber-900">
      <p>Исход операции неизвестен. Кнопки других изменений заблокированы до проверки backend.</p>
      <Button variant="outline" disabled={busy} onClick={()=>void check()}>Проверить актуальное состояние</Button>
    </div>}
    {canPlan&&<div className="space-y-2">
      <label className="block space-y-1 text-sm font-semibold">Планируемая дата
        <Input aria-label="Изменить плановую дату" type="date" value={date} disabled={disabled}
          onChange={e=>setDate(e.target.value)}/>
      </label>
      <div className="flex flex-wrap gap-2">
        <Button disabled={disabled||(date||null)===d.plannedDeliveryDate}
          variant="outline" onClick={()=>void changeDate()}>Сохранить плановую дату</Button>
        <Button disabled={disabled||!d.plannedDeliveryDate} variant="outline"
          onClick={()=>setDate('')}>Очистить дату</Button>
      </div>
    </div>}
    <div className="flex flex-wrap gap-2">
      {canDispatch&&<Button disabled={disabled} onClick={()=>setIntent({action:'dispatch'})}>Отправить доставку</Button>}
      {canDeliver&&<Button disabled={disabled} onClick={()=>setIntent({action:'deliver'})}>Подтвердить вручение</Button>}
      {canAbort&&<Button variant="destructive" disabled={disabled}
        onClick={()=>setIntent({action:'cancel'})}>Отменить доставку и продажу</Button>}
    </div>
    {canFail&&<div className="space-y-2">
      <label className="block space-y-1 text-sm font-semibold">Причина неуспешной доставки
        <textarea aria-label="Причина неуспешной доставки" disabled={disabled} maxLength={1000}
          value={reason} onChange={e=>setReason(e.target.value)}
          className="w-full rounded-xl border border-slate-200 p-3" rows={3}/>
      </label>
      <Button disabled={disabled||!reason.trim()} variant="outline"
        onClick={()=>{if(reason.trim())setIntent({action:'fail',reason:reason.trim()});}}>
        Зафиксировать неуспешную доставку</Button>
    </div>}
    {canReturn&&<div className="space-y-2 rounded-xl border border-amber-200 p-3">
      <p className="font-semibold">Физический возврат товара на склад</p>
      {!canInventory?<p className="text-sm text-amber-900">
        Требуется INVENTORY_READ для выбора реального активного склада.</p>:
        warehouses.isError?<ErrorState error={warehouses.error} onRetry={()=>void warehouses.refetch()}/>:
        <label className="block space-y-1 text-sm font-semibold">Склад возврата
          <select aria-label="Склад возврата" value={warehouse} disabled={disabled||warehouses.isPending}
            onChange={e=>setWarehouse(e.target.value)}
            className="min-h-11 w-full rounded-xl border border-slate-200 bg-white p-3">
            <option value="">Выберите активный склад</option>
            {warehouses.data?.filter(w=>w.active).map(w=>
              <option value={w.id} key={w.id}>{w.name}</option>)}
          </select>
        </label>}
      <Button disabled={disabled||!warehouse||!canInventory||!warehouses.data?.some(w=>w.active&&w.id===warehouse)}
        onClick={()=>setIntent({action:'return',warehouseId:warehouse})}>
        Вернуть товар на склад</Button>
    </div>}
    <ConfirmDialog open={intent!==null} onOpenChange={open=>{if(!open)setIntent(null);}}
      title={intent?.action==='dispatch'?'Отправить доставку?':
        intent?.action==='deliver'?'Подтвердить вручение заказа?':
        intent?.action==='cancel'?'Отменить доставку и продажу?':
        intent?.action==='fail'?'Зафиксировать неуспешную доставку?':
        'Вернуть товар на склад?'}
      description={intent?explanation[intent.action]:''} destructive={intent?.action==='cancel'}
      busy={busy} confirmLabel="Подтвердить действие"
      onConfirm={()=>{if(intent)void send(intent);}}/>
  </section>;
}
