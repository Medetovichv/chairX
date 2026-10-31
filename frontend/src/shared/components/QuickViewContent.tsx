import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router';
import { useAuth } from '../../features/auth/AuthProvider';
import { CustomerForm } from '../../features/customers/components/CustomerForm';
import { DeliveryActions } from '../../features/deliveries/components/DeliveryActions';
import { DeliveryTimeline } from '../../features/deliveries/components/DeliveryTimeline';
import { SaleCommentsPanel } from '../../features/sales/components/SaleCommentsPanel';
import { SaleForm } from '../../features/sales/components/SaleForm';
import { SaleQuickActions } from './SaleQuickActions';
import { saleStatusNames, fulfillmentNames, money } from '../../features/sales/validation';
import { inventoryKeys } from '../../features/inventory/api';
import { customersApi, customerKeys } from '../../features/customers/api';
import { deliveryApi, deliveryKeys } from '../../features/deliveries/api';
import { salesApi, salesKeys } from '../../features/sales/api';
import { ErrorState } from './ErrorState';
import { LoadingState } from './LoadingState';
import { useQuickViewDirty, useQuickViewCanLeave } from './QuickViewDrawer';

type Tab = 'overview' | 'actions' | 'history';
function Tabs({ value, onChange }: { value: Tab; onChange: (tab: Tab) => void }) {
  const canLeave = useQuickViewCanLeave();
  return <div role="tablist" aria-label="Разделы карточки" className="flex gap-2 border-b border-slate-200 pb-2">
    {([['overview', 'Обзор'], ['actions', 'Действия'], ['history', 'История']] as const).map(([key, label]) =>
      <button type="button" role="tab" aria-selected={value === key} key={key}
        className={'rounded-xl px-3 py-2 text-sm font-medium ' + (value === key ? 'bg-blue-600 text-white' : 'bg-slate-50 text-slate-700')}
        onClick={() => { if (value !== key && canLeave()) onChange(key); }}
        onKeyDown={event => {
          const keys: Tab[] = ['overview', 'actions', 'history'];
          const index = keys.indexOf(value);
          const target = event.key === 'ArrowRight' ? keys[(index + 1) % keys.length] :
            event.key === 'ArrowLeft' ? keys[(index + keys.length - 1) % keys.length] :
            event.key === 'Home' ? keys[0] : event.key === 'End' ? keys[keys.length - 1] : undefined;
          if (!target) return;
          event.preventDefault();
          if (target !== value && canLeave()) {
            onChange(target);
            const list = event.currentTarget.parentElement;
            list?.querySelector<HTMLButtonElement>('[data-quick-tab="' + target + '"]')?.focus();
          }
        }}
        tabIndex={value === key ? 0 : -1} data-quick-tab={key}>{label}</button>)}
  </div>;
}
function Field({ label, value }: { label: string; value: string | number | null | undefined }) {
  return <div className="border-b border-slate-100 py-3">
    <dt className="text-xs font-medium text-slate-500">{label}</dt>
    <dd className="mt-1 break-words text-sm font-medium text-slate-900">{value === null || value === undefined || value === '' ? '—' : value}</dd>
  </div>;
}
function Section({ children }: { children: React.ReactNode }) {
  return <dl className="rounded-xl border border-slate-200 bg-white px-4">{children}</dl>;
}
export function SaleQuickView({ id }: { id: string }) {
  const { hasPermission } = useAuth();
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState(false);
  const markDirty = useQuickViewDirty();
  const [saveError, setSaveError] = useState('');
  const detail = useQuery({ queryKey: salesKeys.detail(id), queryFn: () => salesApi.get(id) });
  const relatedDelivery = useQuery({queryKey:deliveryKeys.bySale(id), queryFn:()=>deliveryApi.bySale(id),
    enabled:hasPermission('DELIVERIES_READ')&&detail.data?.fulfillmentType!== 'SELF_PICKUP'&&!!detail.data?.fulfillmentType,
    retry:false});
  if (detail.isPending) return <LoadingState label="Загружаем продажу…"/>;
  if (detail.isError) return <ErrorState error={detail.error} onRetry={() => void detail.refetch()}/>;
  const sale = detail.data;
  return <div className="space-y-5">
    <h3 className="text-base font-bold">Информация и управление заказом</h3>
    <Section>
      <Field label="Заказ" value={sale.saleNumber}/>
      <Field label="Статус" value={saleStatusNames[sale.status]}/>
      <Field label="Создан" value={new Date(sale.createdAt).toLocaleString('ru-RU')}/>
      <Field label="Сумма заказа" value={money(sale.total)}/>
      <Field label="Получение" value={sale.fulfillmentType?fulfillmentNames[sale.fulfillmentType]:null}/>
    </Section>
    <section><h3 className="mb-2 font-semibold">Позиции заказа</h3>
      {sale.items.map((item, index) => <Section key={item.id}>
        <Field label={'Позиция ' + (index + 1)} value={item.quantity + ' шт.'}/>
        <Field label="Цена за единицу" value={money(item.unitSalePrice)}/>
        <Field label="Сумма позиции" value={money(item.total)}/>
      </Section>)}
    </section>
    <div className="space-y-3">
      {saveError && <p role="alert" className="rounded-xl bg-red-50 p-3 text-sm text-red-700">{saveError}</p>}
      {sale.status === 'DRAFT' && hasPermission('SALES_DRAFT_MANAGE') && !editing && <button type="button" className="rounded-xl bg-blue-600 px-4 py-3 text-white" onClick={() => setEditing(true)}>Редактировать черновик</button>}
      {editing && <div onChangeCapture={() => markDirty(true)}><SaleForm initial={sale} onCancel={() => { if (window.confirm('Отменить изменения черновика?')) { markDirty(false); setEditing(false); } }} onSave={async input => {
        const fresh = await salesApi.get(id);
        const snapshot = (value: typeof fresh) => JSON.stringify({status:value.status,customerId:value.customerId,fulfillmentType:value.fulfillmentType,comment:value.comment,items:value.items.map(i=>[i.productVariantId,i.warehouseId,i.quantity,String(i.unitSalePrice)]),total:String(value.total)});
        if (snapshot(fresh) !== snapshot(sale)) {
          queryClient.setQueryData(salesKeys.detail(id), fresh);
          throw new Error('Черновик изменён другим сотрудником; проверьте обновлённые данные.');
        }
        try {
          const updated = await salesApi.updateDraft(id, input);
          queryClient.setQueryData(salesKeys.detail(id), updated);
          await Promise.all([queryClient.invalidateQueries({queryKey:salesKeys.lists}),queryClient.invalidateQueries({queryKey:salesKeys.kanban}),queryClient.invalidateQueries({queryKey:salesKeys.historyPrefix(id)})]);
          markDirty(false);
          setEditing(false);
          setSaveError('');
        } catch (cause) {
          setSaveError('Результат сохранения необходимо проверить перед повтором.');
          throw cause;
        }
      }}/></div>}
      {!editing && <SaleQuickActions sale={sale}/>}
      {sale.fulfillmentType!=='SELF_PICKUP'&&relatedDelivery.data&&<section className="space-y-3 rounded-xl border border-slate-200 p-4">
        <h3 className="font-semibold">Управление доставкой</h3>
        <p className="text-sm text-slate-600">{relatedDelivery.data.address} · {relatedDelivery.data.recipientPhone}</p>
        <DeliveryActions delivery={relatedDelivery.data} onChanged={async updated=>{
          queryClient.setQueryData(deliveryKeys.detail(updated.id),updated);
          queryClient.setQueryData(deliveryKeys.bySale(id),updated);
          await Promise.all([queryClient.invalidateQueries({queryKey:deliveryKeys.lists}),
            queryClient.invalidateQueries({queryKey:salesKeys.detail(id)}),
            queryClient.invalidateQueries({queryKey:salesKeys.lists})]);
        }}/>
      </section>}
      <Link className="inline-flex rounded-xl border border-blue-200 bg-blue-50 px-4 py-3 font-medium text-blue-800" to={'/sales/' + encodeURIComponent(id)}>Открыть полную страницу продажи и оплату</Link>
      <SaleCommentsPanel saleId={id}/>
    </div>

  </div>;
}
export function CustomerQuickView({ id }: { id: string }) {
  const { hasPermission } = useAuth();
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState(false);
  const markDirty = useQuickViewDirty();
  const [tab, setTab] = useState<Tab>('overview');
  const [notice, setNotice] = useState('');
  const customer = useQuery({ queryKey: customerKeys.detail(id), queryFn: () => customersApi.get(id) });
  const history = useQuery({ queryKey: customerKeys.history(id, 0), queryFn: () => customersApi.history(id, 0),
    enabled: hasPermission('SALES_READ') });
  if (customer.isPending) return <LoadingState label="Загружаем клиента…"/>;
  if (customer.isError) return <ErrorState error={customer.error} onRetry={() => void customer.refetch()}/>;
  const c = customer.data;
  return <div className="space-y-5">
    <Tabs value={tab} onChange={setTab}/>
    {notice && <p role="status" className="rounded-xl bg-green-50 p-3 text-sm text-green-800">{notice}</p>}
    {tab === 'actions' && hasPermission('CUSTOMERS_MANAGE') && !editing && <button type="button" className="rounded-xl border px-4 py-2 font-medium" onClick={() => setEditing(true)}>Редактировать клиента</button>}
    {tab === 'actions' && editing && <div onChangeCapture={() => markDirty(true)}><CustomerForm key={c.updatedAt} initial={c} onCancel={() => { if (window.confirm('Отменить изменения клиента?')) { markDirty(false); setEditing(false); } }} onSave={async input => {
      const fresh = await customersApi.get(id);
      if (fresh.updatedAt !== c.updatedAt) {
        queryClient.setQueryData(customerKeys.detail(id), fresh);
        throw new Error('Контакт изменён другим сотрудником. Проверьте данные заново.');
      }
      const updated = await customersApi.update(id, input);
      queryClient.setQueryData(customerKeys.detail(id), updated);
      await queryClient.invalidateQueries({ queryKey: customerKeys.lists });
      markDirty(false);
      setEditing(false);
      setNotice('Клиент обновлён.');
    }}/></div>}
    {tab === 'overview' && <Section>
      <Field label="Имя" value={c.fullName}/>
      <Field label="Телефон" value={c.phone}/>
      <Field label="WhatsApp" value={c.whatsappPhone}/>
      <Field label="Дополнительный телефон" value={c.secondaryPhone}/>
      <Field label="Instagram" value={c.instagramUsername}/>
      <Field label="Регион" value={c.cityRegion}/>
      <Field label="Адрес" value={c.address}/>
      <Field label="Комментарий" value={c.comment}/>
    </Section>}
    {tab === 'history' && hasPermission('SALES_READ') && <section><h3 className="mb-2 font-semibold">Заказы клиента</h3>
      {history.isPending ? <LoadingState label="Загружаем заказы…"/> :
        history.isError ? <p role="status">История недоступна.</p> :
        history.data.items.length ? <ul className="space-y-2">{history.data.items.map(s =>
          <li key={s.id}><Link className="text-blue-700 underline" to={'/sales/' + encodeURIComponent(s.id)}>{s.saleNumber}</Link> · {s.status}</li>)}</ul> :
        <p className="text-sm text-slate-500">Заказов нет.</p>}
    </section>}
    {tab === 'actions' && hasPermission('SALES_CREATE') && c.active && <Link className="inline-flex rounded-xl bg-blue-600 px-4 py-3 text-sm font-semibold text-white"
      to={'/sales/new?customerId=' + encodeURIComponent(id)}>Новая продажа</Link>}
  </div>;
}
export function DeliveryQuickView({ id }: { id: string }) {
  const [tab, setTab] = useState<Tab>('overview');
  const queryClient = useQueryClient();
  const { hasPermission } = useAuth();
  const delivery = useQuery({ queryKey: deliveryKeys.detail(id), queryFn: () => deliveryApi.get(id) });
  if (delivery.isPending) return <LoadingState label="Загружаем доставку…"/>;
  if (delivery.isError) return <ErrorState error={delivery.error} onRetry={() => void delivery.refetch()}/>;
  const d = delivery.data;
  return <div className="space-y-5">
    <Tabs value={tab} onChange={setTab}/>
    {tab === 'overview' && <Section>
      <Field label="Статус" value={d.status}/>
      <Field label="Получатель" value={d.recipientName}/>
      <Field label="Телефон" value={d.recipientPhone}/>
      <Field label="Город / регион" value={d.cityRegion}/>
      <Field label="Адрес" value={d.address}/>
      <Field label="Плановая дата" value={d.plannedDeliveryDate}/>
      <Field label="Стоимость доставки, сом" value={d.deliveryCost}/>
      <Field label="Перевозчик" value={d.carrierName}/>
      <Field label="Трек-номер" value={d.trackingNumber}/>
      <Field label="Комментарий" value={d.comment}/>
      <Field label="Причина неудачи" value={d.failureReason}/>
    </Section>}
    {hasPermission('SALES_READ') && <Link to={'/sales/' + encodeURIComponent(d.saleId)} className="inline-block text-sm font-medium text-blue-700 underline">Открыть связанную продажу</Link>}
    {tab === 'actions' && <DeliveryActions key={d.id} delivery={d} onChanged={async updated => {
      queryClient.setQueryData(deliveryKeys.detail(id), updated);
      queryClient.setQueryData(deliveryKeys.bySale(updated.saleId), updated);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: deliveryKeys.lists }),
        queryClient.invalidateQueries({ queryKey: salesKeys.lists }),
        queryClient.invalidateQueries({ queryKey: salesKeys.kanban }),
        queryClient.invalidateQueries({ queryKey: salesKeys.detail(updated.saleId) }),
        queryClient.invalidateQueries({ queryKey: inventoryKeys.overviewPrefix }),
        queryClient.invalidateQueries({ queryKey: inventoryKeys.balancePrefix }),
        queryClient.invalidateQueries({ queryKey: inventoryKeys.movementsPrefix }),
      ]);
    }}/ >}
    {tab === 'history' && <DeliveryTimeline delivery={d}/>}
  </div>;
}
