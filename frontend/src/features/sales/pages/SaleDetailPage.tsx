import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { catalogApi } from '../../catalog/api';
import { inventoryApi, inventoryKeys } from '../../inventory/api';
import type { Warehouse } from '../../inventory/types';
import { customersApi } from '../../customers/api';
import { deliveryApi, deliveryKeys } from '../../deliveries/api';
import { deliveryStatusNames } from '../../deliveries/validation';
import { ApiClientError, userFacingApiError } from '../../../shared/api/api-error';
import { PageHeader } from '../../../shared/components/PageHeader';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState';
import { ForbiddenPage } from '../../../pages/ForbiddenPage';
import { NotFoundPage } from '../../../pages/NotFoundPage';
import { ConfirmDialog } from '../../../shared/ui/confirm-dialog';
import { Button } from '../../../shared/ui/button';
import { salesApi, salesKeys } from '../api';
import { saleStatisticsKeys } from '../statistics';
import { SaleStatusBadge } from '../components/SaleStatusBadge';
import { SaleForm } from '../components/SaleForm';
import { SaleFinancialSection } from '../../finance/components/SaleFinancialSection';
import { fulfillmentNames, integerSom, money, saleDate, uuidValid } from '../validation';
import { displaySaleNumber } from '../../../shared/lib/format';
import type { DraftInput, SaleResponse, SaleSummary } from '../types';

type Action='confirm'|'fulfill'|'cancel';

/** Reject a stale irreversible-action confirmation before posting.
 * Backend still enforces final business rules under a database row lock.
 */
function sameSaleForAction(displayed:SaleResponse,current:SaleResponse):boolean {
  // If Java BigDecimal could not be represented exactly by JSON Number,
  // never approve a potentially changed amount on ambiguous precision.
  const shownTotal=integerSom(displayed.total),actualTotal=integerSom(current.total);
  if(shownTotal===null||actualTotal===null||
    displayed.items.some(i=>integerSom(i.unitSalePrice)===null)||
    current.items.some(i=>integerSom(i.unitSalePrice)===null))return false;
  if(displayed.id!==current.id||displayed.status!==current.status||
    displayed.customerId!==current.customerId||
    displayed.fulfillmentType!==current.fulfillmentType||
    displayed.comment!==current.comment||
    shownTotal!==actualTotal||
    displayed.items.length!==current.items.length)return false;
  const lines=(s:SaleResponse)=>s.items.map(i=>
    [i.id,i.warehouseId,i.productVariantId,String(i.quantity),
      integerSom(i.unitSalePrice)??'invalid'].join('|')).sort();
  return JSON.stringify(lines(displayed))===JSON.stringify(lines(current));
}

/** Checks whether an uncertain PUT actually wrote the submitted draft. */
function draftMatchesServer(input:DraftInput,current:SaleResponse):boolean {
  if(input.items.some(i=>integerSom(i.unitSalePrice)===null)||
    current.items.some(i=>integerSom(i.unitSalePrice)===null))return false;
  if(current.status!=='DRAFT'||input.customerId!==current.customerId||
    input.fulfillmentType!==current.fulfillmentType||
    (input.comment??'')!==(current.comment??'')||
    input.items.length!==current.items.length)return false;
  const expected=input.items.map(i=>[i.productVariantId,i.warehouseId,
    String(i.quantity),integerSom(i.unitSalePrice)??'invalid'].join('|')).sort();
  const actual=current.items.map(i=>[i.productVariantId,i.warehouseId,
    String(i.quantity),integerSom(i.unitSalePrice)??'invalid'].join('|')).sort();
  return JSON.stringify(expected)===JSON.stringify(actual);
}

function eventStamp(instant: string): string {
  const date = new Date(instant);
  if (Number.isNaN(date.getTime())) return 'Дата не указана';
  return new Intl.DateTimeFormat('ru-RU', {
    timeZone: 'Asia/Bishkek', day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  }).format(date);
}

