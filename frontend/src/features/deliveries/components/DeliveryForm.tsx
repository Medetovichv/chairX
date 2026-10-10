import { useState, type FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { salesApi, salesKeys } from '../../sales/api';
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
    queryKey:[...salesKeys.lists,'delivery-picker','CONFIRMED',page],
    queryFn:()=>salesApi.list({status:'CONFIRMED',from:'',to:'',number:'',page}),
    enabled:canReadSales&&!disabled,
  });
  const sale=useQuery({
    queryKey:salesKeys.detail(saleId),
    queryFn:()=>salesApi.get(saleId),
    enabled:canReadSales&&validUUID(saleId),
  });
  // With SALES_READ, do not permit POST until actual sale eligibility is proven.
  // Without SALES_READ we cannot call the endpoint; backend remains authority.
  const eligibility=canReadSales?
    validUUID(saleId)&&sale.isSuccess&&sale.data.status==='CONFIRMED'&&
    (sale.data.fulfillmentType==='CITY_DELIVERY'||sale.data.fulfillmentType==='REGION_DELIVERY'):true;
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
    if(error||!eligibility){setProblem(error||'Доставка допустима только для подтверждённой продажи с доставкой.');return;}
    setProblem('');await onSubmit(payload);
  }
  return <form aria-label="Оформить доставку" className="space-y-5 rounded-2xl border border-slate-200 bg-white p-4 sm:p-6"
    onSubmit={e=>void submit(e)}>
    <section className="space-y-2">
      <h3 className="font-semibold">Подтверждённый заказ с доставкой</h3>
      <label className="block space-y-1 text-sm font-semibold">UUID заказа *
        <Input aria-label="UUID заказа" value={saleId} disabled={disabled} autoComplete="off"
          onChange={e=>setSaleId(e.target.value)} placeholder="UUID из F07"/>
      </label>
      {!canReadSales&&<p className="text-sm text-amber-800">
        SALES_READ не назначен. Введите реальный UUID заказа: backend проверит статус и тип.
        Список продаж не запрашивается.
      </p>}
      {canReadSales&&<section aria-label="Выбор заказа" className="space-y-2 rounded-xl bg-slate-50 p-3">
        {list.isPending?<LoadingState label="Подтверждённые продажи…"/>:
          list.isError?<ErrorState error={list.error} onRetry={()=>void list.refetch()}/>:
          <>
            <div className="space-y-2">{list.data?.items.filter(s=>
              s.fulfillmentType==='CITY_DELIVERY'||s.fulfillmentType==='REGION_DELIVERY').map(s=>
              <Button key={s.id} type="button" variant="outline" className="w-full justify-start"
                disabled={disabled} onClick={()=>setSaleId(s.id)}>
                {s.saleNumber} · {s.customerName||'Без имени'} · {s.fulfillmentType==='REGION_DELIVERY'?'Регион':'Город'}
              </Button>)}</div>
            <PurchasePager page={list.data?.page??page} size={list.data?.size??20}
              total={list.data?.total??0} label="подтверждённых продаж"
              onPage={setPage}/>
            <p className="text-xs text-slate-600">Список постраничный; продажи с самовывозом скрыты в текущей странице.</p>
          </>}
        {validUUID(saleId)&&sale.isPending&&<LoadingState label="Проверяем заказ…"/>}
        {validUUID(saleId)&&sale.isError&&<ErrorState error={sale.error} onRetry={()=>void sale.refetch()}/>}
        {validUUID(saleId)&&sale.data&&<p className="text-sm">
          {sale.data.saleNumber} · {sale.data.status} · {sale.data.fulfillmentType||'Тип не задан'}
          {!eligibility&&<strong className="block text-red-700">Для этого заказа оформление доставки запрещено.</strong>}
        </p>}
      </section>}
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
    <Button type="submit" disabled={disabled||!eligibility||!saleId.trim()}>Создать доставку</Button>
  </form>;
}
