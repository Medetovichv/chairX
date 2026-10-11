import { useState } from 'react';
import { Link, useLocation, useParams } from 'react-router';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { inventoryApi, inventoryKeys } from '../../inventory/api';
import { salesKeys } from '../../sales/api';
import { ApiClientError } from '../../../shared/api/api-error';
import { PageHeader } from '../../../shared/components/PageHeader';
import { ErrorState } from '../../../shared/components/ErrorState';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ForbiddenPage } from '../../../pages/ForbiddenPage';
import { NotFoundPage } from '../../../pages/NotFoundPage';
import { Button } from '../../../shared/ui/button';
import { deliveryApi, deliveryKeys } from '../api';
import { dateLabel, label, som, validUUID } from '../validation';
import { DeliveryStatusBadge } from '../components/DeliveryStatusBadge';
import { DeliveryTimeline } from '../components/DeliveryTimeline';
import { DeliveryActions } from '../components/DeliveryActions';
import type { DeliveryResponse, DeliverySummary } from '../types';

function Copy({text,name}:{text:string;name:string}){
  const [state,setState]=useState('');
  async function copy(){
    try{await navigator.clipboard.writeText(text);setState('Скопировано');}
    catch{setState('Копирование недоступно. Выделите текст вручную.');}
  }
  return <div className="flex flex-wrap items-center gap-2">
    <span className="break-all">{text}</span>
    <Button type="button" variant="outline" size="sm" onClick={()=>void copy()}>
      Копировать {name}</Button>
    {state&&<span role="status" className="text-xs text-slate-600">{state}</span>}
  </div>;
}
function Detail({d}:{d:DeliveryResponse}){
  const {hasPermission}=useAuth();
  const queryClient=useQueryClient();
  const location=useLocation();
  const state=location.state as {summary?:DeliverySummary;notice?:string}|null;
  const info=state?.summary?.id===d.id?state.summary:null;
  const [notice]=useState(state?.notice??'');
  const warehouses=useQuery({queryKey:inventoryKeys.warehouses,
    queryFn:inventoryApi.warehouses,
    enabled:hasPermission('INVENTORY_READ')&&!!d.returnedToWarehouseAt&&!!d.returnWarehouseId});
  const returnWarehouseName=warehouses.data?.find(w=>w.id===d.returnWarehouseId)?.name;
  async function updated(result:DeliveryResponse){
    queryClient.setQueryData(deliveryKeys.detail(d.id),result);
    queryClient.setQueryData(deliveryKeys.bySale(d.saleId),result);
    await Promise.all([
      queryClient.invalidateQueries({queryKey:deliveryKeys.lists}),
      queryClient.invalidateQueries({queryKey:salesKeys.lists}),
      queryClient.invalidateQueries({queryKey:salesKeys.kanban}),
      queryClient.invalidateQueries({queryKey:salesKeys.detail(d.saleId)}),
      queryClient.invalidateQueries({queryKey:inventoryKeys.overviewPrefix}),
      queryClient.invalidateQueries({queryKey:inventoryKeys.balancePrefix}),
      queryClient.invalidateQueries({queryKey:inventoryKeys.movementsPrefix}),
    ]);
  }
  return <div className="space-y-5">
    <Link className="inline-flex min-h-11 items-center font-semibold text-indigo-700"
      to="/deliveries">← Все доставки</Link>
    <PageHeader title={'Доставка '+(info?.saleNumber||'')}
      description={'Заказ: '+(info?.saleNumber||'номер доступен в списке')}/>
    {notice&&<p role="status" className="rounded-xl bg-emerald-50 p-3 text-sm text-emerald-800">{notice}</p>}
    <div className="flex flex-wrap items-center gap-3"><DeliveryStatusBadge status={d.status}/>
      {hasPermission('SALES_READ')&&<Link className="font-semibold text-indigo-700 underline"
        to={'/sales/'+encodeURIComponent(d.saleId)}>Открыть связанную продажу</Link>}
    </div>
    <section aria-label="Контакты доставки"
      className="grid gap-4 rounded-2xl border border-slate-200 bg-white p-5 sm:grid-cols-2">
      <div><p className="text-sm text-slate-500">Получатель</p>
        <p className="font-semibold">{label(d.recipientName)}</p></div>
      <div><p className="text-sm text-slate-500">Телефон</p>
        <Copy text={d.recipientPhone} name="телефон"/></div>
      <div><p className="text-sm text-slate-500">Город или регион</p>
        <p className="font-semibold">{label(d.cityRegion)}</p></div>
      <div><p className="text-sm text-slate-500">Адрес</p>
        <Copy text={d.address} name="адрес"/></div>
      <div><p className="text-sm text-slate-500">Плановая дата</p>
        <p className="font-semibold">{dateLabel(d.plannedDeliveryDate)}</p></div>
      <div><p className="text-sm text-slate-500">Стоимость доставки</p>
        <p className="font-semibold">{som(d.deliveryCost)}</p>
        <p className="text-xs text-slate-500">Стоимость не равна полученной оплате.</p></div>
      <div><p className="text-sm text-slate-500">Перевозчик</p><p>{label(d.carrierName)}</p></div>
      <div><p className="text-sm text-slate-500">Трек-номер</p>
        <p className="break-all">{label(d.trackingNumber)}</p></div>
      <div><p className="text-sm text-slate-500">Создана</p><p>{dateLabel(d.createdAt)}</p></div>
      <div><p className="text-sm text-slate-500">Отправлена</p><p>{dateLabel(d.dispatchedAt)}</p></div>
      <div><p className="text-sm text-slate-500">Вручена</p><p>{dateLabel(d.deliveredAt)}</p></div>
      <div><p className="text-sm text-slate-500">Комментарий</p>
        <p className="whitespace-pre-wrap">{label(d.comment)}</p></div>
      {d.status==='FAILED'&&<div className="sm:col-span-2 rounded-xl bg-red-50 p-3">
        <p className="font-semibold">Причина неуспешной доставки</p>
        <p className="whitespace-pre-wrap">{label(d.failureReason)}</p>
        <p className="mt-1 text-xs">FAILED не означает физический возврат на склад.</p>
      </div>}
      {d.returnedToWarehouseAt&&<div className="sm:col-span-2 rounded-xl bg-emerald-50 p-3">
        <p className="font-semibold">Товар возвращён на склад</p>
        <p>Дата: {dateLabel(d.returnedToWarehouseAt)}</p>
        <p>Склад: {returnWarehouseName??'Название склада недоступно'}</p>
        <p>Оформил: {label(d.returnedToWarehouseBy)}</p>
        <p className="text-xs">Статус доставки остаётся FAILED; продажа после dispatch остаётся FULFILLED.</p>
      </div>}
    </section>
    <DeliveryActions key={d.id} delivery={d} onChanged={updated}/>
    <DeliveryTimeline delivery={d}/>
  </div>;
}
export function DeliveryDetailPage(){
  const {hasPermission}=useAuth();
  const read=hasPermission('DELIVERIES_READ');
  const {deliveryId=''}=useParams();
  const q=useQuery({queryKey:deliveryKeys.detail(deliveryId),queryFn:()=>deliveryApi.get(deliveryId),
    enabled:read&&validUUID(deliveryId)});
  if(!read)return <ForbiddenPage/>;
  if(!validUUID(deliveryId))return <NotFoundPage/>;
  if(q.isPending)return <LoadingState label="Загружаем доставку…"/>;
  if(q.isError){
    if(q.error instanceof ApiClientError&&q.error.status===403)return <ForbiddenPage/>;
    if(q.error instanceof ApiClientError&&q.error.status===404)return <NotFoundPage/>;
    return <ErrorState error={q.error} onRetry={()=>void q.refetch()}/>;
  }
  return q.data?<Detail d={q.data}/>:<NotFoundPage/>;
}
