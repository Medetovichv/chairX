import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useLocation, useParams } from 'react-router';
import { useAuth } from '../../auth/AuthProvider';
import { ApiClientError, userFacingApiError } from '../../../shared/api/api-error';
import { PageHeader } from '../../../shared/components/PageHeader';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState';
import { StatusBadge } from '../../../shared/components/StatusBadge';
import { Button } from '../../../shared/ui/button';
import { ConfirmDialog } from '../../../shared/ui/confirm-dialog';
import { ForbiddenPage } from '../../../pages/ForbiddenPage';
import { NotFoundPage } from '../../../pages/NotFoundPage';
import type { Customer } from '../types';
import { customerKeys, customersApi } from '../api';
import { customerDate, customerTitle } from '../validation';
import { CustomerForm } from '../components/CustomerForm';
import { CustomerOrderHistory } from '../components/CustomerOrderHistory';

function CopyContact({ label, value }: { label: string; value: string | null }) {
  const [message, setMessage] = useState('');
  if (!value) return <p className="text-sm text-slate-400">—</p>;
  async function copy() {
    try {
      await navigator.clipboard.writeText(value!);
      setMessage('Скопировано');
    } catch {
      setMessage('Не удалось скопировать. Выделите контакт вручную.');
    }
  }
  return <div className="flex flex-wrap items-center gap-2">
    <span className="break-all text-sm font-medium">{value}</span>
    <Button type="button" size="sm" variant="outline" aria-label={'Копировать ' + label}
      onClick={() => void copy()}>Копировать</Button>
    {message && <span role="status" className="text-xs text-slate-500">{message}</span>}
  </div>;
}

