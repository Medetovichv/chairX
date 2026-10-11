import { useQuery, useQueries } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { inventoryApi, inventoryKeys } from '../../inventory/api';
import { Button } from '../../../shared/ui/button';
import { Input } from '../../../shared/ui/input';
import { SaleVariantPicker } from './SaleVariantPicker';
import { integerSom, money } from '../validation';
import type { Money } from '../types';
import type { Warehouse } from '../../inventory/types';

export interface SaleEditorRow {
  key: number; productVariantId: string; warehouseId: string;
  quantity: string; unitSalePrice: string; recommended: Money | null;
}
export function SaleItemsEditor({rows,onChange,disabled}:{
  rows:SaleEditorRow[];onChange:(rows:SaleEditorRow[])=>void;disabled:boolean;
}) {
  const {hasPermission}=useAuth();
  const inventoryRead=hasPermission('INVENTORY_READ');
  const warehouses=useQuery({
    queryKey:inventoryKeys.warehouses,queryFn:inventoryApi.warehouses,enabled:inventoryRead,
  });
  function patch(key:number,update:Partial<SaleEditorRow>) {
    onChange(rows.map(r=>r.key===key?{...r,...update}:r));
  }
  return <section aria-label="Товары продажи" className="space-y-3">
    <h3 className="text-lg font-semibold">Кресла и вариации</h3>
    <p className="text-sm text-slate-600">
      Позиции сохраняются внутри одного заказа. Склад и цена указаны для каждой вариации.
      Рекомендуемая цена не применяется автоматически.
    </p>
    {!inventoryRead&&<p className="rounded-xl bg-amber-50 p-3 text-sm text-amber-800">
      Нет INVENTORY_READ: нельзя выбрать склад или проверить остаток для новых строк.
      При редактировании существующих строк выбранный склад сохраняется.
    </p>}
    {warehouses.isError&&inventoryRead&&<p role="alert" className="text-sm text-red-700">
      Не удалось загрузить список складов.
      <Button type="button" variant="outline" size="sm" onClick={()=>void warehouses.refetch()}>Повторить</Button>
    </p>}
    {rows.map((row,index)=><div key={row.key} className="space-y-3 rounded-xl border border-slate-200 bg-slate-50 p-4">
      <div className="flex items-center justify-between gap-2">
        <p className="font-bold">Позиция {index+1}</p>
        <Button type="button" variant="outline" size="sm" disabled={disabled}
          onClick={()=>onChange(rows.filter(r=>r.key!==row.key))}>Удалить позицию</Button>
      </div>
      <SaleVariantPicker value={row.productVariantId} disabled={disabled} onSelect={v=>patch(row.key,{
        productVariantId:v.id,recommended:v.recommendedSalePrice,
      })}/>
      <div className="grid gap-3 sm:grid-cols-3">
        <label className="space-y-1 text-sm font-semibold">Склад *
          <select aria-label={'Склад позиции '+(index+1)} value={row.warehouseId}
            disabled={disabled||!inventoryRead||warehouses.isPending||warehouses.isError}
            onChange={e=>patch(row.key,{warehouseId:e.target.value})}
            className="min-h-11 w-full rounded-xl border border-slate-200 bg-white px-3">
            <option value="">Выберите склад</option>
            {!!row.warehouseId&&!warehouses.data?.some(w=>w.id===row.warehouseId)&&
              <option value={row.warehouseId}>Ранее выбранный склад недоступен</option>}
            {warehouses.data?.filter(w=>w.active||w.id===row.warehouseId).map(w=>
              <option key={w.id} value={w.id}>{w.name}{w.active?'':' (неактивен)'}</option>)}
          </select>
        </label>
        <label className="space-y-1 text-sm font-semibold">Количество *
          <Input aria-label={'Количество позиции '+(index+1)} inputMode="numeric"
            value={row.quantity} disabled={disabled}
            onChange={e=>patch(row.key,{quantity:e.target.value})}/>
        </label>
        <label className="space-y-1 text-sm font-semibold">Цена продажи, сом *
          <Input aria-label={'Цена продажи позиции '+(index+1)} inputMode="numeric"
            value={row.unitSalePrice} disabled={disabled}
            onChange={e=>patch(row.key,{unitSalePrice:e.target.value})}/>
        </label>
      </div>
      {row.recommended!==null&&<div className="flex flex-wrap items-center gap-3 text-xs text-slate-600">
        <span>Рекомендованная цена: {money(row.recommended)}</span>
        <Button type="button" size="sm" variant="outline" disabled={disabled||integerSom(row.recommended)===null}
          onClick={()=>patch(row.key,{unitSalePrice:integerSom(row.recommended!)??''})}>
          Использовать рекомендованную
        </Button>
      </div>}
      <SaleAvailability warehouseId={row.warehouseId} variantId={row.productVariantId}
        enabled={inventoryRead} warehouses={warehouses.data??[]} quantity={row.quantity}
        onSwitch={id=>patch(row.key,{warehouseId:id})}/>
    </div>)}
    <Button type="button" variant="outline" disabled={disabled||rows.length>=1000}
      onClick={()=>onChange([...rows,{
        key:nextKey(),productVariantId:'',warehouseId:'',quantity:'1',unitSalePrice:'',recommended:null,
      }])}>+ Добавить кресло</Button>
  </section>;
}
let keySequence=0;
export function nextKey(){return ++keySequence;}

