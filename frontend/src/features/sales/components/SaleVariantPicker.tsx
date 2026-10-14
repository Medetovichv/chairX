import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { catalogApi, catalogKeys } from '../../catalog/api';
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
  const [modelPage,setModelPage]=useState(0);
  const [modelId,setModelId]=useState('');
  const [variantPage,setVariantPage]=useState(0);
  const models=useQuery({queryKey:catalogKeys.products(modelPage),
    queryFn:()=>catalogApi.listProducts(modelPage),enabled:open&&allowed});
  const variants=useQuery({queryKey:catalogKeys.variants(modelId,variantPage),
    queryFn:()=>catalogApi.listVariants(modelId,variantPage),
    enabled:open&&allowed&&!!modelId});
  const current=useQuery({queryKey:['catalog','variant',value],
    queryFn:()=>catalogApi.getVariant(value),enabled:allowed&&!!value});
  const stock=useQuery({
    queryKey:inventoryKeys.overview({warehouseId:null,model:'',variation:'',search:searchTerm,
      includeZero:false,onlyAvailable:true,page:searchPage,size:20}),
    queryFn:()=>inventoryApi.overview({warehouseId:null,model:'',variation:'',search:searchTerm,
      includeZero:false,onlyAvailable:true,page:searchPage,size:20}),
    enabled:open&&allowed&&canReadInventory&&searchTerm.length>=2,
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
      <Button type="button" variant="outline" disabled={disabled}
        onClick={()=>setOpen(x=>!x)}>{value?'Изменить модель':'Выбрать модель'}</Button>}
    {open&&allowed&&<section aria-label="Каталог для продажи"
      className="space-y-3 rounded-xl border border-slate-200 bg-white p-3">
      {canReadInventory&&<section aria-label="Быстрый поиск кресел" className="space-y-2">
        <label className="block space-y-1 text-sm font-semibold">Быстрый поиск по модели, цвету или SKU
          <input type="search" aria-label="Поиск по креслам в наличии"
            value={stockSearch} disabled={disabled||picking} maxLength={200}
            onChange={event=>{setStockSearch(event.target.value);setSearchPage(0);}}
            placeholder="Например, Ergo, чёрный, ERGO-BLK"
            className="min-h-11 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm"/>
        </label>
        {searchTerm.length>=2&&(stock.isPending?<LoadingState label="Ищем кресла в наличии…"/>:
          stock.isError?<ErrorState error={stock.error} onRetry={()=>void stock.refetch()}/>:
          <div className="space-y-2">
            {stock.data?.items.length===0&&<p className="text-sm text-slate-600">
              Кресла с доступным остатком не найдены. Попробуйте другой запрос.</p>}
            {stock.data?.items.map(line=>{
              const best=line.warehouses.filter(w=>w.available>0)
                .sort((a,b)=>b.available-a.available)[0];
              return <div key={line.productVariantId}
                className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-slate-200 bg-white p-3 text-sm">
                <div>
                  <p className="font-semibold">{line.model} · {line.variation}</p>
                  <p className="text-xs text-slate-500">Доступно на всех складах: {line.available} шт.</p>
                </div>
                <Button type="button" size="sm" variant="outline" disabled={disabled||picking}
                  onClick={()=>void chooseAvailable(line.productVariantId,best?.warehouseId)}>
                  Добавить {line.model} — {line.variation}
                </Button>
              </div>;
            })}
            {!!stock.data&&stock.data.total>stock.data.size&&
              <PurchasePager page={stock.data.page} size={stock.data.size} total={stock.data.total}
                label="доступных вариаций" onPage={setSearchPage}/>}
          </div>)}
      </section>}
      {selectionError&&<p role="alert" className="text-sm text-red-700">{selectionError}</p>}

      {models.isPending?<LoadingState label="Модели…"/>:
        models.isError?<ErrorState error={models.error} onRetry={()=>void models.refetch()}/>:
        <div className="space-y-2">
          <label className="block space-y-1 text-sm font-semibold">Модель
            <select aria-label="Модель кресла" value={modelId}
              onChange={e=>{setModelId(e.target.value);setVariantPage(0);}}
              className="min-h-11 w-full rounded-xl border border-slate-200 px-3">
              <option value="">Выберите модель</option>
              {models.data?.items.filter(p=>p.active).map(p=><option key={p.id} value={p.id}>{p.name}</option>)}
            </select>
          </label>
          <PurchasePager page={models.data?.page??modelPage} size={20} label="моделей"
            total={models.data?.totalElements??0}
            onPage={p=>{setModelPage(p);setModelId('');setVariantPage(0);}}/>
        </div>}
      {modelId&&<div className="space-y-2">
        <p className="font-semibold">Выберите вариацию</p>
        {variants.isPending?<LoadingState label="Вариации…"/>:
          variants.isError?<ErrorState error={variants.error} onRetry={()=>void variants.refetch()}/>:
          <>
            <div className="flex flex-wrap gap-2">{variants.data?.items.filter(v=>v.active).map(v=>
              <Button type="button" key={v.id} variant="outline" disabled={disabled}
                onClick={()=>{onSelect(v);setOpen(false);}}>
                {v.name}{v.sku?' · '+v.sku:''}
              </Button>)}</div>
            <PurchasePager page={variants.data?.page??variantPage} size={20} label="вариаций"
              total={variants.data?.totalElements??0} onPage={setVariantPage}/>
          </>}
      </div>}
    </section>}
  </div>;
}
