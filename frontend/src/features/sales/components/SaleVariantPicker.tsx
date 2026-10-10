import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { catalogApi, catalogKeys } from '../../catalog/api';
import { useAuth } from '../../auth/AuthProvider';
import { Button } from '../../../shared/ui/button';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState';
import { PurchasePager } from '../../purchases/components/PurchasePager';
import { money } from '../validation';
import type { ProductVariant } from '../../catalog/types';

export function SaleVariantPicker({value,onSelect,disabled=false}:{
  value:string;onSelect:(variant:ProductVariant)=>void;disabled?:boolean;
}) {
  const {hasPermission}=useAuth();
  const allowed=hasPermission('CATALOG_READ');
  const [open,setOpen]=useState(false);
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
  return <div className="space-y-2">
    {value&&<p className="break-all text-xs text-slate-600">
      Вариация: {current.data?.name||value}
      {current.data?.sku&&' · '+current.data.sku}
      {current.data&&' · Рекомендовано: '+money(current.data.recommendedSalePrice)}
    </p>}
    {!allowed?<p className="text-sm text-amber-800">
      Для выбора вариации требуется CATALOG_READ.</p>:
      <Button type="button" variant="outline" disabled={disabled}
        onClick={()=>setOpen(x=>!x)}>{value?'Сменить вариацию':'Выбрать вариацию'}</Button>}
    {open&&allowed&&<section aria-label="Каталог для продажи"
      className="space-y-3 rounded-xl border border-slate-200 bg-white p-3">
      <p className="text-xs text-slate-500">Каталог постраничный, без вымышленного глобального поиска.</p>
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
        <p className="font-semibold">Вариации</p>
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