function CustomerRecord({ customer }: { customer: Customer }) {
  const { hasPermission } = useAuth();
  const canManage = hasPermission('CUSTOMERS_MANAGE');
  const canCreateSale = hasPermission('SALES_CREATE') && customer.active;
  const client = useQueryClient();
  const location = useLocation();
  const noticeState = (location.state as { notice?: unknown } | null)?.notice;
  const [notice, setNotice] = useState(typeof noticeState === 'string' ? noticeState : '');
  const [error, setError] = useState('');
  const [editing, setEditing] = useState(false);
  const [confirmDeactivation, setConfirmDeactivation] = useState(false);
  const update = useMutation({
    mutationFn: (data: Parameters<typeof customersApi.update>[1]) => customersApi.update(customer.id, data),
    onSuccess: async (saved) => {
      client.setQueryData(customerKeys.detail(customer.id), saved);
      await client.invalidateQueries({ queryKey: customerKeys.lists });
      setEditing(false); setError(''); setNotice('Данные клиента сохранены.');
    },
  });
  const status = useMutation({
    mutationFn: (active: boolean) => customersApi.setActive(customer.id, active),
    onSuccess: async (saved) => {
      client.setQueryData(customerKeys.detail(customer.id), saved);
      await client.invalidateQueries({ queryKey: customerKeys.lists });
      setConfirmDeactivation(false); setError('');
      setNotice(saved.active ? 'Клиент активирован.' : 'Клиент деактивирован.');
    },
    onError: (cause) => {
      setConfirmDeactivation(false); setNotice(''); setError(userFacingApiError(cause));
    },
  });
  function changeStatus() {
    setError(''); setNotice('');
    if (customer.active) setConfirmDeactivation(true);
    else status.mutate(true);
  }

  const lines: Array<[string, keyof Customer]> = [
    ['Имя', 'fullName'], ['Основной телефон', 'phone'],
    ['Дополнительный телефон', 'secondaryPhone'], ['WhatsApp', 'whatsappPhone'],
    ['Instagram', 'instagramUsername'], ['Город / регион', 'cityRegion'],
    ['Адрес', 'address'], ['Комментарий', 'comment'],
  ];

  return <div className="space-y-6">
    <Link className="inline-flex min-h-11 items-center font-semibold text-indigo-700" to="/customers">← Все клиенты</Link>
    <PageHeader title={customerTitle(customer)} description="Карточка клиента"
      action={canManage || canCreateSale ? <div className="flex flex-wrap gap-2">
        {canCreateSale && <Button asChild><Link to={'/sales/new?customerId='+encodeURIComponent(customer.id)}>Новая продажа</Link></Button>}
        {canManage && <>
          <Button variant="outline" disabled={editing || status.isPending} onClick={() => setEditing(true)}>Редактировать</Button>
          <Button variant={customer.active ? 'outline' : 'default'} disabled={status.isPending || editing}
            onClick={changeStatus}>{customer.active ? 'Деактивировать' : 'Активировать'}</Button>
        </>}
      </div> : undefined} />
    {notice && <p role="status" className="rounded-xl bg-emerald-50 p-3 text-sm text-emerald-800">{notice}</p>}
    {error && <p role="alert" className="rounded-xl bg-red-50 p-3 text-sm text-red-800">{error}</p>}
    {editing && canManage && <CustomerForm key={customer.updatedAt} initial={customer}
      onSave={async (data) => { await update.mutateAsync(data); }}
      onCancel={() => setEditing(false)} />}
    <section aria-label="Сведения о клиенте"
      className="rounded-2xl border border-slate-200 bg-white p-4 shadow-xs sm:p-6">
      <div className="mb-5 flex flex-wrap items-center gap-3">
        <h3 className="text-lg font-bold">Контактные данные</h3>
        <StatusBadge tone={customer.active ? 'success' : 'neutral'}>
          {customer.active ? 'Активен' : 'Неактивен'}
        </StatusBadge>
      </div>
      <dl className="grid gap-x-6 gap-y-5 sm:grid-cols-2">
        {lines.map(([label, key]) => <div key={key} className={key === 'comment' || key === 'address' ? 'sm:col-span-2' : ''}>
          <dt className="mb-1 text-xs font-semibold text-slate-500">{label}</dt>
          <dd className="min-w-0 whitespace-pre-wrap break-words">
            {key === 'phone' || key === 'whatsappPhone' ?
              <CopyContact label={label} value={customer[key]} /> :
              <span className="text-sm text-slate-800">{customer[key] || '—'}</span>}
          </dd>
        </div>)}
        <div><dt className="text-xs font-semibold text-slate-500">Дата создания</dt>
          <dd className="mt-1 text-sm">{customerDate(customer.createdAt)}</dd></div>
        <div><dt className="text-xs font-semibold text-slate-500">Дата изменения</dt>
          <dd className="mt-1 text-sm">{customerDate(customer.updatedAt)}</dd></div>
      </dl>
    </section>
    <CustomerOrderHistory customerId={customer.id} />
    <ConfirmDialog open={confirmDeactivation} onOpenChange={setConfirmDeactivation}
      title="Деактивировать клиента?" destructive confirmLabel="Деактивировать"
      description="Клиент останется в базе вместе с историей заказов. Это не удаляет персональные данные."
      busy={status.isPending}
      onConfirm={() => { if (!status.isPending) status.mutate(false); }} />
  </div>;
}

export function CustomerDetailPage() {
  const { hasPermission } = useAuth();
  const { customerId = '' } = useParams();
  const canRead = hasPermission('CUSTOMERS_READ');
  const valid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(customerId);
  const detail = useQuery({
    queryKey: customerKeys.detail(customerId),
    queryFn: () => customersApi.get(customerId),
    enabled: canRead && valid,
  });
  if (!canRead) return <ForbiddenPage />;
  if (!valid) return <NotFoundPage />;
  if (detail.isPending) return <LoadingState label="Загружаем карточку клиента…" />;
  if (detail.isError) {
    if (detail.error instanceof ApiClientError && detail.error.status === 403) return <ForbiddenPage />;
    if (detail.error instanceof ApiClientError && detail.error.status === 404) return <NotFoundPage />;
    return <ErrorState error={detail.error} onRetry={() => void detail.refetch()} />;
  }
  if (!detail.data) return <NotFoundPage />;
  return <CustomerRecord key={customerId} customer={detail.data} />;
}
