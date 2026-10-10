import { useRef, useState, type FormEvent } from 'react';
import { useAuth } from '../../auth/AuthProvider';
import { userFacingApiError } from '../../../shared/api/api-error';
import { Input } from '../../../shared/ui/input';
import { Button } from '../../../shared/ui/button';
import { CustomerPicker } from './CustomerPicker';
import { SaleItemsEditor, nextKey, type SaleEditorRow } from './SaleItemsEditor';
import { checkDraft, countValid, fulfillmentNames, salePriceValid } from '../validation';
import type { DraftInput, FulfillmentType, SaleResponse } from '../types';

export function SaleForm({initial,defaultCustomerId=null,onSave,onCancel}:{
  initial?:SaleResponse;defaultCustomerId?:string|null;
  onSave:(input:DraftInput)=>Promise<void>;onCancel?:()=>void;
}) {
  const {hasPermission}=useAuth();
  const [customerId,setCustomerId]=useState<string|null>(initial?.customerId??defaultCustomerId);
  const [type,setType]=useState<FulfillmentType|null>(initial?.fulfillmentType??null);
  const [comment,setComment]=useState(initial?.comment??'');
  const [rows,setRows]=useState<SaleEditorRow[]>(()=>{
    return initial?.items.map(i=>({
      key:nextKey(),productVariantId:i.productVariantId,warehouseId:i.warehouseId,
      quantity:String(i.quantity),unitSalePrice:String(i.unitSalePrice),recommended:null,
    }))??[];
  });
  const [error,setError]=useState('');
  const [saving,setSaving]=useState(false);
  const inFlight=useRef(false);
  async function submit(event:FormEvent) {
    event.preventDefault();if(inFlight.current)return;
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
    const data:DraftInput={customerId,fulfillmentType:type,items,comment:comment.trim()||null};
    const problem=checkDraft(data);
    if(problem){setError(problem);return;}
    inFlight.current=true;setSaving(true);setError('');
    try{await onSave(data);}catch(cause){setError(userFacingApiError(cause));}
    finally{inFlight.current=false;setSaving(false);}
  }
  return <form aria-label={initial?'Редактирование черновика':'Новый черновик продажи'}
    className="space-y-5 rounded-2xl border border-slate-200 bg-white p-4 sm:p-6"
    onSubmit={e=>void submit(e)}>
    <CustomerPicker value={customerId} onChange={setCustomerId} disabled={saving}/>
    <label className="block space-y-2 text-sm font-semibold">Способ получения
      <select aria-label="Способ получения" disabled={saving} value={type??''}
        onChange={e=>setType(e.target.value?e.target.value as FulfillmentType:null)}
        className="min-h-11 w-full rounded-xl border border-slate-200 bg-white px-3">
        <option value="">Уточним позже</option>
        {(Object.keys(fulfillmentNames) as FulfillmentType[]).map(k=>
          <option value={k} key={k}>{fulfillmentNames[k]}</option>)}
      </select>
    </label>
    <SaleItemsEditor rows={rows} onChange={setRows} disabled={saving}/>
    {(!hasPermission('CATALOG_READ')||!hasPermission('INVENTORY_READ'))&&
      <p className="rounded-xl bg-amber-50 p-3 text-sm text-amber-800">
        Для добавления новых товарных позиций требуются CATALOG_READ и INVENTORY_READ.
        Можно сохранить черновик без позиций и заполнить позже.
      </p>}
    <label className="block space-y-2 text-sm font-semibold">Комментарий
      <textarea aria-label="Комментарий продажи" maxLength={2000} rows={3}
        value={comment} disabled={saving} onChange={e=>setComment(e.target.value)}
        className="w-full rounded-xl border border-slate-200 bg-white p-3"/>
    </label>
    <p className="text-sm text-slate-600">
      Черновик допускает отсутствие клиента, товаров и способа получения.
      Резервирование и проверка доступности происходят только при подтверждении заказа.
    </p>
    {error&&<p role="alert" className="rounded-xl bg-red-50 p-3 text-sm text-red-800">{error}</p>}
    <div className="flex flex-wrap gap-2">
      <Button type="submit" disabled={saving}>{saving?'Сохраняем…':'Сохранить черновик'}</Button>
      {onCancel&&<Button type="button" variant="outline" disabled={saving} onClick={onCancel}>Отмена</Button>}
    </div>
  </form>;
}
