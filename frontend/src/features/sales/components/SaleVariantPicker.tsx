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

export function SaleVariantPicker({value,onSelect,disabled=false,allowOutOfStock=false}:{
  value:string;onSelect:(variant:ProductVariant,warehouseId?:string)=>void;disabled?:boolean;allowOutOfStock?:boolean;
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
    queryFn:()=>catalogApi.listProducts(modelPage),enabled:open&&allowed&&allowOutOfStock});
  const variants=useQuery({queryKey:catalogKeys.variants(modelId,variantPage),
    queryFn:()=>catalogApi.listVariants(modelId,variantPage),
    enabled:open&&allowed&&allowOutOfStock&&!!modelId});
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
      if(!warehouseId){setSelectionError('Нет склада с доступным остатком.');return;}
      // Inventory overview can be stale while multiple cashiers reserve stock.
      // Re-read the selected warehouse before committing the selected item.
      const balance=await inventoryApi.balance(warehouseId,id);
      if(balance.available<=0){
        setSelectionError('Товар уже закончился на выбранном складе. Обновите список.');
        await stock.refetch();
        return;
      }
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
    {value&&<p className="break-words rounded-lg bg-slate-100 px-3 py-2 text-xs text-slate-600">
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

      {allowOutOfStock&&<div aria-label="Каталог для учёта брака">      {models.isPending?<LoadingState label="Модели…"/>:
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
</div>}
      {!canReadInventory&&!allowOutOfStock&&<p role="alert" className="rounded-lg bg-amber-50 p-3 text-sm text-amber-900">
        Для выбора товара по наличию требуется право INVENTORY_READ. Обратитесь к администратору.
      </p>}
      {canReadInventory&&<section aria-label="Быстрый поиск кресел" className="space-y-2">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h4 className="text-sm font-semibold text-slate-900">Товары в наличии</h4>
          <span className="rounded-full bg-emerald-50 px-3 py-1 text-xs font-medium text-emerald-700">Только доступные</span>
        </div>
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
              return <button key={line.productVariantId} type="button"
                disabled={disabled||picking}
                onClick={()=>void chooseAvailable(line.productVariantId,best!.warehouseId)}
                className="flex w-full items-center justify-between gap-3 rounded-xl border border-slate-200 bg-white p-3 text-left text-sm transition-colors hover:border-blue-400 hover:bg-blue-50 focus-visible:outline-2 focus-visible:outline-blue-600 disabled:opacity-50">
                <span className="min-w-0">
                  <strong className="block break-words text-slate-900">{line.model}</strong>
                  <span className="mt-0.5 block text-xs text-slate-500">{line.variation}</span>
                </span>
                <span className="flex shrink-0 items-center gap-2">
                  <span className="rounded-lg bg-slate-100 px-2 py-1 text-xs font-semibold text-slate-700">{line.available} шт.</span>
                  <span className="font-semibold text-blue-700" aria-hidden="true">Выбрать</span>
                </span>
              </button>;
            })}
            {!!stock.data&&stock.data.total>stock.data.size&&
              <div className="flex flex-wrap items-center justify-between gap-2 border-t border-slate-100 pt-3">
                <Button type="button" size="sm" variant="outline" disabled={disabled||picking||searchPage===0||stock.isFetching}
                  onClick={()=>setSearchPage(page=>Math.max(0,page-1))}>Назад к товарам</Button>
                <Button type="button" size="sm" variant="outline" disabled={disabled||picking||stock.isFetching||
                    (searchPage+1)*stock.data.size>=stock.data.total}
                  onClick={()=>setSearchPage(page=>page+1)}>Показать другие товары</Button>
              </div>}
          </div>)}
      </section>}
    </section>}
  </div>;
}