function SaleAvailability({warehouseId,variantId,enabled,warehouses,quantity,onSwitch}:{
  warehouseId:string;variantId:string;enabled:boolean;warehouses:Warehouse[];
  quantity:string;onSwitch:(warehouseId:string)=>void;
}) {
  const q=useQuery({
    queryKey:inventoryKeys.balance(warehouseId,variantId),
    queryFn:()=>inventoryApi.balance(warehouseId,variantId),
    enabled:enabled&&!!warehouseId&&!!variantId,
  });
  const alternatives=warehouses.filter(w=>w.active&&w.id!==warehouseId);
  const stocks=useQueries({queries:alternatives.map(w=>({
    queryKey:inventoryKeys.balance(w.id,variantId),
    queryFn:()=>inventoryApi.balance(w.id,variantId),
    enabled:enabled&&!!warehouseId&&!!variantId,
    staleTime:15_000,
  }))});
  if(!enabled||!warehouseId||!variantId)return null;
  if(q.isPending)return <p role="status" className="text-xs text-slate-500">Проверяем доступный остаток…</p>;
  if(q.isError)return <p role="alert" className="text-xs text-amber-800">
    Остаток не загружен: подтвердит backend.</p>;
  const requested=Number(quantity);
  const shortage=Number.isSafeInteger(requested)&&requested>0&&requested>q.data.available;
  const availableAlternatives=alternatives.filter((_,index)=>
    stocks[index]?.isSuccess&&(stocks[index]?.data?.available??0)>=requested);
  return <div className="space-y-2 text-xs text-slate-600">
    <p>Склад: {q.data.onHand} · Резерв: {q.data.reserved} · Доступно:
      <strong> {q.data.available} шт.</strong> Черновик не резервирует товар.</p>
    {shortage&&<div className="space-y-2 rounded-xl border border-amber-200 bg-amber-50 p-3 text-amber-900">
      <p role="alert" className="font-semibold">
        На выбранном складе недостаточно товара: нужно {requested}, доступно {q.data.available}.
      </p>
      {stocks.some(s=>s.isPending)&&<p>Проверяем другие склады…</p>}
      {availableAlternatives.map(w=>{
        const remaining=stocks[alternatives.indexOf(w)]?.data?.available??0;
        return <Button key={w.id} type="button" variant="outline" size="sm"
          onClick={()=>onSwitch(w.id)}>
          Использовать {w.name} · {remaining} шт.
        </Button>;
      })}
      {!availableAlternatives.length&&!stocks.some(s=>s.isPending)&&
        <p>Подходящий другой склад не найден. Измените количество или товар.</p>}
    </div>}
  </div>;
}
