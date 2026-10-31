import { useRef, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import { salesKeys } from '../../features/sales/api';
import { money } from '../../features/sales/validation';
import type { SaleResponse } from '../../features/sales/types';
import { useAuth } from '../../features/auth/AuthProvider';

interface Payment { id:string; amount:string; method:'CASH'|'TRANSFER'; status:'PAID'|'CANCELLED' }
export function SalePaymentPanel({sale}:{sale:SaleResponse}) {
  const {hasPermission}=useAuth();
  const client=useQueryClient();
  const [method,setMethod]=useState<'CASH'|'TRANSFER'>('CASH');
  const [busy,setBusy]=useState(false);
  const [unknown,setUnknown]=useState(false);
  const [error,setError]=useState('');
  const pending=useRef(false);
  const [submitted,setSubmitted]=useState(false);
  const payments=useQuery({queryKey:['payments','sale',sale.id],
    queryFn:()=>apiClient.get<Payment[]>('/api/payments/sale/'+encodeURIComponent(sale.id)),
    enabled:hasPermission('PAYMENTS_READ')||hasPermission('PAYMENTS_CREATE')});
  const active=payments.data?.find(p=>p.status==='PAID');
  async function register() {
    if(pending.current||unknown||active||submitted||sale.status!=='CONFIRMED'&&sale.status!=='FULFILLED')return;
    pending.current=true;setBusy(true);setError('');
    try {
      // Re-read before a non-idempotent POST. A failed response may still have committed.
      const fresh=await apiClient.get<Payment[]>('/api/payments/sale/'+encodeURIComponent(sale.id));
      if(fresh.some(p=>p.status==='PAID')) {
        setError('Оплата уже существует. Повторное создание заблокировано.');
        await payments.refetch();return;
      }
      await apiClient.post(sale.fulfillmentType==='SELF_PICKUP'?'/api/payments/pickup-checkout':'/api/payments',
        {saleId:sale.id,method,reference:null,comment:null,channel:null});
      setSubmitted(true);
      // A successful POST is authoritative; cache refresh errors must not
      // trigger another non-idempotent payment request.
      void Promise.allSettled([payments.refetch(),client.invalidateQueries({queryKey:salesKeys.detail(sale.id)}),
        client.invalidateQueries({queryKey:salesKeys.lists})]);
    } catch {
      setUnknown(true);
      setError('Результат оплаты неизвестен. Проверьте оплату перед повторной отправкой.');
    } finally {pending.current=false;setBusy(false);}
  }
  return <section aria-label="Оплата заказа" className="space-y-3 rounded-xl border border-slate-200 bg-white p-4">
    <h3 className="font-semibold">Оплата заказа</h3>
    {payments.isPending&&<p className="text-sm text-slate-500">Загружаем оплату…</p>}
    {payments.isError&&<p role="alert" className="text-sm text-red-700">Не удалось проверить оплату.</p>}
    {active?<p className="font-semibold text-green-700">Оплачено: {money(active.amount)} · {active.method==='CASH'?'Наличные':'Перевод'}</p>:
      <p className="text-sm text-slate-600">Оплата не зарегистрирована</p>}
    {error&&<p role="alert" className="text-sm text-amber-800">{error}</p>}
    {hasPermission('PAYMENTS_CREATE')&&!active&&(sale.status==='CONFIRMED'||sale.status==='FULFILLED')&&
      <div className="space-y-2">
        <label className="block text-sm font-medium">Способ оплаты
          <select value={method} onChange={e=>setMethod(e.target.value as 'CASH'|'TRANSFER')}
            className="mt-1 min-h-11 w-full rounded-lg border px-3">
            <option value="CASH">Наличные</option><option value="TRANSFER">Перевод</option>
          </select>
        </label>
        <button type="button" disabled={busy||unknown||submitted||payments.isPending||payments.isError}
          onClick={()=>void register()} className="rounded-xl bg-blue-600 px-4 py-3 font-semibold text-white disabled:opacity-50">
          {busy?'Сохраняем…':'Подтвердить получение оплаты'}
        </button>
      </div>}
    {unknown&&<button type="button" className="rounded-lg border px-3 py-2 text-sm"
      onClick={async()=>{try {const refreshed=await apiClient.get<Payment[]>('/api/payments/sale/'+encodeURIComponent(sale.id));
        client.setQueryData(['payments','sale',sale.id],refreshed);
        if(refreshed.some(p=>p.status==='PAID')) {setUnknown(false);setSubmitted(true);setError('Оплата подтверждена сервером.');}
        else setError('Оплата не подтверждена. Повторная отправка заблокирована до ручной проверки.');
      }catch {setError('Проверка не удалась. Повторная отправка заблокирована.');}}}>
      Проверить оплату
    </button>}
  </section>;
}
