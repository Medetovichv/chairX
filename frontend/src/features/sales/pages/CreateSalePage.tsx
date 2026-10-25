import { useQuickViewDirty } from '../../../shared/components/QuickViewDrawer';
import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { ApiClientError, userFacingApiError } from '../../../shared/api/api-error';
import { PageHeader } from '../../../shared/components/PageHeader';
import { Button } from '../../../shared/ui/button';
import { ForbiddenPage } from '../../../pages/ForbiddenPage';
import { SaleForm } from '../components/SaleForm';
import { salesApi, salesKeys } from '../api';
import { saleStatisticsKeys } from '../statistics';
import { displaySaleNumber } from '../../../shared/lib/format';
import { readPendingDraft, savePendingDraft, clearPendingDraft } from '../pending-draft';
import { uuidValid } from '../validation';
import type { CreateDraftInput, DraftInput, SaleResponse } from '../types';

function unresolved(error:unknown):boolean {
  return !(error instanceof ApiClientError) ||
    error.kind!=='http' || error.status===undefined ||
    error.status>=500 || error.status===409;
}
export function CreateSalePage({embedded=false,onCreated}:{embedded?:boolean;onCreated?:(id:string)=>void} = {}) {
  const markDirty=useQuickViewDirty();
  const {hasPermission,user}=useAuth();
  const allowed=hasPermission('SALES_CREATE');
  const canRead=hasPermission('SALES_READ');
  const navigate=useNavigate();
  const queryClient=useQueryClient();
  const [params]=useSearchParams();
  const param=params.get('customerId')??'';
  const initialCustomer=hasPermission('CUSTOMERS_READ')&&uuidValid(param)?param:null;
  const [stored]=useState(()=>readPendingDraft(user?.id));
  const [pending,setPending]=useState<CreateDraftInput|null>(
    stored.kind==='pending'?stored.payload:null,
  );
  const [invalid,setInvalid]=useState(stored.kind==='invalid');
  const [posting,setPosting]=useState(false);
  const inFlight=useRef(false);
  const [problem,setProblem]=useState('');
  const [success,setSuccess]=useState<SaleResponse|null>(null);

  useEffect(()=>{
    if(!pending&&!posting)return;
    const warn=(e:BeforeUnloadEvent)=>{e.preventDefault();e.returnValue='';};
    window.addEventListener('beforeunload',warn);
    return()=>window.removeEventListener('beforeunload',warn);
  },[pending,posting]);

  async function save(data:DraftInput, retry?:CreateDraftInput) {
    if(inFlight.current||!allowed||invalid)return;
    const payload:CreateDraftInput=retry??{...data,idempotencyKey:crypto.randomUUID()};
    if(!user?.id||!savePendingDraft(user.id,payload)){
      setInvalid(true);
      setProblem('Не удалось сохранить ключ запроса. Черновик не отправлен. Проверьте состояние вкладки.');
      return;
    }
    inFlight.current=true;setPosting(true);setProblem('');
    try {
      const result=payload.deliveryDetails?
        await salesApi.createWithDelivery(payload):await salesApi.createDraft(payload);
      clearPendingDraft(user.id);
      setPending(null);
      await queryClient.invalidateQueries({queryKey:salesKeys.lists});
      await queryClient.invalidateQueries({queryKey:salesKeys.kanban});
      await queryClient.invalidateQueries({queryKey:saleStatisticsKeys.last30Days});
      await queryClient.invalidateQueries({queryKey:['dashboard']});
      if(embedded && onCreated) { markDirty(false); onCreated(result.id); }
      else if(canRead) navigate('/sales/'+encodeURIComponent(result.id), {replace:true,
        state:{notice:payload.deliveryDetails?'Продажа подтверждена, доставка создана.':'Черновик создан.'}});
      else setSuccess(result);
    }catch(error){
      setProblem(userFacingApiError(error));
      if(unresolved(error)){
        // Server may already have committed: preserve exact operation + key.
        setPending(payload);
      }else{
        clearPendingDraft(user.id);
        setPending(null);
        throw error; // SaleForm retains fields after a definite error.
      }
    }finally{inFlight.current=false;setPosting(false);}
  }
  if(!allowed)return <ForbiddenPage/>;
  return <div onChangeCapture={embedded ? () => markDirty(true) : undefined} className="space-y-6">
    {!embedded&&<PageHeader title="Новая продажа" description="Создайте черновик или подтвердите продажу сразу с оформлением доставки."/>}
    {!embedded&&canRead&&<Link className="inline-flex min-h-11 items-center font-semibold text-indigo-700"
      to="/sales">← Все продажи</Link>}
    {success&&<div role="status" className="space-y-2 rounded-xl bg-emerald-50 p-5">
      <h3 className="font-bold">{success.status==='CONFIRMED'?'Продажа и доставка оформлены':'Черновик создан'}</h3>
      <p>Заказ {displaySaleNumber(success.saleNumber)}</p>
      <p>Чтобы открыть карточку заказа, требуется SALES_READ.</p>
    </div>}
    {problem&&<p role="alert" className="rounded-xl bg-red-50 p-4 text-sm text-red-800">{problem}</p>}
    {invalid&&<p role="alert" className="rounded-xl bg-amber-50 p-4 text-sm text-amber-900">
      Хранилище ключа недоступно или повреждено. Создание заблокировано для защиты от повторного заказа.
    </p>}
    {pending&&<section aria-label="Восстановление черновика"
      className="space-y-3 rounded-xl border border-amber-200 bg-amber-50 p-5 text-sm text-amber-900">
      <h3 className="font-bold">Результат создания черновика неизвестен</h3>
      <p>Заказ мог сохраниться. Не создавайте новый запрос с новым UUID.</p>
      <p>Сохранён исходный ключ запроса для безопасного повтора.</p>
      <p>После обновления этой вкладки исходный payload и ключ сохраняются. Повтор безопасен благодаря серверной идемпотентности.</p>
      <Button disabled={posting} onClick={()=>void save(pending,pending)}>
        {posting?'Проверяем…':'Повторить исходный запрос'}
      </Button>
    </section>}
    {!pending&&!invalid&&!success&&<SaleForm defaultCustomerId={initialCustomer}
      onSave={async data=>{await save(data);}}/>}
  </div>;
}
