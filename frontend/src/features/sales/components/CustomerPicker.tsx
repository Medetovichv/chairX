import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router';
import { useAuth } from '../../auth/AuthProvider';
import { customersApi, customerKeys } from '../../customers/api';
import { CustomerForm } from '../../customers/components/CustomerForm';
import type { CustomerInput } from '../../customers/types';
import { unknownCustomerWriteOutcome } from './customer-write-outcome';
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
export function CustomerPicker({value,onChange,onPhoneDraftChange,disabled=false}:{
  value:string|null;onChange:(id:string|null)=>void;
  onPhoneDraftChange?:(phone:string)=>void;disabled?:boolean;
}) {
  const {hasPermission}=useAuth();
  const queryClient=useQueryClient();
  const allowed=hasPermission('CUSTOMERS_READ');
  const canCreate=hasPermission('CUSTOMERS_CREATE');
  const [search,setSearch]=useState('');
  const [query,setQuery]=useState('');
  const [selected,setSelected]=useState<Customer|null>(null);
  const [creating,setCreating]=useState(false);
  const dialogRef=useRef<HTMLElement>(null);
  const createButtonRef=useRef<HTMLButtonElement>(null);
  useEffect(()=>{
    if(!creating)return;
    const overlay=dialogRef.current?.parentElement;
    const background=Array.from(document.body.children).filter(
      (node):node is HTMLElement=>node instanceof HTMLElement&&node!==overlay);
    const inertValues=background.map(node=>node.inert);
    background.forEach(node=>{node.inert=true;});
    dialogRef.current?.querySelector<HTMLButtonElement>('button')?.focus();
    return()=>{
      background.forEach((node,index)=>{node.inert=inertValues[index]!;});
      createButtonRef.current?.focus();
    };
  },[creating]);
  const [createBusy,setCreateBusy]=useState(false);
  const [phone,setPhone]=useState('');
  const [phoneBusy,setPhoneBusy]=useState(false);
  const [phoneError,setPhoneError]=useState('');

  const [creationUncertain,setCreationUncertain]=useState(false);
  async function createInsideSale(data:CustomerInput) {
    if(createBusy||creationUncertain)throw new Error('Проверьте результат предыдущего создания клиента.');
    setCreateBusy(true);
    try {
      const created=await customersApi.create(data);
      queryClient.setQueryData(customerKeys.detail(created.id),created);
      queryClient.setQueryData(['customers','customer',created.id],created);
      // Selecting the created customer must never depend on refreshing the list.
      setSelected(created);
      onChange(created.id);
      setSearch('');
      setQuery('');
      setCreating(false);
      void queryClient.invalidateQueries({queryKey:customerKeys.lists});
    } catch(error) {
      // Customer creation has no explicit idempotency key; never silently retry
      // when the request might already have created the record.
      if(unknownCustomerWriteOutcome(error)) {
        setCreationUncertain(true);
      }
      throw error;
    } finally {setCreateBusy(false);}
  }
  async function resolvePhone() {
    if(phoneBusy||createBusy||creationUncertain||!canCreate||!allowed||disabled)return;
    setPhoneBusy(true);setPhoneError('');
    try {
      const found=await customersApi.resolvePhone(phone);
      queryClient.setQueryData(customerKeys.detail(found.id),found);
      queryClient.setQueryData(['customers','customer',found.id],found);
      setSelected(found);onChange(found.id);
      setPhone('');setSearch('');setQuery('');
      void queryClient.invalidateQueries({queryKey:customerKeys.lists});
    } catch(error) {
      setPhoneError(error instanceof Error?error.message:'Не удалось определить клиента по телефону');
      // The POST may have committed even when the response was lost.
      // Don't issue another uncorrelated write without manual reconciliation.
      if(unknownCustomerWriteOutcome(error)){
        setCreationUncertain(true);
      }
    } finally {setPhoneBusy(false);}
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
      <p className="text-xs text-slate-600">Необязательно для черновика</p>
      {canCreate && allowed && <Button type="button" variant="outline" size="sm"
        ref={createButtonRef} disabled={disabled||creating} onClick={()=>setCreating(true)}>+ Новый клиент</Button>}
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
    {allowed&&canCreate&&<div className="space-y-3 rounded-xl border border-[#dfe7eb] bg-[#f2f7f6] p-4">
      <div>
        <p className="text-sm font-semibold text-[#182d38]">Быстро привязать клиента по телефону</p>
        <p className="mt-1 text-xs text-[#516977]">Повторно используем найденную карточку или создадим контакт без имени. Если номер неоднозначен, потребуется выбрать клиента вручную.</p>
      </div>
      <div className="flex flex-wrap items-end gap-2">
        <label className="min-w-[13rem] flex-1 space-y-1 text-sm font-semibold">Номер клиента
          <Input type="tel" aria-label="Номер клиента для продажи" value={phone} maxLength={50}
            placeholder="+996 700 123456" disabled={disabled||phoneBusy||creationUncertain}
            onChange={e=>{setPhone(e.target.value);setPhoneError('');onPhoneDraftChange?.(e.target.value);}}/>
        </label>
        <Button type="button" variant="outline" disabled={disabled||phoneBusy||creationUncertain||!phone.trim()}
          onClick={()=>void resolvePhone()}>
          {phoneBusy?'Проверяем номер…':'Найти или создать'}
        </Button>
      </div>
      {phoneError&&<p role="alert" className="text-sm text-red-700">{phoneError}</p>}
      {creationUncertain&&<p role="alert" className="text-sm text-amber-800">
        Результат предыдущего создания неизвестен. Проверьте клиента вручную, прежде чем повторять операцию.
      </p>}
    </div>}
    {allowed && <>
      <label className="block space-y-2 text-sm font-semibold">Найти клиента
        <Input type="search" aria-label="Поиск клиента для продажи" value={search}
          disabled={disabled} maxLength={200} placeholder="Имя, телефон или WhatsApp"
          onChange={e=>setSearch(e.target.value)} />
      </label>
      {current.isError&&value&&<ErrorState title="Не удалось загрузить выбранного клиента"
        error={current.error} onRetry={()=>void current.refetch()}/>}
      {query.length>=2&&(results.isPending?<LoadingState label="Ищем клиентов…"/>:
        results.isError?<ErrorState error={results.error} onRetry={()=>void results.refetch()}/>:
        <div aria-label="Результаты поиска клиентов" className="divide-y divide-slate-100 rounded-xl border border-slate-200 bg-white">
          {(results.data?.length??0)===0&&<p className="p-3 text-sm text-slate-500">Клиент не найден</p>}
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

        </div>)}
    </>}
    {creating&&canCreate&&allowed&&createPortal(
      <div className="fixed inset-0 z-50 flex justify-end bg-slate-950/40">
        <aside ref={dialogRef} role="dialog" aria-modal="true" aria-label="Новый клиент для продажи"
          onKeyDown={event=>{
            if(event.key==='Escape'){
              event.preventDefault();event.stopPropagation();
              if(!createBusy)setCreating(false);
            }
            if(event.key==='Tab'){
              const nodes=Array.from(event.currentTarget.querySelectorAll<HTMLElement>(
                'button, a[href], input, select, textarea')).filter(node=>
                  !node.hasAttribute('disabled')&&!node.closest('fieldset[disabled]'));
              const first=nodes[0],last=nodes[nodes.length-1];
              if(event.shiftKey&&document.activeElement===first){event.preventDefault();last?.focus();}
              else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first?.focus();}
            }
          }}
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
