import { useRef,useState,type FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { returnsApi,returnKeys } from '../api';
import { financeApi, financeKeys } from '../../finance/api';
import { ApiClientError } from '../../../shared/api/api-error';
import { SaleItemsEditor,type SaleEditorRow } from '../../sales/components/SaleItemsEditor';
import { Button } from '../../../shared/ui/button';
import { ConfirmDialog } from '../../../shared/ui/confirm-dialog';
import { Link } from 'react-router';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState';
import { validateExchange,uuid,positive } from '../validation';
import type { ExchangeInput } from '../types';
import type { FulfillmentType } from '../../sales/types';
export function ExchangeForm({initialReturnId,onSubmit,disabled=false}:{
 initialReturnId:string;onSubmit:(input:Omit<ExchangeInput,'idempotencyKey'>)=>Promise<void>;disabled?:boolean;
}){
 const {hasPermission}=useAuth();
 const returnId=initialReturnId;
 const [kind,setKind]=useState<FulfillmentType>('SELF_PICKUP');
 const [rows,setRows]=useState<SaleEditorRow[]>([]);
 const [error,setError]=useState('');
 const [candidate,setCandidate]=useState<Omit<ExchangeInput,'idempotencyKey'>|null>(null);
 const original=useQuery({queryKey:returnKeys.detail(returnId),queryFn:()=>returnsApi.get(returnId),
   enabled:hasPermission('RETURNS_READ')&&uuid(returnId)});
 const activePayment=useQuery({queryKey:financeKeys.activePayment(original.data?.saleId??''),
   queryFn:()=>financeApi.activePayment(original.data!.saleId),
   enabled:hasPermission('PAYMENTS_READ')&&!!original.data,retry:false});
 const definitelyUnpaid=hasPermission('PAYMENTS_READ')&&activePayment.isError&&
   activePayment.error instanceof ApiClientError&&activePayment.error.status===404;
 const creating=useRef(false);
 async function submit(e:FormEvent){
  e.preventDefault();if(disabled||creating.current)return;
  const data:ExchangeInput={idempotencyKey:crypto.randomUUID(),returnId,
   fulfillmentType:kind,items:rows.map(r=>({
    productVariantId:r.productVariantId,warehouseId:r.warehouseId,quantity:Number(r.quantity),
    unitSalePrice:r.unitSalePrice.trim(),
   }))};
  if(definitelyUnpaid){setError('Сначала зарегистрируйте оплату исходной продажи. Без активной оплаты обмен запрещён.');return;}
  if(rows.some(r=>!positive(r.quantity))){setError('Количество должно быть целым положительным.');return;}
  const problem=validateExchange(data);
  if(problem){setError(problem);return;}
  const {idempotencyKey:unused,...plain}=data;
  void unused;
  setError('');
  setCandidate(plain);
 }
 async function confirm(){
  if(!candidate||creating.current)return;
  creating.current=true;
  const draft=candidate;setCandidate(null);
  try{await onSubmit(draft);}
  finally{creating.current=false;}
 }
 return <form aria-label="Создать обмен" className="space-y-4 rounded-xl border bg-white p-4" onSubmit={e=>void submit(e)}>
  {!uuid(returnId)&&<div role="alert" className="space-y-2 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm"><p className="font-semibold">Сначала выберите оформленный возврат.</p><p>Откройте список возвратов и нажмите «Оформить обмен» в нужной карточке. Вводить технический номер не нужно.</p><Button type="button" variant="outline" asChild><Link to="/returns">Перейти к возвратам</Link></Button></div>}
  {original.isPending&&hasPermission('RETURNS_READ')&&uuid(returnId)&&<LoadingState label="Загружаем исходный возврат…"/>}
  {original.isError&&<ErrorState error={original.error} onRetry={()=>void original.refetch()}/>}
  {original.data&&<div className="rounded-xl bg-slate-50 p-3 text-sm">
   <p className="font-semibold">Возврат выбран · Позиций: {original.data.items.length}</p>
   <p>Причина: {original.data.reason}</p>
  </div>}
  {!hasPermission('RETURNS_READ')&&<p className="text-sm text-amber-800">Для оформления обмена нужен доступ к исходному возврату.</p>}
  <label className="block space-y-1 text-sm font-semibold">Тип выдачи нового товара
   <select aria-label="Тип выдачи обмена" className="min-h-11 w-full rounded-xl border p-2"
    value={kind} disabled={disabled} onChange={e=>setKind(e.target.value as FulfillmentType)}>
    <option value="SELF_PICKUP">Самовывоз</option>
    <option value="CITY_DELIVERY">Городская доставка</option>
    <option value="REGION_DELIVERY">Региональная доставка</option>
   </select>
  </label>
  {definitelyUnpaid&&<p role="alert" className="rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm">По исходной продаже нет активной оплаты. Сначала проведите оплату в карточке продажи; создание обмена сейчас недоступно.</p>}
  {original.data&&<div className="rounded-xl border border-blue-100 bg-blue-50 p-4 text-sm text-slate-800"><p className="font-semibold">Перед обменом проверьте</p><ul className="mt-2 list-inside list-disc space-y-1"><li>Исходная продажа была оплачена.</li><li>По этому возврату ещё не возвращали деньги и не оформляли обмен.</li><li>Новые кресла есть на выбранном складе в нужном количестве.</li></ul><p className="mt-2 text-slate-600">Если одно из условий не выполнено, сервер отклонит обмен без изменения складских и денежных остатков.</p></div>}
  <SaleItemsEditor rows={rows} onChange={setRows} disabled={disabled}/>
  <p className="text-xs text-slate-500">
   После подтверждения создаётся новая связанная продажа. При денежной разнице расчёт оформляется отдельным безопасным действием.
  </p>
  {error&&<p role="alert" className="text-sm text-red-700">{error}</p>}
  <Button type="submit" disabled={disabled||candidate!==null||!uuid(returnId)||!hasPermission("RETURNS_READ")||!original.isSuccess||definitelyUnpaid}>Проверить и создать обмен</Button>
  <ConfirmDialog open={candidate!==null} onOpenChange={open=>{if(!open)setCandidate(null);}}
    title="Подтвердить создание обмена?"
    description={candidate?'Новых позиций: '+candidate.items.length+
      '. Backend создаст новую продажу и складской резерв. Денежную разницу рассчитает сервер.':
      ''}
    confirmLabel="Создать обмен" onConfirm={()=>void confirm()}/>
 </form>;
}
