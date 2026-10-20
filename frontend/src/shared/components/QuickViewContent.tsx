import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { useAuth } from '../../features/auth/AuthProvider';
import { customersApi, customerKeys } from '../../features/customers/api';
import { deliveryApi, deliveryKeys } from '../../features/deliveries/api';
import { salesApi, salesKeys } from '../../features/sales/api';
import { ErrorState } from './ErrorState';
import { LoadingState } from './LoadingState';

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
  const detail = useQuery({ queryKey: salesKeys.detail(id), queryFn: () => salesApi.get(id) });
  const history = useQuery({ queryKey: salesKeys.history(id, 0), queryFn: () => salesApi.history(id, 0) });
  if (detail.isPending) return <LoadingState label="Загружаем продажу…"/>;
  if (detail.isError) return <ErrorState error={detail.error} onRetry={() => void detail.refetch()}/>;
  const sale = detail.data;
  return <div className="space-y-5">
    <Section>
      <Field label="Заказ" value={sale.saleNumber}/>
      <Field label="Статус" value={sale.status}/>
      <Field label="Создан" value={new Date(sale.createdAt).toLocaleString('ru-RU')}/>
      <Field label="Сумма, сом" value={sale.total}/>
      <Field label="Тип получения" value={sale.fulfillmentType}/>
      <Field label="Комментарий" value={sale.comment}/>
    </Section>
    <section><h3 className="mb-2 font-semibold">Позиции заказа</h3>
      {sale.items.map((item, index) => <Section key={item.id}>
        <Field label={'Позиция ' + (index + 1)} value={item.quantity + ' шт.'}/>
        <Field label="Цена за единицу, сом" value={item.unitSalePrice}/>
        <Field label="Итого, сом" value={item.total}/>
      </Section>)}
    </section>
    <section><h3 className="mb-2 font-semibold">История</h3>
      {history.isPending ? <LoadingState label="Загружаем историю…"/> :
        history.isError ? <p role="status" className="text-sm text-slate-600">История недоступна.</p> :
        history.data.items.length ? <ul className="space-y-2 text-sm">{history.data.items.map((event, index) =>
          <li key={index} className="rounded-xl bg-slate-50 p-3">{event.action} · {event.actor} · {new Date(event.occurredAt).toLocaleString('ru-RU')}</li>)}</ul> :
        <p className="text-sm text-slate-500">Событий нет.</p>}
    </section>
  </div>;
}
export function CustomerQuickView({ id }: { id: string }) {
  const { hasPermission } = useAuth();
  const customer = useQuery({ queryKey: customerKeys.detail(id), queryFn: () => customersApi.get(id) });
  const history = useQuery({ queryKey: customerKeys.history(id, 0), queryFn: () => customersApi.history(id, 0),
    enabled: hasPermission('SALES_READ') });
  if (customer.isPending) return <LoadingState label="Загружаем клиента…"/>;
  if (customer.isError) return <ErrorState error={customer.error} onRetry={() => void customer.refetch()}/>;
  const c = customer.data;
  return <div className="space-y-5">
    <Section>
      <Field label="Имя" value={c.fullName}/>
      <Field label="Телефон" value={c.phone}/>
      <Field label="WhatsApp" value={c.whatsappPhone}/>
      <Field label="Дополнительный телефон" value={c.secondaryPhone}/>
      <Field label="Instagram" value={c.instagramUsername}/>
      <Field label="Регион" value={c.cityRegion}/>
      <Field label="Адрес" value={c.address}/>
      <Field label="Комментарий" value={c.comment}/>
    </Section>
    {hasPermission('SALES_READ') && <section><h3 className="mb-2 font-semibold">Заказы клиента</h3>
      {history.isPending ? <LoadingState label="Загружаем заказы…"/> :
        history.isError ? <p role="status">История недоступна.</p> :
        history.data.items.length ? <ul className="space-y-2">{history.data.items.map(s =>
          <li key={s.id}><Link className="text-blue-700 underline" to={'/sales/' + encodeURIComponent(s.id)}>{s.saleNumber}</Link> · {s.status}</li>)}</ul> :
        <p className="text-sm text-slate-500">Заказов нет.</p>}
    </section>}
    {hasPermission('SALES_CREATE') && <Link className="inline-flex rounded-xl bg-blue-600 px-4 py-3 text-sm font-semibold text-white"
      to={'/sales/new?customerId=' + encodeURIComponent(id)}>Новая продажа</Link>}
  </div>;
}
export function DeliveryQuickView({ id }: { id: string }) {
  const delivery = useQuery({ queryKey: deliveryKeys.detail(id), queryFn: () => deliveryApi.get(id) });
  if (delivery.isPending) return <LoadingState label="Загружаем доставку…"/>;
  if (delivery.isError) return <ErrorState error={delivery.error} onRetry={() => void delivery.refetch()}/>;
  const d = delivery.data;
  return <div className="space-y-5">
    <Section>
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
    </Section>
    <Link to={'/sales/' + encodeURIComponent(d.saleId)} className="inline-block text-sm font-medium text-blue-700 underline">
      Открыть связанную продажу
    </Link>
  </div>;
}
