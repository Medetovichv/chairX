import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { PageHeader } from '../../../shared/components/PageHeader';
import { ForbiddenPage } from '../../../pages/ForbiddenPage';
import { ApiClientError, userFacingApiError } from '../../../shared/api/api-error';
import { deliveryApi, deliveryKeys } from '../api';
import { DeliveryForm } from '../components/DeliveryForm';
import { readPendingCreate, savePendingCreate, clearPendingCreate } from '../pending-create';
import type { CreateDeliveryInput } from '../types';
export function CreateDeliveryPage(){
  const {hasPermission,user}=useAuth();
  const allowed=hasPermission('DELIVERIES_READ')&&hasPermission('DELIVERIES_MANAGE');
  const [qs]=useSearchParams();
  const navigate=useNavigate();
  const client=useQueryClient();
  const [initial]=useState(()=>readPendingCreate(user?.id));
  const [pending,setPending]=useState(initial.kind==='pending'?initial.payload:null);
  const [invalid,setInvalid]=useState(initial.kind==='invalid');
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState('');
  const once=useRef(false);
  useEffect(()=>{
    if(!busy&&!pending)return;
    const listener=(e:BeforeUnloadEvent)=>{e.preventDefault();e.returnValue='';};
    window.addEventListener('beforeunload',listener);
    return()=>window.removeEventListener('beforeunload',listener);
  },[busy,pending]);
  async function create(payload:CreateDeliveryInput){
    if(once.current||busy||pending||invalid||!allowed)return;
    // There is deliberately no backend idempotency key for creation.
    // Persist and lock before POST; never auto-replay after an unknown outcome.
    if(!user?.id||!savePendingCreate(user.id,payload)){
      setInvalid(true);setError('Не удалось сохранить незавершённое создание. Отправка заблокирована.');return;
    }
    once.current=true;setBusy(true);setError('');
    try{
      const result=await deliveryApi.create(payload);
      clearPendingCreate(user.id);
      await Promise.all([
        client.invalidateQueries({queryKey:deliveryKeys.lists}),
        client.invalidateQueries({queryKey:deliveryKeys.bySale(result.saleId)}),
      ]);
      navigate('/deliveries/'+encodeURIComponent(result.id),{replace:true,
        state:{notice:'Доставка создана.'}});
    }catch(cause){
      setError(userFacingApiError(cause));
      if(!(cause instanceof ApiClientError)||cause.kind!=='http'||cause.status===409||
        cause.status===undefined||cause.status>=500){
        setPending(payload);
        setError(userFacingApiError(cause)+
          ' Результат может быть уже сохранён. Проверьте список по заказу и не отправляйте второй POST вслепую.');
      }else clearPendingCreate(user.id);
    }finally{once.current=false;setBusy(false);}
  }
  if(!allowed)return <ForbiddenPage/>;
  return <div className="space-y-5">
    <PageHeader title="Оформить доставку" description="Подтверждённая продажа с городской или региональной доставкой."/>
    <Link className="font-semibold text-indigo-700 underline" to="/deliveries">← Все доставки</Link>
    {error&&<p role="alert" className="rounded-xl bg-red-50 p-4 text-sm text-red-800">{error}</p>}
    {(pending||invalid)&&<section aria-label="Неопределённое создание"
      className="space-y-3 rounded-xl border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900">
      <h3 className="font-bold">Нельзя безопасно повторить создание доставки</h3>
      <p>Исходный POST мог уже создать доставку. Отдельного idempotencyKey в API нет;
        backend запрещает второй объект для той же продажи, но повтор не возвращает прежний результат.</p>
      {pending&&<p>Проверьте связанную продажу и список доставок, прежде чем
        создавать ещё одну доставку. При необходимости обратитесь к администратору.</p>}
      <Link className="font-semibold text-indigo-700 underline" to="/deliveries">Проверить список доставок</Link>
      {invalid&&<p>Хранилище операции недоступно или повреждено. Новые операции заблокированы.</p>}
      <p>После сверки откройте отдельную вкладку с новой операцией; не повторяйте POST без проверки.</p>
    </section>}
    {!pending&&!invalid&&<DeliveryForm initialSaleId={qs.get('saleId')??''} disabled={busy}
      onSubmit={create}/>}
  </div>;
}