function ItemLine({item,warehouses}: {
  item:SaleResponse['items'][number]; warehouses:Warehouse[]|null;
}) {
  const {hasPermission}=useAuth();
  const catalogRead=hasPermission('CATALOG_READ');
  const inventoryRead=hasPermission('INVENTORY_READ');
  const variant=useQuery({
    queryKey:['catalog','variant',item.productVariantId],
    queryFn:()=>catalogApi.getVariant(item.productVariantId),
    enabled:catalogRead,
  });
  const model=useQuery({
    queryKey:['catalog','product',variant.data?.productId],
    queryFn:()=>catalogApi.getProduct(variant.data!.productId),
    enabled:catalogRead&&!!variant.data?.productId,
  });
  const warehouse=warehouses?.find(w=>w.id===item.warehouseId);
  return <article className="grid gap-3 border-b border-slate-100 p-4 text-sm sm:grid-cols-[minmax(0,1fr)_auto]">
    <div className="min-w-0 space-y-1">
      <div className="font-semibold text-slate-900">
        {catalogRead&&model.data?
          <Link className="text-blue-700 underline-offset-2 hover:underline focus-visible:underline"
            to={'/catalog/'+encodeURIComponent(model.data.id)}>
            {model.data.name}</Link>:<span>Кресло</span>}
        {catalogRead&&variant.data&&<span> · {variant.data.name}</span>}
      </div>
      {catalogRead&&variant.data?.sku&&
        <p className="text-xs text-slate-500">Артикул: {variant.data.sku}</p>}
      {inventoryRead&&<p className="text-xs text-slate-600">
        Склад: {warehouse?
          <Link className="font-semibold text-blue-700 underline-offset-2 hover:underline focus-visible:underline"
            to={'/inventory?warehouseId='+encodeURIComponent(warehouse.id)}>
            {warehouse.name}</Link>:<strong>Название склада недоступно</strong>}
      </p>}
      {!inventoryRead&&<p className="text-xs text-slate-500">
        Данные склада доступны сотрудникам с INVENTORY_READ.
      </p>}
    </div>
    <div className="sm:text-right">
      <p>{item.quantity} шт. × {money(item.unitSalePrice)}</p>
      <p className="mt-1 font-bold text-slate-950">{money(item.total)}</p>
    </div>
  </article>;
}

