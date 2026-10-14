import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { salesApi, salesKeys } from '../../sales/api';
import { deliveryApi, deliveryKeys } from '../api';
import { ApiClientError } from '../../../shared/api/api-error';
import { displaySaleNumber } from '../../../shared/lib/format';
import { PurchasePager } from '../../purchases/components/PurchasePager';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState';
import { Button } from '../../../shared/ui/button';
import { Input } from '../../../shared/ui/input';
import { validateCreate, validUUID, optional } from '../validation';
import type { CreateDeliveryInput } from '../types';

export function DeliveryForm({initialSaleId='',onSubmit,disabled=false}:{
  initialSaleId?:string;onSubmit:(payload:CreateDeliveryInput)=>Promise<void>|void;disabled?:boolean;
}){
  const {hasPermission}=useAuth();
  const canReadSales=hasPermission('SALES_READ');
  const [saleId,setSaleId]=useState(initialSaleId);
  const [page,setPage]=useState(0);
  const [search,setSearch]=useState('');
  const [term,setTerm]=useState('');
  useEffect(()=>{
    const timeout=setTimeout(()=>setTerm(search.trim().replace(/^#/,'')),300);
    return ()=>clearTimeout(timeout);
  },[search]);
  const [recipientName,setRecipientName]=useState('');
  const [recipientPhone,setRecipientPhone]=useState('');
  const [address,setAddress]=useState('');
  const [cityRegion,setCityRegion]=useState('');
  const [deliveryCost,setDeliveryCost]=useState('');
  const [carrierName,setCarrierName]=useState('');
  const [trackingNumber,setTrackingNumber]=useState('');
  const [plannedDeliveryDate,setPlannedDeliveryDate]=useState('');
  const [comment,setComment]=useState('');
  const [problem,setProblem]=useState('');
  const list=useQuery({
    queryKey:[...salesKeys.lists,'delivery-picker','CONFIRMED',term,page],
    queryFn:()=>salesApi.list({status:'CONFIRMED',from:'',to:'',number:term,page}),
    enabled:canReadSales&&!disabled&&!saleId,
  });
  const sale=useQuery({
    queryKey:salesKeys.detail(saleId),
    queryFn:()=>salesApi.get(saleId),
    enabled:canReadSales&&validUUID(saleId),
  });
  const existing=useQuery({
    queryKey:deliveryKeys.bySale(saleId),
    queryFn:()=>Promise.race([
      deliveryApi.bySale(saleId),
      new Promise<never>((_,reject)=>window.setTimeout(
        ()=>reject(new Error('Проверка доставки не завершилась за 10 секунд. Повторите запрос.')),10000)),
    ]),
    enabled:validUUID(saleId)&&hasPermission('DELIVERIES_READ'),
    retry:false,
  });
  const deliveryAbsent=existing.isError&&existing.error instanceof ApiClientError&&
    existing.error.status===404;
  // With SALES_READ validate the sale type/status locally; without it only a
  // preselected sale can be submitted, after independent duplicate detection.
  const eligibility=validUUID(saleId)&&(canReadSales?
    sale.isSuccess&&sale.data.status==='CONFIRMED'&&
      (sale.data.fulfillmentType==='CITY_DELIVERY'||sale.data.fulfillmentType==='REGION_DELIVERY'):true);
  const canCreate=eligibility&&deliveryAbsent;
  async function submit(event:FormEvent){
    event.preventDefault();
    if(disabled)return;
    const payload:CreateDeliveryInput={
      saleId:saleId.trim(),recipientName:optional(recipientName),
      recipientPhone:recipientPhone.trim(),address:address.trim(),cityRegion:optional(cityRegion),
      deliveryCost:deliveryCost.trim(),carrierName:optional(carrierName),
      trackingNumber:optional(trackingNumber),comment:optional(comment),
      plannedDeliveryDate:plannedDeliveryDate||null,
    };
    const error=validateCreate(payload);
    if(error||!canCreate){setProblem(error||'Проверьте заказ и отсутствие существующей доставки.');return;}
    setProblem('');await onSubmit(payload);
  }
  return <form aria-label="Оформить доставку" className="space-y-5 rounded-2xl border border-slate-200 bg-white p-4 sm:p-6"
    onSubmit={e=>void submit(e)}>
    <section className="space-y-3">
      <h3 className="font-semibold">Подтверждённый заказ с доставкой</h3>
      {!saleId&&!canReadSales&&<p className="rounded-xl bg-amber-50 p-3 text-sm text-amber-900">
        Чтобы выбрать заказ, нужен доступ к продажам. Откройте оформление из карточки заказа
        или обратитесь к администратору. Технический идентификатор вводить не требуется.
      </p>}
      {saleId&&<div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-blue-200 bg-blue-50 p-3 text-sm">
        <div>
          <p className="font-semibold">Выбранный заказ</p>
          {canReadSales&&sale.data
            ? <Link to={'/sales/'+encodeURIComponent(saleId)}
                className="font-bold text-blue-700 underline-offset-2 hover:underline">
                {displaySaleNumber(sale.data.saleNumber)}</Link>
            : <p>Заказ выбран для оформления доставки</p>}
        </div>
        {canReadSales&&<Button type="button" variant="outline" size="sm" disabled={disabled}
          onClick={()=>{setSaleId('');setProblem('');}}>Выбрать другой</Button>}
      </div>}
      {canReadSales&&!saleId&&<section aria-label="Выбор заказа" className="space-y-3 rounded-xl border border-slate-200 bg-slate-50 p-3">
        <label className="block space-y-1 text-sm font-semibold">Найти заказ по номеру
          <Input type="search" aria-label="Поиск заказа для доставки" value={search}
            disabled={disabled} maxLength={100} placeholder="Например, #1258"
            onChange={event=>{setSearch(event.target.value);setPage(0);}}/>
        </label>
        {list.isPending?<LoadingState label="Подтверждённые продажи…"/>:
          list.isError?<ErrorState error={list.error} onRetry={()=>void list.refetch()}/>:
          <>
            <div className="space-y-2">{list.data?.items.filter(s=>
              s.fulfillmentType==='CITY_DELIVERY'||s.fulfillmentType==='REGION_DELIVERY').map(s=>
              <Button key={s.id} type="button" variant="outline" className="w-full justify-start"
                disabled={disabled} onClick={()=>{setSaleId(s.id);
                  if(s.customerPhone)setRecipientPhone(s.customerPhone);
                  if(s.deliveryAddress)setAddress(s.deliveryAddress);}}>
                <span className="min-w-0 text-left">
                  <strong>{displaySaleNumber(s.saleNumber)}</strong> · {s.customerPhone||s.customerName||'Без контакта'}
                  <span className="block truncate text-xs text-slate-600">
                    {s.products||'Товары не указаны'} · {s.quantity} шт. · {s.deliveryAddress||'Адрес уточняется'}
                  </span>
                </span>
              </Button>)}</div>
            <PurchasePager page={list.data?.page??page} size={list.data?.size??20}
              total={list.data?.total??0} label="подтверждённых заказов" onPage={setPage}/>
            <p className="text-xs text-slate-600">Список постраничный; самовывоз скрыт из выбора.
              Если нужного заказа нет, уточните номер или переключите страницу.</p>
          </>}
      </section>}
      {saleId&&canReadSales&&sale.isPending&&<LoadingState label="Проверяем заказ…"/>}
      {saleId&&canReadSales&&sale.isError&&
        <ErrorState error={sale.error} onRetry={()=>void sale.refetch()}/>}
      {saleId&&canReadSales&&sale.data&&!eligibility&&
        <p role="alert" className="text-sm font-semibold text-red-700">
          Для этого заказа оформление доставки запрещено.</p>}
      {saleId&&existing.isPending&&<p role="status" className="text-sm text-slate-500">
        Проверяем, не создана ли уже доставка…</p>}
      {existing.data&&<p className="text-sm text-amber-900">
        Для этой продажи доставка уже существует. <Link
          className="font-semibold text-blue-700 underline-offset-2 hover:underline"
          to={'/deliveries/'+encodeURIComponent(existing.data.id)}>Открыть доставку</Link>
      </p>}
      {saleId&&existing.isError&&!deliveryAbsent&&
        <ErrorState title="Не удалось проверить существующую доставку"
          error={existing.error} onRetry={()=>void existing.refetch()}/>}
    </section>
    <div className="grid gap-4 sm:grid-cols-2">
      {([
        ['Имя получателя','recipientName',recipientName,setRecipientName,200],
        ['Телефон *','recipientPhone',recipientPhone,setRecipientPhone,50],
        ['Адрес *','address',address,setAddress,500],
        ['Город / регион','cityRegion',cityRegion,setCityRegion,200],
        ['Стоимость доставки, сом *','deliveryCost',deliveryCost,setDeliveryCost,19],
        ['Перевозчик','carrierName',carrierName,setCarrierName,200],
        ['Трек-номер','trackingNumber',trackingNumber,setTrackingNumber,200],
      ] as const).map(([title,id,value,update,max])=>
        <label key={id} className={'space-y-1 text-sm font-semibold '+(id==='address'?'sm:col-span-2':'')}>
          {title}
          <Input aria-label={title} value={value} maxLength={max} disabled={disabled}
            inputMode={id==='deliveryCost'?'numeric':undefined}
            onChange={e=>update(e.target.value)} />
        </label>)}
      <label className="space-y-1 text-sm font-semibold">Плановая дата
        <Input type="date" aria-label="Плановая дата" disabled={disabled} value={plannedDeliveryDate}
          onChange={e=>setPlannedDeliveryDate(e.target.value)}/>
      </label>
      <label className="space-y-1 text-sm font-semibold sm:col-span-2">Комментарий
        <textarea aria-label="Комментарий доставки" rows={3} maxLength={2000}
          disabled={disabled} value={comment} onChange={e=>setComment(e.target.value)}
          className="w-full rounded-xl border border-slate-200 p-3"/>
      </label>
    </div>
    {problem&&<p role="alert" className="rounded-xl bg-red-50 p-3 text-sm text-red-800">{problem}</p>}
    <p className="text-sm text-slate-600">
      Стоимость — целые сомы, это не регистрация оплаты. Доставка создаётся в статусе READY.
      Дублирование заказа блокирует backend.
    </p>
    <Button type="submit" disabled={disabled||!canCreate}>Создать доставку</Button>
  </form>;
}
