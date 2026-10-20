import { useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../features/auth/AuthProvider';
import { salesApi, salesKeys } from '../../features/sales/api';
import { ApiClientError, userFacingApiError } from '../api/api-error';
import { ConfirmDialog } from '../ui/confirm-dialog';
import { readSaleAction, saveSaleAction, clearSaleAction } from './pending-sale-action';
import type { SaleResponse } from '../../features/sales/types';

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
    if(busyRef.current||unknown||invalid)return;
    busyRef.current=true;setBusy(true);setError('');
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
      const result=action==='confirm'?await salesApi.confirm(sale.id):
        action==='fulfill'?await salesApi.fulfill(sale.id):await salesApi.cancel(sale.id);
      if(!clearSaleAction(user.id, sale.id)) {
        setInvalid(true);
        setError('Операция выполнена, но запись восстановления не удалось очистить. Действия заблокированы.');
      }
      await update(result);
      setNotice('Операция подтверждена сервером.');
    } catch(cause) {
      setError(userFacingApiError(cause));
      if(!(cause instanceof ApiClientError)||cause.kind!=='http'||
        cause.status===undefined||cause.status===409||cause.status>=500) {
        setUnknown(action);
      } else if(user?.id && !clearSaleAction(user.id,sale.id)) {
        setInvalid(true);
      }
    } finally {
      busyRef.current=false;setBusy(false);setIntent(null);
    }
  }
  async function verify() {
    if(busyRef.current)return;
    busyRef.current=true;setBusy(true);
    try {
      const current=await salesApi.get(sale.id);
      await update(current);
      const expected=unknown==='confirm'?'CONFIRMED':unknown==='fulfill'?'FULFILLED':'CANCELLED';
      if(current.status===expected) {
        if(user?.id && clearSaleAction(user.id,sale.id)) {
          setUnknown(null);setError('');setNotice('Результат подтверждён сервером.');
        } else {setInvalid(true);setError('Подтверждено, но запись восстановления не очищена.');}
      } else {
        setError('Результат не подтверждён. Не повторяйте операцию без ручной сверки.');
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
      <p>Исход операции неизвестен. Другие операции заблокированы до проверки.</p>
      <button type="button" disabled={busy} className="rounded-xl border px-3 py-2" onClick={()=>void verify()}>Проверить состояние сервера</button>
    </div>}
    <div className="flex flex-wrap gap-2">
      {canConfirm&&<button type="button" disabled={busy||!!unknown||invalid} className="rounded-xl bg-blue-600 px-4 py-2 text-white" onClick={()=>setIntent('confirm')}>Подтвердить заказ</button>}
      {canFulfill&&<button type="button" disabled={busy||!!unknown} className="rounded-xl bg-blue-600 px-4 py-2 text-white" onClick={()=>setIntent('fulfill')}>Завершить выдачу</button>}
      {canCancel&&<button type="button" disabled={busy||!!unknown} className="rounded-xl border px-4 py-2" onClick={()=>setIntent('cancel')}>Отменить заказ</button>}
    </div>
    <ConfirmDialog open={!!intent} onOpenChange={open=>{if(!open)setIntent(null);}}
      title={intent==='confirm'?'Подтвердить заказ?':intent==='fulfill'?'Выдать товар?':'Отменить заказ?'}
      description={'Заказ '+sale.saleNumber+', '+sale.items.length+' позиций, сумма '+sale.total+' сом. Проверьте данные: операция изменит складской учёт.'}
      busy={busy} destructive={intent==='cancel'} confirmLabel="Подтвердить"
      onConfirm={()=>{if(intent)void execute(intent);}}/>
  </section>;
}