function SaleDetail({sale}:{sale:SaleResponse}) {
  const {hasPermission}=useAuth();
  const client=useQueryClient();
  const loc=useLocation();
  const summary=((loc.state as {summary?:SaleSummary}|null)?.summary);
  const listed=useQuery({
    queryKey:[...salesKeys.lists,'summary',sale.id,sale.saleNumber],
    queryFn:()=>salesApi.list({status:'',from:'',to:'',number:sale.saleNumber,page:0}),
    enabled:hasPermission('SALES_READ'),
  });
  const authoritative=listed.data?.items.find(i=>i.id===sale.id);
  const matched=authoritative??(summary?.id===sale.id?summary:null);
  const initialNotice=(loc.state as {notice?:unknown}|null)?.notice;
  const [notice,setNotice]=useState(typeof initialNotice==='string'?initialNotice:'');
  const [error,setError]=useState('');
  const [editing,setEditing]=useState(false);
  const [draftUncertain,setDraftUncertain]=useState(false);
  const [submittedDraft,setSubmittedDraft]=useState<DraftInput|null>(null);
  const [action,setAction]=useState<Action|null>(null);
  const [unknownAction,setUnknownAction]=useState<Action|null>(null);
  const [busy,setBusy]=useState(false);
  const running=useRef(false);
  const isDraft=sale.status==='DRAFT';
  const isConfirmed=sale.status==='CONFIRMED';
  const canManageDraft=isDraft&&hasPermission('SALES_DRAFT_MANAGE');
  const canConfirm=canManageDraft&&!!sale.fulfillmentType&&sale.items.length>0;
  const canFulfill=isConfirmed&&sale.fulfillmentType==='SELF_PICKUP'&&hasPermission('SALES_UPDATE');
  const canCancel=hasPermission('SALES_CANCEL')&&
    (isDraft||isConfirmed&&sale.fulfillmentType==='SELF_PICKUP');
  // A Kanban drop is only a REQUEST to use this existing confirmation flow.
  // The server's current sale is loaded before mounting this component;
  // explicit confirmation still triggers the pre-POST stale-data check.
  const handledKanbanIntent=useRef(false);
  const requestedAction=(loc.state as {kanbanAction?:unknown}|null)?.kanbanAction;
  useEffect(()=>{
    if(handledKanbanIntent.current||!requestedAction)return;
    handledKanbanIntent.current=true;
    const valid=requestedAction==='confirm'&&canConfirm||
      requestedAction==='fulfill'&&canFulfill||
      requestedAction==='cancel'&&canCancel;
    if(valid){
      setNotice('Перемещение из Kanban: проверьте заказ и подтвердите действие. Остатки пока не изменились.');
      setAction(requestedAction as Action);
    }else{
      setError('Переход из Kanban больше недоступен: проверьте актуальные данные и права. Никаких изменений не выполнено.');
    }
  },[requestedAction,canConfirm,canFulfill,canCancel]);

  const warehouseList=useQuery({
    queryKey:inventoryKeys.warehouses,queryFn:inventoryApi.warehouses,
    enabled:hasPermission('INVENTORY_READ')&&sale.items.length>0,
  });
  const customer=useQuery({
    queryKey:['customers','customer',sale.customerId],
    queryFn:()=>customersApi.get(sale.customerId!),
    enabled:hasPermission('CUSTOMERS_READ')&&!!sale.customerId,
  });
  const customerLabel=customer.data?.fullName||customer.data?.phone||
    customer.data?.whatsappPhone||'Клиент недоступен';
  const requiresDelivery=sale.fulfillmentType!==null&&sale.fulfillmentType!=='SELF_PICKUP'&&
    (sale.status==='CONFIRMED'||sale.status==='FULFILLED');
  const canReadDelivery=hasPermission('DELIVERIES_READ');
  const relatedDelivery=useQuery({
    queryKey:deliveryKeys.bySale(sale.id),
    queryFn:()=>deliveryApi.bySale(sale.id),
    enabled:requiresDelivery&&canReadDelivery,
    retry:false,
  });
  const deliveryMissing=relatedDelivery.isError&&relatedDelivery.error instanceof ApiClientError&&
    relatedDelivery.error.status===404;
  const paymentStatus=matched?.paymentStatus==='PAID'?'Оплачено':
    matched?.paymentStatus==='UNPAID'?'Не оплачено':
    matched?.paymentStatus==='PARTIAL'?'Требуется проверить данные оплаты':
    'Статус оплаты недоступен';
  const delivery=canReadDelivery?relatedDelivery.data:undefined;
  const events=[
    {key:'created',label:'Заказ создан',at:sale.createdAt},
    ...(sale.fulfilledAt?[{key:'fulfilled',label:'Продажа выполнена',at:sale.fulfilledAt}]:[]),
    ...(sale.cancelledAt?[{key:'cancelled',label:'Заказ отменён',at:sale.cancelledAt}]:[]),
    ...(delivery?[
      {key:'delivery-created',label:'Доставка оформлена',at:delivery.createdAt},
      ...(delivery.dispatchedAt?[{key:'dispatched',label:'Доставка отправлена',at:delivery.dispatchedAt}]:[]),
      ...(delivery.deliveredAt?[{key:'delivered',label:'Доставка вручена',at:delivery.deliveredAt}]:[]),
      ...(delivery.failedAt?[{key:'delivery-failed',label:'Доставка не удалась',at:delivery.failedAt}]:[]),
      ...(delivery.returnedToWarehouseAt?[{key:'delivery-returned',label:'Товар возвращён на склад после доставки',at:delivery.returnedToWarehouseAt}]:[]),
    ]:[]),
  ].filter(e=>!!e.at&&!Number.isNaN(new Date(e.at).getTime()))
    .sort((a,b)=>new Date(a.at).getTime()-new Date(b.at).getTime());
  async function changed(result:SaleResponse) {
    client.setQueryData(salesKeys.detail(sale.id),result);
    await Promise.all([
      client.invalidateQueries({queryKey:salesKeys.lists}),
      client.invalidateQueries({queryKey:salesKeys.kanban}),
      client.invalidateQueries({queryKey:saleStatisticsKeys.last30Days}),
      client.invalidateQueries({queryKey:['dashboard']}),
      client.invalidateQueries({queryKey:inventoryKeys.overviewPrefix}),
      client.invalidateQueries({queryKey:inventoryKeys.balancePrefix}),
      client.invalidateQueries({queryKey:inventoryKeys.movementsPrefix}),
    ]);
  }
  async function update(input:DraftInput) {
    setSubmittedDraft(input);
    const next=await salesApi.updateDraft(sale.id,input);
    await changed(next);
    setEditing(false);setDraftUncertain(false);setSubmittedDraft(null);
    setError('');setNotice('Черновик обновлён.');
  }
  async function saveDraft(input:DraftInput) {
    if(running.current||draftUncertain)throw new ApiClientError('http','DRAFT_RESULT_UNKNOWN',409);
    running.current=true;
    try{await update(input);}catch(cause){
      if(!(cause instanceof ApiClientError)||cause.kind!=='http'||(cause.status??500)>=500){
        setDraftUncertain(true);
      } else {
        setSubmittedDraft(null);
        if(cause.status===409)void client.invalidateQueries({queryKey:salesKeys.detail(sale.id)});
      }
      throw cause;
    }finally{running.current=false;}
  }
  async function execute(value:Action) {
    if(running.current||unknownAction)return;
    running.current=true;setBusy(true);setError('');
    try {
      // Verify what the operator is actually approving. Another employee may
      // have changed a DRAFT or its amount since this card was opened.
      const fresh=await salesApi.get(sale.id);
      if(!sameSaleForAction(sale,fresh)){
        client.setQueryData(salesKeys.detail(sale.id),fresh);
        setError('Заказ изменён другим сотрудником. Данные и сумма обновлены; проверьте их и подтвердите операцию заново.');
        return;
      }
      // A 200 is not assumed until the backend returns an authoritative SaleResponse.
      const next=await (value==='confirm'?salesApi.confirm(sale.id):
        value==='fulfill'?salesApi.fulfill(sale.id):salesApi.cancel(sale.id));
      await changed(next);
      setNotice(value==='confirm'?'Заказ подтверждён.':
        value==='fulfill'?'Продажа выполнена.':'Заказ отменён.');
      setUnknownAction(null);
    }catch(cause){
      setError(userFacingApiError(cause));
      if(!(cause instanceof ApiClientError)||cause.kind!=='http'||(cause.status??500)>=500)
        setUnknownAction(value);
    }finally{running.current=false;setBusy(false);setAction(null);}
  }
  async function checkStatus() {
    setBusy(true);
    try {
      const result=await salesApi.get(sale.id);
      client.setQueryData(salesKeys.detail(sale.id),result);
      const expected=unknownAction==='confirm'?'CONFIRMED':
        unknownAction==='fulfill'?'FULFILLED':'CANCELLED';
      if(result.status===expected){
        setNotice('Операция подтверждена актуальным статусом сервера.');
        setError('');setUnknownAction(null);
      }else{
        setError('Операция пока не подтверждена. Не повторяйте действие без проверки.');
      }
      if(draftUncertain) {
        if(submittedDraft&&draftMatchesServer(submittedDraft,result)){
          setDraftUncertain(false);setEditing(false);setSubmittedDraft(null);
          setNotice('Проверено: сервер сохранил изменения черновика.');
          setError('');
        }else{
          // Keep the locally entered fields visible and locked. Without a
          // version token the user must explicitly discard or reconcile.
          setError('Сервер не подтвердил сохранение введённых изменений. Локальные поля сохранены; не отправляйте повторный PUT. При необходимости перепроверьте заказ и явно откажитесь от локальной версии.');
        }
      }
    }catch(cause){setError(userFacingApiError(cause));}
    finally{setBusy(false);}
  }
  return <div className="space-y-6">
    <Link to="/sales" className="inline-flex min-h-11 items-center font-semibold text-indigo-700">
      ← Все продажи</Link>
    <PageHeader title={'Продажа '+displaySaleNumber(sale.saleNumber)}/>
    {notice&&<p role="status" className="rounded-xl bg-emerald-50 p-3 text-sm text-emerald-800">{notice}</p>}
    {error&&<p role="alert" className="rounded-xl bg-red-50 p-3 text-sm text-red-800">{error}</p>}
    {(unknownAction||draftUncertain)&&<div className="space-y-3 rounded-xl bg-amber-50 p-4 text-sm text-amber-900">
      <p>Результат операции неизвестен. Возможна запись изменений на сервере.</p>
      <Button type="button" disabled={busy} onClick={()=>void checkStatus()}>
        Проверить актуальный статус</Button>
      {draftUncertain&&<Button type="button" variant="outline" disabled={busy}
        onClick={()=>{
          setDraftUncertain(false);setSubmittedDraft(null);setEditing(false);
          setError('Локальные изменения отброшены по вашему решению. Загружена версия сервера.');
          void client.invalidateQueries({queryKey:salesKeys.detail(sale.id)});
        }}>Отказаться от локальных правок и загрузить версию сервера</Button>}
    </div>}
    {editing&&canManageDraft&&<SaleForm key={sale.id+sale.items.length}
      blocked={draftUncertain}
      initial={sale} onSave={saveDraft} onCancel={()=>setEditing(false)}/>}
    {isDraft&&!sale.items.length&&<p className="text-sm text-amber-800">
      Для подтверждения добавьте хотя бы одну позицию.</p>}
    {isDraft&&!sale.fulfillmentType&&<p className="text-sm text-amber-800">
      Для подтверждения выберите способ получения.</p>}
    <div className="grid items-start gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(19rem,23rem)]">
      <div className="min-w-0 space-y-5">
    {requiresDelivery&&
      <section aria-label="Связанная доставка" className="space-y-2 rounded-xl border border-blue-200 bg-blue-50 p-4 text-sm">
        <h3 className="font-bold text-slate-900">Доставка заказа</h3>
        {!canReadDelivery?<p>Для просмотра доставки требуется разрешение DELIVERIES_READ.</p>:
          relatedDelivery.isPending?<p role="status">Проверяем связанную доставку…</p>:
          relatedDelivery.data?<div className="flex flex-wrap items-center justify-between gap-3">
            <p>Статус доставки: <strong>{deliveryStatusNames[relatedDelivery.data.status]}</strong></p>
            <Link className="font-semibold text-blue-700 underline-offset-2 hover:underline"
              to={'/deliveries/'+encodeURIComponent(relatedDelivery.data.id)}>Открыть доставку</Link>
          </div>:
          deliveryMissing?<div className="space-y-2">
            <p>Доставка ещё не оформлена.</p>
            {isConfirmed&&hasPermission('DELIVERIES_MANAGE')&&
              <Link className="font-semibold text-blue-700 underline-offset-2 hover:underline"
                to={'/deliveries/new?saleId='+encodeURIComponent(sale.id)}>Оформить доставку</Link>}
          </div>:
          <ErrorState title="Не удалось проверить связанную доставку"
            error={relatedDelivery.error} onRetry={()=>void relatedDelivery.refetch()}/>}
      </section>}
    <section aria-label="Карточка заказа" className="grid gap-4 rounded-2xl border border-slate-200 bg-white p-5 text-sm sm:grid-cols-2">
      <div><p className="text-slate-500">Статус</p><SaleStatusBadge status={sale.status}/></div>
      <div><p className="text-slate-500">Клиент</p>
        {sale.customerId && hasPermission('CUSTOMERS_READ') && customer.data
          ? <Link className="font-semibold text-blue-700 underline-offset-2 hover:underline focus-visible:underline" to={'/customers/'+encodeURIComponent(sale.customerId)}>{customerLabel}</Link>
          : <p className="font-semibold">{customerLabel}</p>}
      </div>
      <div><p className="text-slate-500">Создан</p><p className="font-semibold">{saleDate(sale.createdAt)}</p></div>
      <div><p className="text-slate-500">Способ получения</p><p className="font-semibold">
        {sale.fulfillmentType?fulfillmentNames[sale.fulfillmentType]:'Не указан'}</p></div>
      <div><p className="text-slate-500">Статус оплаты</p><p className="font-semibold">
        {paymentStatus}</p></div>
      <div><p className="text-slate-500">Статус доставки</p><p className="font-semibold">
        {delivery?deliveryStatusNames[delivery.status]:
          !canReadDelivery?'Нет доступа к доставке':
          requiresDelivery&&relatedDelivery.isPending?'Загружается…':
          requiresDelivery&&deliveryMissing?'Не оформлена':
          !requiresDelivery?'Не требуется':'Нет подтверждённых данных'}</p>
        {matched?.plannedDeliveryDate&&<p>План: {matched.plannedDeliveryDate}</p>}</div>
      <div><p className="text-slate-500">Выдан</p><p className="font-semibold">{saleDate(sale.fulfilledAt)}</p></div>
      <div><p className="text-slate-500">Отменён</p><p className="font-semibold">{saleDate(sale.cancelledAt)}</p></div>
      <div className="sm:col-span-2"><p className="text-slate-500">Комментарий</p>
        <p className="whitespace-pre-wrap">{sale.comment||'—'}</p></div>
    </section>
    <section aria-label="Товары заказа" className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
      <h3 className="p-4 text-lg font-bold">Товары</h3>
      {!sale.items.length?<p className="p-4 text-sm text-slate-500">Позиции пока не добавлены.</p>:
        sale.items.map(i=><ItemLine key={i.id} item={i}
          warehouses={hasPermission('INVENTORY_READ')?warehouseList.data??null:null}/>)}
      {hasPermission('INVENTORY_READ')&&
        <div className="px-4 pt-3"><Link to="/inventory"
          className="text-sm font-semibold text-blue-700 underline-offset-2 hover:underline">
          Открыть учёт остатков</Link></div>}
      <p className="p-4 text-right text-xl font-bold">Итого: {money(sale.total)}</p>
    </section>
    <section aria-label="События заказа" className="rounded-2xl border border-slate-200 bg-white p-5">
      <h3 className="text-lg font-bold">События заказа</h3>
      <p className="mt-1 text-xs text-slate-500">Только события с датами, подтверждёнными системой.
        Полная история изменений и обсуждение сотрудников пока не доступны.</p>
      <ol className="mt-4 space-y-4">
        {events.map(e=><li key={e.key} className="flex gap-3 text-sm">
          <span aria-hidden="true" className="mt-1 size-2 shrink-0 rounded-full bg-blue-600"/>
          <div><p className="font-semibold text-slate-900">{e.label}</p>
            <time dateTime={e.at} className="text-xs text-slate-500">{eventStamp(e.at)}</time></div>
        </li>)}
      </ol>
    </section>
      </div>
      <aside aria-label="Итог и действия по заказу" className="min-w-0 space-y-4 self-start">
        <section aria-label="Итог продажи" className="space-y-3 rounded-2xl border border-slate-200 bg-white p-5">
          <p className="text-sm font-semibold text-slate-600">Сумма заказа</p>
          <p className="text-2xl font-extrabold text-slate-950">{money(sale.total)}</p>
          <SaleStatusBadge status={sale.status}/>
          <p className="text-sm text-slate-600">Оплата: {paymentStatus}</p>
        </section>
        <section aria-label="Действия с заказом" className="space-y-3 rounded-2xl border border-slate-200 bg-white p-5">
          <h3 className="font-bold">Действия</h3>
    <div className="flex flex-col gap-2 [&_button]:w-full">
      {canManageDraft&&!unknownAction&&!draftUncertain&&
        <Button variant="outline" disabled={editing||busy} onClick={()=>setEditing(true)}>Редактировать черновик</Button>}
      {canConfirm&&!editing&&!unknownAction&&!draftUncertain&&
        <Button disabled={busy} onClick={()=>setAction('confirm')}>Подтвердить заказ</Button>}
      {canFulfill&&!unknownAction&&<Button disabled={busy}
        onClick={()=>setAction('fulfill')}>Завершить продажу</Button>}
      {canCancel&&!unknownAction&&<Button disabled={busy}
        variant="outline" onClick={()=>setAction('cancel')}>Отменить заказ</Button>}
    </div>

          {!canManageDraft&&!canFulfill&&!canCancel&&
            <p className="text-xs text-slate-500">Действия зависят от статуса заказа и ваших прав.</p>}
        </section>
        <SaleFinancialSection sale={sale}/>
      </aside>
    </div>
    <ConfirmDialog open={!!action} onOpenChange={open=>{if(!open)setAction(null);}}
      title={action==='confirm'?'Подтвердить заказ?':
        action==='fulfill'?'Завершить выдачу кресел?':'Отменить заказ?'}
      description={'Заказ '+sale.saleNumber+'. Клиент: '+customerLabel+'. '+sale.items.length+
        ' товарных позиций, общая сумма '+money(sale.total)+'. Способ получения: '+
        (sale.fulfillmentType?fulfillmentNames[sale.fulfillmentType]:'не указан')+'. '+
        (action==='confirm'?'Будет создан резерв складских остатков.':
        action==='fulfill'?'Будут списаны зарезервированные складские товары.':
        'Для подтверждённой продажи резерв будет освобождён, если сервер разрешит отмену.')}
      busy={busy} destructive={action==='cancel'}
      confirmLabel={action==='confirm'?'Подтвердить':
        action==='fulfill'?'Завершить продажу':'Отменить заказ'}
      onConfirm={()=>{if(action)void execute(action);}}/>
  </div>;
}
export function SaleDetailPage() {
  const {hasPermission}=useAuth();
  const {saleId=''}=useParams();
  const read=hasPermission('SALES_READ');
  const valid=uuidValid(saleId);
  const result=useQuery({
    queryKey:salesKeys.detail(saleId),queryFn:()=>salesApi.get(saleId),
    enabled:read&&valid,
  });
  if(!read)return <ForbiddenPage/>;
  if(!valid)return <NotFoundPage/>;
  if(result.isPending)return <LoadingState label="Загружаем заказ…"/>;
  if(result.isError) {
    if(result.error instanceof ApiClientError && result.error.status===403)return <ForbiddenPage/>;
    if(result.error instanceof ApiClientError && result.error.status===404)return <NotFoundPage/>;
    return <ErrorState error={result.error} onRetry={()=>void result.refetch()}/>;
  }
  return result.data?<SaleDetail key={saleId} sale={result.data}/>:<NotFoundPage/>;
}
