import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { inventoryApi, inventoryKeys } from '../../inventory/api';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState';
import { Button } from '../../../shared/ui/button';
import { PurchasePager } from './PurchasePager';
import { purchaseApi, purchaseKeys } from '../api';
import { formatMoney } from '../validation';
import type { Purchase } from '../types';

function postedAt(value:string):string {
  const date=new Date(value);
  if(Number.isNaN(date.getTime()))return 'Дата недоступна';
  return new Intl.DateTimeFormat('ru-RU',{timeZone:'Asia/Bishkek',
    day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit'}).format(date);
}

/**
 * Purchase-read-only history: receipt cost details are intentionally never used
 * by warehouse-only receiving pages, which have their own cost-free DTO.
 */
export function PurchaseReceiptsHistory({purchase}:{purchase:Purchase}){
  const {hasPermission}=useAuth();
  const allowed=hasPermission('PURCHASE_READ');
  const canSeeWarehouses=hasPermission('INVENTORY_READ');
  const [page,setPage]=useState(0);
  const [chosen,setChosen]=useState<string|null>(null);
  const receipts=useQuery({
    queryKey:purchaseKeys.receiptList(purchase.id,page),
    queryFn:()=>purchaseApi.receiptList(purchase.id,page),
    enabled:allowed,
  });
  const active=useQuery({
    queryKey:purchaseKeys.receiptDetail(purchase.id,chosen??''),
    queryFn:()=>purchaseApi.receiptDetail(purchase.id,chosen!),
    enabled:allowed&&!!chosen,
  });
  const warehouses=useQuery({
    queryKey:inventoryKeys.warehouses,queryFn:inventoryApi.warehouses,
    enabled:allowed&&canSeeWarehouses,
  });
  if(!allowed)return null;
  const warehouseLabel=(id:string)=>{
    if(!canSeeWarehouses)return 'Склад (подробности недоступны)';
    return warehouses.data?.find(w=>w.id===id)?.name??'Название склада недоступно';
  };
  return <section aria-label="История поступлений"
    className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5">
    <div className="flex flex-wrap items-center justify-between gap-3">
      <h3 className="text-lg font-bold text-slate-950">История поступлений</h3>
      <p className="text-xs text-slate-500">Фактически проведённые операции</p>
    </div>
    {receipts.isPending?<LoadingState label="Загружаем поступления закупки…"/>:
      receipts.isError?<ErrorState title="Не удалось загрузить историю поступлений"
        error={receipts.error} onRetry={()=>void receipts.refetch()}/>:
      <>
        {!receipts.data?.items.length&&<p className="text-sm text-slate-500">
          По этой закупке поступлений пока не было.</p>}
        <ol className="space-y-3">
          {receipts.data?.items.map(item=><li key={item.id}
            className="rounded-xl border border-slate-200 bg-slate-50 p-4 text-sm">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="space-y-1">
                <p className="font-bold text-slate-900">Поступление · {postedAt(item.postedAt)}</p>
                <p className="text-xs text-slate-600">{warehouseLabel(item.warehouseId)}
                  {' · '}Принял: {item.createdBy}</p>
              </div>
              <Button type="button" variant="outline" size="sm"
                onClick={()=>setChosen(prev=>prev===item.id?null:item.id)}>
                {chosen===item.id?'Свернуть поступление':'Подробнее о поступлении'}
              </Button>
            </div>
            {chosen===item.id&&<div className="mt-3 space-y-3 border-t border-slate-200 pt-3">
              {active.isPending?<LoadingState label="Загружаем позиции поступления…"/>:
                active.isError?<ErrorState title="Не удалось открыть поступление"
                  error={active.error} onRetry={()=>void active.refetch()}/>:
                <>
                  <ul className="space-y-2">
                    {active.data?.items.map(line=>{
                      const purchaseLine=purchase.items.find(x=>x.id===line.purchaseItemId);
                      return <li key={line.id} className="rounded-lg bg-white p-3">
                        <p className="font-semibold">{purchaseLine?.modelName??'Кресло'}
                          {purchaseLine?.variantName?' · '+purchaseLine.variantName:''}</p>
                        <p>Принято: {line.quantity} шт.</p>
                        <p className="text-xs text-slate-600">Стоимость поступивших товаров:
                          {' '}{formatMoney(line.totalCost)} · Карго: {formatMoney(line.allocatedCargoCost)}</p>
                      </li>;
                    })}
                  </ul>
                  {active.data?.comment&&<p className="whitespace-pre-wrap break-words text-slate-600">
                    Комментарий: {active.data.comment}</p>}
                </>}
            </div>}
          </li>)}
        </ol>
        {!!receipts.data&&receipts.data.totalElements>receipts.data.size&&
          <PurchasePager page={receipts.data.page} size={receipts.data.size}
            total={receipts.data.totalElements} label="поступлений"
            onPage={n=>{setChosen(null);setPage(n);}}/>}
      </>}
  </section>;
}
