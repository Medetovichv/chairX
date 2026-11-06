import { useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../features/auth/AuthProvider';
import { salesApi, salesKeys } from '../../features/sales/api';
import { ApiClientError, userFacingApiError } from '../api/api-error';
import { saleStatusNames } from '../../features/sales/validation';
import { ConfirmDialog } from '../ui/confirm-dialog';
import { readSaleAction, readSaleOperationId, saveSaleAction, clearSaleAction, isUnknownSaleMutationResult } from './pending-sale-action';
import type { SaleResponse } from '../../features/sales/types';
import {cancellationReasons,type SaleCancellationReason} from '../../features/sales/cancellation';

type Action = 'confirm' | 'fulfill' | 'cancel';
function snapshot(s: SaleResponse) {
  return JSON.stringify({
    status:s.status, customerId:s.customerId, fulfillmentType:s.fulfillmentType,
    comment:s.comment, total:String(s.total),
    items:s.items.map(i=>[i.id,i.productVariantId,i.warehouseId,i.quantity,String(i.unitSalePrice)]).sort((a,b)=>String(a[0]).localeCompare(String(b[0]))),
  });
}
export function SaleQuickActions({sale}:{sale:SaleResponse}) {
  const {hasPermission,user}=useAuth();
  const queryClient=useQueryClient();
  const busyRef=useRef(false);
  const [busy,setBusy]=useState(false);
  const [intent,setIntent]=useState<Action|null>(null);
  const [stored]=useState(() => readSaleAction(user?.id, sale.id));
  const [unknown,setUnknown]=useState<Action|null>(stored.kind === 'pending' ? stored.action : null);
  const [invalid,setInvalid]=useState(stored.kind === 'invalid');
  const [error,setError]=useState('');
  const [notice,setNotice]=useState('');
  const [showCancelForm,setShowCancelForm]=useState(false);
  const [cancelReason,setCancelReason]=useState<SaleCancellationReason|''>('');
  const [cancelComment,setCancelComment]=useState('');
  const draft=sale.status==='DRAFT';
  const confirmed=sale.status==='CONFIRMED';
  const canConfirm=draft&&hasPermission('SALES_DRAFT_MANAGE')&&!!sale.fulfillmentType&&sale.items.length>0;
  const canFulfill=confirmed&&sale.fulfillmentType==='SELF_PICKUP'&&hasPermission('SALES_UPDATE');
  const canCancel=hasPermission('SALES_CANCEL')&&(draft||(confirmed&&sale.fulfillmentType==='SELF_PICKUP'));
  async function update(result:SaleResponse) {
    queryClient.setQueryData(salesKeys.detail(sale.id),result);
    await Promise.all([
      queryClient.invalidateQueries({queryKey:salesKeys.lists}),
      queryClient.invalidateQueries({queryKey:salesKeys.kanban}),
      queryClient.invalidateQueries({queryKey:salesKeys.historyPrefix(sale.id)}),
    ]);
  }
  async function execute(action:Action) {
    if(busyRef.current||unknown||invalid||action==='cancel'&&(!cancelReason||cancelComment.length>1000))return;
    busyRef.current=true;setBusy(true);setError('');
    let sent=false;
    try {
      const current=await salesApi.get(sale.id);
      if(snapshot(current)!==snapshot(sale)) {
        await update(current);
        setError('Данные заказа изменились. Перепроверьте сумму и позиции перед повторным действием.');
        return;
      }
      if(!user?.id || !saveSaleAction(user.id, sale.id, action)) {
        setInvalid(true);
        setError('Не удалось сохранить запись операции. Запрос не отправлен.');
        return;
      }
      const operationId=readSaleOperationId(user.id, sale.id);
      if(!operationId) {
        setInvalid(true);
        setError('Не удалось сохранить идентификатор операции. Запрос не отправлен; требуется ручная проверка.');
        return;
      }
      sent=true;
      const result=action==='confirm'?await salesApi.confirm(sale.id, operationId):
        action==='fulfill'?await salesApi.fulfill(sale.id, operationId):await salesApi.cancelWithReason(sale.id,{reason:cancelReason as SaleCancellationReason,comment:cancelComment.trim()||null},operationId);
      sent=false; // A successful API response is authoritative: cache invalidation is not a second mutation.
      if(!clearSaleAction(user.id, sale.id)) {
        setInvalid(true);
        setError('Операция выполнена, но запись восстановления не удалось очистить. Действия заблокированы.');
      }
      await update(result);
      setNotice('Операция подтверждена сервером.');
    } catch(cause) {
      setError(userFacingApiError(cause));
      if(sent && isUnknownSaleMutationResult(cause)) {
        setUnknown(action);
      } else if(sent && user?.id && !clearSaleAction(user.id,sale.id)) {
        // Preflight GET never sent a mutation and owns no journal entry.
        // Clearing it here could erase another mounted surface's pending POST.
        setInvalid(true);
      }
    } finally {
      busyRef.current=false;setBusy(false);setIntent(null);
    }
  }
  async function verify() {
    if(busyRef.current || !unknown)return;
    busyRef.current=true;setBusy(true);setNotice('');
    try {
      const operationId=readSaleOperationId(user?.id,sale.id);
      if(operationId){
        try {
          const receipt=await salesApi.operationReceipt(sale.id,operationId);
          if(receipt.status==='COMMITTED'&&receipt.operationId===operationId&&
              receipt.action===unknown&&receipt.sale.id===sale.id){
            if(!user?.id || !clearSaleAction(user.id,sale.id)){
              setInvalid(true);
              setError('Сервер подтвердил операцию, но журнал не удалось очистить. Действия заблокированы.');
              return;
            }
            await update(receipt.sale);
            setUnknown(null);
            setError('');
            setNotice('Сервер подтвердил результат именно вашего запроса.');
            return;
          }
          setError('Сервер вернул несоответствующую квитанцию. Действия заблокированы; нужна ручная проверка.');
          return;
        } catch(cause) {
          // A 404 or transport error cannot prove that an in-flight POST failed.
          // Read the current state only for display; never clear the pending marker.
          if(!(cause instanceof ApiClientError && cause.status===404)){
            setError('Квитанция операции недоступна. Результат запроса неизвестен; нужна ручная проверка.');
          }
        }
      }
      const current=await salesApi.get(sale.id);
      await update(current);
      const expected=unknown==='confirm'?'CONFIRMED':unknown==='fulfill'?'FULFILLED':'CANCELLED';
      // A matching final status is NOT evidence that this user's particular POST
      // committed. Another operator could have independently changed the sale.
      // These endpoints expose no action idempotency key / request correlation.
      // Keep the durable pending marker and the mutation controls locked.
      if(current.status===expected) {
        setError('Текущий статус заказа совпадает с ожидаемым, но исход именно вашего запроса не доказан. Запись операции сохранена. Не повторяйте действие: требуется ручная сверка с аудитом.');
      } else {
        setError('Текущий статус заказа: '+saleStatusNames[current.status]+'. Исход отправленного запроса остаётся неизвестным. Не повторяйте действие без ручной сверки с аудитом.');
      }
    } catch(cause) {setError(userFacingApiError(cause));}
    finally {busyRef.current=false;setBusy(false);}
  }
  if(!canConfirm&&!canFulfill&&!canCancel&&!unknown&&!invalid)return null;
  return <section aria-label="Изменение статуса продажи" className="space-y-3 rounded-xl border border-slate-200 bg-white p-4">
    <h3 className="font-semibold">Управление заказом</h3>
    {error&&<p role="alert" className="text-sm text-red-700">{error}</p>}
    {notice&&<p role="status" className="text-sm text-green-700">{notice}</p>}
    {invalid&&<p role="alert" className="text-sm text-amber-900">Хранилище операции недоступно или повреждено. Новые действия заблокированы — нужна ручная сверка.</p>}
    {unknown&&<div className="space-y-2 rounded-xl bg-amber-50 p-3 text-sm">
      <p>Исход операции неизвестен. Действия заблокированы до ручной сверки с аудитом; проверка статуса сама по себе не разблокирует заказ.</p>
      <button type="button" disabled={busy} className="rounded-xl border px-3 py-2" onClick={()=>void verify()}>Проверить состояние сервера</button>
    </div>}
    <div className="flex flex-wrap gap-2">
      {canConfirm&&<button type="button" disabled={busy||!!unknown||invalid} className="rounded-xl bg-blue-600 px-4 py-2 text-white" onClick={()=>void execute('confirm')}>Подтвердить заказ</button>}
      {canFulfill&&<button type="button" disabled={busy||!!unknown||invalid} className="rounded-xl bg-blue-600 px-4 py-2 text-white" onClick={()=>setIntent('fulfill')}>Завершить выдачу</button>}
      {canCancel&&<button type="button" disabled={busy||!!unknown||invalid} className="rounded-xl border px-4 py-2" onClick={()=>setShowCancelForm(true)}>Отменить заказ</button>}
    </div>
    {showCancelForm&&<div className="space-y-3 rounded-xl border border-amber-200 bg-amber-50 p-4">
      <label className="block space-y-1 text-sm font-semibold">Причина отмены *
        <select aria-label="Причина отмены" required value={cancelReason}
          disabled={busy} onChange={e=>setCancelReason(e.target.value as SaleCancellationReason|'')}
          className="min-h-11 w-full rounded-xl border border-slate-300 bg-white px-3">
          <option value="">Выберите причину</option>
          {cancellationReasons.map(r=><option key={r.value} value={r.value}>{r.label}</option>)}
        </select>
      </label>
      <label className="block space-y-1 text-sm font-semibold">Комментарий (необязательно)
        <textarea aria-label="Комментарий отмены" value={cancelComment} maxLength={1000}
          disabled={busy} onChange={e=>setCancelComment(e.target.value)}
          className="min-h-20 w-full rounded-xl border border-slate-300 bg-white p-3"/>
      </label>
      <p className="text-xs text-slate-700">Оплаченную продажу нельзя отменить напрямую — сначала требуется компенсация или возврат оплаты.</p>
      <div className="flex flex-wrap gap-2">
        <button type="button" className="min-h-11 rounded-xl bg-red-700 px-4 text-white disabled:opacity-50"
          disabled={busy||!cancelReason} onClick={()=>setIntent('cancel')}>Продолжить отмену</button>
        <button type="button" className="min-h-11 rounded-xl border px-4" onClick={()=>setShowCancelForm(false)}>Не отменять</button>
      </div>
    </div>}
    <ConfirmDialog open={!!intent} onOpenChange={open=>{if(!open)setIntent(null);}}
      title={intent==='confirm'?'Подтвердить заказ?':intent==='fulfill'?'Выдать товар?':'Отменить заказ?'}
      description={'Заказ '+sale.saleNumber+', '+sale.items.length+' позиций, сумма '+sale.total+' сом. Проверьте данные: операция изменит складской учёт.'}
      busy={busy} destructive={intent==='cancel'} confirmLabel="Подтвердить"
      onConfirm={()=>{if(intent)void execute(intent);}}/>
  </section>;
}
