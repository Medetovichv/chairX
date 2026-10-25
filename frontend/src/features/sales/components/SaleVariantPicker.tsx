import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { catalogApi } from '../../catalog/api';
import { inventoryApi, inventoryKeys } from '../../inventory/api';
import { useAuth } from '../../auth/AuthProvider';
import { Button } from '../../../shared/ui/button';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState';
import { PurchasePager } from '../../purchases/components/PurchasePager';
import { money } from '../validation';
import type { ProductVariant } from '../../catalog/types';

export function SaleVariantPicker({value,onSelect,disabled=false}:{
  value:string;onSelect:(variant:ProductVariant,warehouseId?:string)=>void;disabled?:boolean;
}) {
  const {hasPermission}=useAuth();
  const allowed=hasPermission('CATALOG_READ');
  const canReadInventory=hasPermission('INVENTORY_READ');
  const [open,setOpen]=useState(!value);
  const [stockSearch,setStockSearch]=useState('');
  const [searchTerm,setSearchTerm]=useState('');
  const [searchPage,setSearchPage]=useState(0);
  const [picking,setPicking]=useState(false);
  const [selectionError,setSelectionError]=useState('');
  useEffect(()=>{
    const timer=setTimeout(()=>setSearchTerm(stockSearch.trim()),300);
    return ()=>clearTimeout(timer);
  },[stockSearch]);
  const current=useQuery({queryKey:['catalog','variant',value],
    queryFn:()=>catalogApi.getVariant(value),enabled:allowed&&!!value});
  const stock=useQuery({
    queryKey:inventoryKeys.overview({warehouseId:null,model:'',variation:'',search:searchTerm,
      includeZero:false,onlyAvailable:true,page:searchPage,size:20}),
    queryFn:()=>inventoryApi.overview({warehouseId:null,model:'',variation:'',search:searchTerm,
      includeZero:false,onlyAvailable:true,page:searchPage,size:20}),
    enabled:open&&allowed&&canReadInventory,
  });
  async function chooseAvailable(id:string,warehouseId:string|undefined){
    if(picking)return;
    setSelectionError('');
    setPicking(true);
    try {
      const variant=await catalogApi.getVariant(id);
      if(!variant.active) {
        setSelectionError('Этот вариант товара неактивен. Выберите другой.');
        return;
      }
      onSelect(variant,warehouseId);
      setOpen(false);
    } catch {
      setSelectionError('Не удалось получить сведения о варианте. Попробуйте ещё раз.');
    } finally {setPicking(false);}
  }
  return <div className="space-y-2">
    {value&&<p className="break-all text-xs text-slate-600">
      Вариация: {current.data?.name||value}
      {current.data?.sku&&' · '+current.data.sku}
      {current.data&&' · Рекомендовано: '+money(current.data.recommendedSalePrice)}
    </p>}
    {!allowed?<p className="text-sm text-amber-800">
      Для выбора вариации требуется CATALOG_READ.</p>:
      value?<Button type="button" variant="outline" disabled={disabled}
        onClick={()=>setOpen(x=>!x)}>Изменить модель</Button>:null}
    {open&&allowed&&<section aria-label="Каталог для продажи"
      className="space-y-3 rounded-xl border border-slate-200 bg-white p-3">
      {selectionError&&<p role="alert" className="text-sm text-red-700">{selectionError}</p>}

      {!canReadInventory&&<p role="alert" className="rounded-lg bg-amber-50 p-3 text-sm text-amber-900">
        Для выбора товара по наличию требуется право INVENTORY_READ. Обратитесь к администратору.
      </p>}
      {canReadInventory&&<section aria-label="Быстрый поиск кресел" className="space-y-2">
        <h4 className="text-sm font-medium text-blue-700">Поиск по наличию</h4>
        <label className="block space-y-1 text-sm font-semibold">Быстрый поиск по модели, цвету или SKU
          <input type="search" aria-label="Поиск по креслам в наличии"
            value={stockSearch} disabled={disabled||picking} maxLength={200}
            onChange={event=>{setStockSearch(event.target.value);setSearchPage(0);}}
            placeholder="Например, Ergo, чёрный, ERGO-BLK"
            className="min-h-11 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm"/>
        </label>
        {(stock.isPending?<LoadingState label="Ищем кресла в наличии…"/>:
          stock.isError?<ErrorState error={stock.error} onRetry={()=>void stock.refetch()}/>:
          <div className="space-y-2">
            {stock.data?.items.length===0&&<p className="text-sm text-slate-600">
              Кресла с доступным остатком не найдены. Попробуйте другой запрос.</p>}
            {stock.data?.items.filter(line=>line.available>0&&line.warehouses.some(w=>w.available>0)).map(line=>{
              const best=line.warehouses.filter(w=>w.available>0)
                .sort((a,b)=>b.available-a.available)[0];
              return <div key={line.productVariantId}
                className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-slate-200 bg-white p-3 text-sm">
                <div>
                  <p className="font-semibold">{line.model} · {line.variation}</p>
                  <p className="text-xs text-slate-500">Доступно на всех складах: {line.available} шт.</p>
                </div>
                <Button type="button" size="sm" variant="outline" disabled={disabled||picking}
                  onClick={()=>void chooseAvailable(line.productVariantId,best!.warehouseId)}>
                  Добавить {line.model} — {line.variation}
                </Button>
              </div>;
            })}
            {!!stock.data&&stock.data.total>stock.data.size&&
              <PurchasePager page={stock.data.page} size={stock.data.size} total={stock.data.total}
                label="доступных вариаций" onPage={setSearchPage}/>}
          </div>)}
      </section>}
    </section>}
  </div>;
}
