import { useRef, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { catalogApi } from '../../catalog/api';
import { customersApi } from '../../customers/api';
import { ApiClientError, userFacingApiError } from '../../../shared/api/api-error';
import { PageHeader } from '../../../shared/components/PageHeader';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState';
import { ForbiddenPage } from '../../../pages/ForbiddenPage';
import { NotFoundPage } from '../../../pages/NotFoundPage';
import { ConfirmDialog } from '../../../shared/ui/confirm-dialog';
import { Button } from '../../../shared/ui/button';
import { salesApi, salesKeys } from '../api';
import { SaleStatusBadge } from '../components/SaleStatusBadge';
import { SaleForm } from '../components/SaleForm';
import { inventoryKeys } from '../../inventory/api';
import { fulfillmentNames, integerSom, money, saleDate, uuidValid } from '../validation';
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

function ItemLine({item}: {item:SaleResponse['items'][number]}) {
  const {hasPermission}=useAuth();
  const variant=useQuery({
    queryKey:['catalog','variant',item.productVariantId],
    queryFn:()=>catalogApi.getVariant(item.productVariantId),
    enabled:hasPermission('CATALOG_READ'),
  });
  return <article className="grid gap-2 border-b border-slate-100 p-4 text-sm sm:grid-cols-[1fr_auto]">
    <div>
      <p className="font-semibold">{variant.data?.name??'Вариация '+item.productVariantId}</p>
      <p className="break-all text-xs text-slate-500">Вариация: {item.productVariantId}</p>
      <p className="break-all text-xs text-slate-500">Склад: {item.warehouseId}</p>
    </div>
    <div className="sm:text-right">
      <p>{item.quantity} шт. × {money(item.unitSalePrice)}</p>
      <p className="font-bold">{money(item.total)}</p>
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
  const customer=useQuery({
    queryKey:['customers','customer',sale.customerId],
    queryFn:()=>customersApi.get(sale.customerId!),
    enabled:hasPermission('CUSTOMERS_READ')&&!!sale.customerId,
  });
  const customerLabel=customer.data?.fullName||customer.data?.phone||
    customer.data?.whatsappPhone||sale.customerId||'Без клиента';
  async function changed(result:SaleResponse) {
    client.setQueryData(salesKeys.detail(sale.id),result);
    await Promise.all([
      client.invalidateQueries({queryKey:salesKeys.lists}),
      client.invalidateQueries({queryKey:salesKeys.kanban}),
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
    <PageHeader title={'Продажа '+sale.saleNumber} description={'UUID: '+sale.id}/>
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
    <div className="flex flex-wrap gap-2">
      {canManageDraft&&!unknownAction&&!draftUncertain&&
        <Button variant="outline" disabled={editing||busy} onClick={()=>setEditing(true)}>Редактировать черновик</Button>}
      {canConfirm&&!editing&&!unknownAction&&!draftUncertain&&
        <Button disabled={busy} onClick={()=>setAction('confirm')}>Подтвердить заказ</Button>}
      {canFulfill&&!unknownAction&&<Button disabled={busy}
        onClick={()=>setAction('fulfill')}>Завершить продажу</Button>}
      {canCancel&&!unknownAction&&<Button disabled={busy}
        variant="outline" onClick={()=>setAction('cancel')}>Отменить заказ</Button>}
    </div>
    {editing&&canManageDraft&&<SaleForm key={sale.id+sale.items.length}
      blocked={draftUncertain}
      initial={sale} onSave={saveDraft} onCancel={()=>setEditing(false)}/>}
    {isDraft&&!sale.items.length&&<p className="text-sm text-amber-800">
      Для подтверждения добавьте хотя бы одну позицию.</p>}
    {isDraft&&!sale.fulfillmentType&&<p className="text-sm text-amber-800">
      Для подтверждения выберите способ получения.</p>}
    {isConfirmed&&sale.fulfillmentType!=='SELF_PICKUP'&&
      <div className="space-y-2 rounded-xl bg-indigo-50 p-3 text-sm text-indigo-900">
        <p>Для этого заказа требуется процесс доставки. Выдача и отмена через /api/sales недоступны.</p>
        {hasPermission('DELIVERIES_READ')&&
          (matched?.deliveryStatus ?
            <Link className="font-semibold text-indigo-700 underline" to="/deliveries">
              Доставка уже отмечена в списке продаж — открыть список доставок</Link> :
            hasPermission('DELIVERIES_MANAGE')?
              <Link className="font-semibold text-indigo-700 underline"
                to={'/deliveries/new?saleId='+encodeURIComponent(sale.id)}>
                Оформить доставку</Link>:
              <Link className="font-semibold text-indigo-700 underline" to="/deliveries">Все доставки</Link>)}
      </div>}
    <section aria-label="Карточка заказа" className="grid gap-4 rounded-2xl border border-slate-200 bg-white p-5 text-sm sm:grid-cols-2">
      <div><p className="text-slate-500">Статус</p><SaleStatusBadge status={sale.status}/></div>
      <div><p className="text-slate-500">Клиент</p><p className="font-semibold break-all">{customerLabel}</p></div>
      <div><p className="text-slate-500">Создан</p><p className="font-semibold">{saleDate(sale.createdAt)}</p></div>
      <div><p className="text-slate-500">Способ получения</p><p className="font-semibold">
        {sale.fulfillmentType?fulfillmentNames[sale.fulfillmentType]:'Не указан'}</p></div>
      <div><p className="text-slate-500">Статус оплаты</p><p className="font-semibold">
        {matched?.paymentStatus||'Статус недоступен в SaleResponse'}</p></div>
      <div><p className="text-slate-500">Статус доставки</p><p className="font-semibold">
        {matched?.deliveryStatus||'Статус недоступен в SaleResponse'}</p>
        {matched?.plannedDeliveryDate&&<p>План: {matched.plannedDeliveryDate}</p>}</div>
      <div><p className="text-slate-500">Выдан</p><p className="font-semibold">{saleDate(sale.fulfilledAt)}</p></div>
      <div><p className="text-slate-500">Отменён</p><p className="font-semibold">{saleDate(sale.cancelledAt)}</p></div>
      <div className="sm:col-span-2"><p className="text-slate-500">Комментарий</p>
        <p className="whitespace-pre-wrap">{sale.comment||'—'}</p></div>
    </section>
    <section aria-label="Товары заказа" className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
      <h3 className="p-4 text-lg font-bold">Товары</h3>
      {!sale.items.length?<p className="p-4 text-sm text-slate-500">Позиции пока не добавлены.</p>:
        sale.items.map(i=><ItemLine key={i.id} item={i}/>)}
      <p className="p-4 text-right text-xl font-bold">Итого: {money(sale.total)}</p>
    </section>
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
