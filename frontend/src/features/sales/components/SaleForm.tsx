import { useRef, useState, type FormEvent } from 'react';
import { useAuth } from '../../auth/AuthProvider';
import { userFacingApiError } from '../../../shared/api/api-error';
import { Button } from '../../../shared/ui/button';
import { CustomerPicker } from './CustomerPicker';
import { SaleItemsEditor, nextKey, type SaleEditorRow } from './SaleItemsEditor';
import { checkDraft, countValid, fulfillmentNames, integerSom, money, salePriceValid } from '../validation';
import { bishkekToday } from '../../finance/validation';
import type { DraftInput, FulfillmentType, SaleResponse } from '../types';

export function SaleForm({initial,defaultCustomerId=null,onSave,onCancel,blocked=false}:{
  initial?:SaleResponse;defaultCustomerId?:string|null;
  onSave:(input:DraftInput)=>Promise<void>;onCancel?:()=>void;blocked?:boolean;
}) {
  const {hasPermission}=useAuth();
  const [customerId,setCustomerId]=useState<string|null>(initial?.customerId??defaultCustomerId);
  const [type,setType]=useState<FulfillmentType|null>(initial?.fulfillmentType??null);
  const [comment,setComment]=useState(initial?.comment??'');
  const [rows,setRows]=useState<SaleEditorRow[]>(()=>{
    return initial?.items.map(i=>({
      key:nextKey(),productVariantId:i.productVariantId,warehouseId:i.warehouseId,
      quantity:String(i.quantity),
      unitSalePrice:integerSom(i.unitSalePrice)??'Некорректная исходная цена — проверьте',
      recommended:null,
    }))??[];
  });
  const canInlineDelivery=!initial&&hasPermission('SALES_DRAFT_MANAGE')&&(hasPermission('DELIVERIES_CREATE')||hasPermission('DELIVERIES_MANAGE'));
  const withDelivery=!initial&&type!==null&&type!=='SELF_PICKUP'&&canInlineDelivery;
  const [recipientName,setRecipientName]=useState('');
  const [recipientPhone,setRecipientPhone]=useState('');
  const [deliveryAddress,setDeliveryAddress]=useState('');
  const [deliveryCity,setDeliveryCity]=useState('');
  const [deliveryCost,setDeliveryCost]=useState('0');
  const [plannedDeliveryDate,setPlannedDeliveryDate]=useState(bishkekToday);
  const [error,setError]=useState('');
  const [saving,setSaving]=useState(false);
  const inFlight=useRef(false);
  async function submit(event:FormEvent) {
    event.preventDefault();if(inFlight.current||blocked)return;
    const items=[] as DraftInput['items'];
    for(const row of rows){
      if (!row.productVariantId || !row.warehouseId || !countValid(row.quantity) ||
          !salePriceValid(row.unitSalePrice)) {
        setError('Заполните все поля добавленной позиции: вариацию, склад, количество и целую цену в сомах.');
        return;
      }
      items.push({productVariantId:row.productVariantId,warehouseId:row.warehouseId,
        quantity:Number(row.quantity),unitSalePrice:row.unitSalePrice.trim()});
    }
    if(withDelivery&&type){
      if(!recipientPhone.trim()||!deliveryAddress.trim()||!/^\d+$/.test(deliveryCost.trim())||
         !items.length){
        setError('Для немедленного оформления доставки укажите телефон, адрес, стоимость в целых сомах и хотя бы одно кресло.');return;
      }
    }
    const data:DraftInput={customerId,fulfillmentType:type,items,comment:comment.trim()||null,
      ...(withDelivery&&type?{deliveryDetails:{
        recipientName:recipientName.trim()||null,recipientPhone:recipientPhone.trim(),
        address:deliveryAddress.trim(),cityRegion:deliveryCity.trim()||null,
        deliveryCost:deliveryCost.trim(),carrierName:null,trackingNumber:null,
        comment:null,plannedDeliveryDate,
      }}:{})};
    const problem=checkDraft(data);
    if(problem){setError(problem);return;}
    inFlight.current=true;setSaving(true);setError('');
    try{await onSave(data);}catch(cause){setError(userFacingApiError(cause));}
    finally{inFlight.current=false;setSaving(false);}
  }
  const validPrices=rows.every(row=>countValid(row.quantity)&&salePriceValid(row.unitSalePrice));
  const total=validPrices?rows.reduce((sum,row)=>
    sum+BigInt(row.quantity)*BigInt(row.unitSalePrice),0n):null;
  const quantity=rows.reduce((sum,row)=>sum+(countValid(row.quantity)?Number(row.quantity):0),0);

  return <form aria-label={initial?'Редактирование черновика':'Новый черновик продажи'}
    className="space-y-5" onSubmit={e=>void submit(e)}>
    <div className="flex flex-wrap gap-x-4 gap-y-2 rounded-2xl border border-slate-200 bg-white px-5 py-3 text-xs font-semibold text-slate-600 [.quick-create_&]:hidden">
      <span>1. Клиент</span><span aria-hidden="true">→</span>
      <span>2. Товары</span><span aria-hidden="true">→</span>
      <span>3. Получение</span><span aria-hidden="true">→</span>
      <span>4. Проверка</span>
    </div>
    <div className="grid min-w-0 items-start gap-5 xl:grid-cols-[minmax(0,1fr)_minmax(17rem,20rem)] [.quick-create_&]:!grid-cols-1">
      <div className="min-w-0 space-y-5">
        <section aria-label="Этап 1. Клиент" className="min-w-0 space-y-4 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm sm:p-6 [.quick-create_&]:p-4">
          <h3 className="text-lg font-bold text-slate-950">1. Клиент</h3>
          <CustomerPicker value={customerId} onChange={setCustomerId} disabled={saving||blocked}/>
        </section>
        <section aria-label="Этап 2. Товары" className="min-w-0 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm sm:p-6 [.quick-create_&]:p-4">
          <h3 className="mb-4 text-lg font-bold text-slate-950">2. Выбор кресел</h3>
          <SaleItemsEditor rows={rows} onChange={setRows} disabled={saving||blocked}/>
          {(!hasPermission('CATALOG_READ')||!hasPermission('INVENTORY_READ'))&&
            <p className="mt-3 rounded-xl bg-amber-50 p-3 text-sm text-amber-800">
              Для добавления новых товарных позиций нужны CATALOG_READ и INVENTORY_READ.
              Черновик можно сохранить без позиций и заполнить позже.
            </p>}
        </section>
        <section aria-label="Этап 3. Получение" className="space-y-4 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm sm:p-6">
          <h3 className="text-lg font-bold text-slate-950">3. Получение заказа</h3>
          <label className="block space-y-2 text-sm font-semibold">Способ получения
            <select aria-label="Способ получения" disabled={saving||blocked} value={type??''}
              onChange={e=>setType(e.target.value?e.target.value as FulfillmentType:null)}
              className="min-h-11 w-full rounded-xl border border-slate-200 bg-white px-3">
              <option value="">Уточним позже</option>
              {(Object.keys(fulfillmentNames) as FulfillmentType[]).map(k=>
                <option value={k} key={k}>{fulfillmentNames[k]}</option>)}
            </select>
          </label>
          {!initial&&type&&type!=='SELF_PICKUP'&&<div className="space-y-3 rounded-xl border border-blue-100 bg-blue-50 p-4">
            {canInlineDelivery?<p className="text-sm font-semibold text-blue-950">Доставка по заказу — заполните адрес и контакты. При сохранении продажа и доставка создаются вместе.</p>:<p className="text-sm text-amber-900">Для создания доставки необходимы разрешения подтверждения продажи и оформления доставки. Обратитесь к администратору.</p>}
            {canInlineDelivery&&withDelivery&&<div className="grid gap-3 sm:grid-cols-2 [.quick-create_&]:grid-cols-1">
              <label className="space-y-1 text-sm font-semibold">Получатель
                <input aria-label="Получатель при создании продажи" maxLength={200} value={recipientName}
                  disabled={saving||blocked} onChange={e=>setRecipientName(e.target.value)}
                  className="min-h-11 w-full rounded-xl border border-slate-200 bg-white px-3"/></label>
              <label className="space-y-1 text-sm font-semibold">Телефон *
                <input aria-label="Телефон доставки при создании продажи" maxLength={50} value={recipientPhone}
                  disabled={saving||blocked} onChange={e=>setRecipientPhone(e.target.value)}
                  className="min-h-11 w-full rounded-xl border border-slate-200 bg-white px-3"/></label>
              <label className="space-y-1 text-sm font-semibold sm:col-span-2">Адрес *
                <input aria-label="Адрес доставки при создании продажи" maxLength={500} value={deliveryAddress}
                  disabled={saving||blocked} onChange={e=>setDeliveryAddress(e.target.value)}
                  className="min-h-11 w-full rounded-xl border border-slate-200 bg-white px-3"/></label>
              <label className="space-y-1 text-sm font-semibold">Город / регион
                <input aria-label="Город доставки при создании продажи" maxLength={200} value={deliveryCity}
                  disabled={saving||blocked} onChange={e=>setDeliveryCity(e.target.value)}
                  className="min-h-11 w-full rounded-xl border border-slate-200 bg-white px-3"/></label>
              <label className="space-y-1 text-sm font-semibold">Планируемая дата доставки
                <input aria-label="Планируемая дата доставки при создании продажи"
                  type="date" value={plannedDeliveryDate} disabled={saving||blocked}
                  onChange={e=>setPlannedDeliveryDate(e.target.value)}
                  className="min-h-11 w-full rounded-xl border border-slate-200 bg-white px-3"/></label>
              <label className="space-y-1 text-sm font-semibold">Стоимость доставки, сом
                <input aria-label="Стоимость доставки при создании продажи" inputMode="numeric" maxLength={17} value={deliveryCost}
                  disabled={saving||blocked} onChange={e=>setDeliveryCost(e.target.value)}
                  className="min-h-11 w-full rounded-xl border border-slate-200 bg-white px-3"/></label>
              <p className="text-xs text-slate-600 sm:col-span-2">Стоимость доставки сохраняется отдельно в карточке доставки и не прибавляется автоматически к сумме кресел или оплате. Плановая дата по умолчанию сегодня по Бишкеку; при необходимости выберите другую дату.</p>
            </div>}
          </div>}
          <label className="block space-y-2 text-sm font-semibold">Комментарий
            <textarea aria-label="Комментарий продажи" maxLength={2000} rows={3}
              value={comment} disabled={saving||blocked} onChange={e=>setComment(e.target.value)}
              className="w-full rounded-xl border border-slate-200 bg-white p-3"/>
          </label>
        </section>
      </div>
      <aside aria-label="Этап 4. Итог заказа" className="sticky bottom-0 z-10 space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm [.quick-create_&]:bottom-0 [.quick-create_&]:border-t-2 [.quick-create_&]:shadow-lg">
        <h3 className="text-lg font-bold text-slate-950">4. Итог заказа</h3>
        <dl className="space-y-3 text-sm">
          <div className="flex justify-between gap-3"><dt className="text-slate-500">Клиент</dt>
            <dd className="font-semibold">{customerId?'Выбран':'Не выбран'}</dd></div>
          <div className="flex justify-between gap-3"><dt className="text-slate-500">Позиций</dt>
            <dd className="font-semibold">{rows.length}</dd></div>
          <div className="flex justify-between gap-3"><dt className="text-slate-500">Всего кресел</dt>
            <dd className="font-semibold">{quantity} шт.</dd></div>
          <div className="flex justify-between gap-3"><dt className="text-slate-500">Получение</dt>
            <dd className="text-right font-semibold">{type?fulfillmentNames[type]:'Не указано'}</dd></div>
          <div className="flex justify-between gap-3 border-t border-slate-200 pt-3 text-base">
            <dt className="font-bold">Предварительный итог</dt>
            <dd className="font-extrabold text-blue-700">{total===null?'Проверьте цены':money(total.toString())}</dd>
          </div>
        </dl>
        <p className="text-xs leading-relaxed text-slate-500">
          Сохранение черновика не резервирует кресла. Остатки проверяются при подтверждении заказа.
        </p>
        {blocked&&<p role="alert" className="rounded-xl bg-amber-50 p-3 text-sm text-amber-900">
          Данные формы сохранены на экране, но отправка заблокирована:
          результат предыдущего PUT неизвестен. Проверьте состояние сервера.
        </p>}
        {error&&<p role="alert" className="rounded-xl bg-red-50 p-3 text-sm text-red-800">{error}</p>}
        <Button className="w-full" type="submit" disabled={saving||blocked}>
          {saving?'Сохраняем…':withDelivery?'Подтвердить продажу и доставку':'Сохранить черновик'}
        </Button>
        {onCancel&&<Button className="w-full" type="button" variant="outline" disabled={saving||blocked}
          onClick={onCancel}>Отмена</Button>}
      </aside>
    </div>
  </form>;
}
