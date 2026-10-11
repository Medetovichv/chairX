import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router';
import { useAuth } from '../../auth/AuthProvider';
import { customersApi, customerKeys } from '../../customers/api';
import { CustomerForm } from '../../customers/components/CustomerForm';
import type { CustomerInput } from '../../customers/types';
import { ApiClientError } from '../../../shared/api/api-error';
import { salesKeys } from '../api';
import { Button } from '../../../shared/ui/button';
import { Input } from '../../../shared/ui/input';
import { ErrorState } from '../../../shared/components/ErrorState';
import { LoadingState } from '../../../shared/components/LoadingState';
import type { Customer } from '../../customers/types';

function display(customer:Customer):string {
  return customer.fullName?.trim() || customer.phone || customer.whatsappPhone ||
    (customer.instagramUsername ? '@'+customer.instagramUsername : '') || 'Клиент без имени';
}
export function CustomerPicker({value,onChange,disabled=false}:{
  value:string|null;onChange:(id:string|null)=>void;disabled?:boolean;
}) {
  const {hasPermission}=useAuth();
  const queryClient=useQueryClient();
  const allowed=hasPermission('CUSTOMERS_READ');
  const canCreate=hasPermission('CUSTOMERS_CREATE');
  const [search,setSearch]=useState('');
  const [query,setQuery]=useState('');
  const [selected,setSelected]=useState<Customer|null>(null);
  const [creating,setCreating]=useState(false);
  const [createBusy,setCreateBusy]=useState(false);
  const [creationUncertain,setCreationUncertain]=useState(false);
  async function createInsideSale(data:CustomerInput) {
    if(createBusy||creationUncertain)throw new Error('Проверьте результат предыдущего создания клиента.');
    setCreateBusy(true);
    try {
      const created=await customersApi.create(data);
      queryClient.setQueryData(customerKeys.detail(created.id),created);
      queryClient.setQueryData(['customers','customer',created.id],created);
      await queryClient.invalidateQueries({queryKey:customerKeys.lists});
      setSelected(created);
      onChange(created.id);
      setSearch('');
      setQuery('');
      setCreating(false);
    } catch(error) {
      // Customer creation has no explicit idempotency key; never silently retry
      // when the request might already have created the record.
      if(!(error instanceof ApiClientError)||error.kind!=='http'||
          error.status===undefined||error.status===409||error.status>=500) {
        setCreationUncertain(true);
      }
      throw error;
    } finally {setCreateBusy(false);}
  }
  useEffect(()=>{
    const timeout=setTimeout(()=>setQuery(search.trim()),350);
    return ()=>clearTimeout(timeout);
  },[search]);
  const current=useQuery({
    queryKey:['customers','customer',value],
    queryFn:()=>customersApi.get(value!),enabled:allowed&&!!value,
  });
  const results=useQuery({
    queryKey:salesKeys.customerSearch(query),
    queryFn:()=>customersApi.search(query),
    enabled:allowed&&query.length>=2,
  });
  const picked=selected?.id===value?selected:current.data;
  return <section aria-label="Выбор клиента" className="space-y-3">
    <div className="flex flex-wrap items-center justify-between gap-2">
      <h3 className="font-semibold">Клиент (необязательно для черновика)</h3>
      {canCreate && allowed && <Button type="button" variant="outline" size="sm"
        disabled={disabled||creating} onClick={()=>setCreating(true)}>+ Новый клиент</Button>}
    </div>
    {value && <div className="rounded-xl bg-indigo-50 p-3 text-sm">
      <p>Выбран: <strong>{picked?display(picked):'Загружаем клиента…'}</strong></p>
      {picked&&<Link className="text-sm text-blue-700 underline-offset-2 hover:underline"
        to={'/customers/'+encodeURIComponent(picked.id)}>Открыть карточку клиента</Link>}
      {picked && <p className="text-slate-600">{[picked.phone,picked.whatsappPhone,picked.cityRegion].filter(Boolean).join(' · ')}</p>}
      <Button type="button" variant="outline" size="sm" disabled={disabled}
        className="mt-2" onClick={()=>{onChange(null);setSelected(null);}}>Убрать клиента</Button>
    </div>}
    {!allowed && <p className="rounded-xl bg-amber-50 p-3 text-sm text-amber-900">
      Без CUSTOMERS_READ поиск клиентов недоступен. Можно сохранить черновик без клиента.
      {value&&' Ранее выбранный клиент сохранится при редактировании.'}
    </p>}
    {allowed && <>
      <label className="block space-y-2 text-sm font-semibold">Найти клиента
        <Input type="search" aria-label="Поиск клиента для продажи" value={search}
          disabled={disabled} maxLength={200} placeholder="Имя, телефон или WhatsApp (от 2 символов)"
          onChange={e=>setSearch(e.target.value)} />
      </label>
      {current.isError&&value&&<ErrorState title="Не удалось загрузить выбранного клиента"
        error={current.error} onRetry={()=>void current.refetch()}/>}
      {query.length>=2&&(results.isPending?<LoadingState label="Ищем клиентов…"/>:
        results.isError?<ErrorState error={results.error} onRetry={()=>void results.refetch()}/>:
        <div aria-label="Результаты поиска клиентов" className="divide-y divide-slate-100 rounded-xl border border-slate-200 bg-white">
          {(results.data?.length??0)===0&&<p className="p-3 text-sm text-slate-500">Клиенты не найдены</p>}
          {results.data?.map(customer=><div key={customer.id} className="flex flex-wrap justify-between gap-3 p-3 text-sm">
            <div className="min-w-0">
              <p className="font-semibold">{display(customer)}</p>
              <p className="text-xs text-slate-500">{[customer.phone,customer.whatsappPhone,customer.cityRegion].filter(Boolean).join(' · ')||'Контакты не указаны'}</p>
            </div>
            <Button type="button" variant="outline" disabled={disabled||!customer.active}
              onClick={()=>{setSelected(customer);onChange(customer.id);setSearch('');setQuery('');}}>
              {customer.active?'Выбрать':'Неактивен'}
            </Button>
          </div>)}
          <p className="p-3 text-xs text-slate-500">Поиск backend показывает максимум 20 совпадений. Уточните запрос.</p>
        </div>)}
    </>}
    {creating&&canCreate&&allowed&&createPortal(
      <div className="fixed inset-0 z-50 flex justify-end bg-slate-950/40">
        <aside role="dialog" aria-modal="true" aria-label="Новый клиент для продажи"
          className="h-full w-full max-w-xl overflow-y-auto bg-white p-4 shadow-xl sm:p-6">
          <div className="mb-4 flex items-center justify-between gap-3">
            <h3 className="text-lg font-bold">Создание клиента для заказа</h3>
            <Button type="button" variant="outline" disabled={createBusy}
              onClick={()=>setCreating(false)}>Закрыть</Button>
          </div>
          {creationUncertain&&<p role="alert" className="mb-4 rounded-xl bg-amber-50 p-3 text-sm text-amber-900">
            Ответ сервера не получен. Клиент мог быть создан. Повторная отправка заблокирована:
            закройте панель и проверьте клиента через поиск по телефону.
          </p>}
          <fieldset disabled={creationUncertain}>
            <CustomerForm onSave={createInsideSale} onCancel={()=>setCreating(false)}/>
          </fieldset>
        </aside>
      </div>,document.body)}
  </section>;
}
