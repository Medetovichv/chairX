import { useState } from 'react';
import { Plus, Phone, MessageSquareText } from 'lucide-react';
import { SuppliersWorkspaceOverview } from '../components/SuppliersWorkspaceOverview';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { ApiClientError, userFacingApiError } from '../../../shared/api/api-error';
import { PageHeader } from '../../../shared/components/PageHeader';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState';
import { EmptyState } from '../../../shared/components/EmptyState';
import { StatusBadge } from '../../../shared/components/StatusBadge';
import { ForbiddenPage } from '../../../pages/ForbiddenPage';
import { Button } from '../../../shared/ui/button';
import { ConfirmDialog } from '../../../shared/ui/confirm-dialog';
import { purchaseKeys, supplierApi } from '../api';
import type { Supplier } from '../types';
import { SupplierForm } from '../components/SupplierForm';
import { PurchasePager } from '../components/PurchasePager';
import { PurchaseTabs } from '../components/PurchaseTabs';

export function SuppliersPage() {
  const { hasPermission } = useAuth();
  const canRead = hasPermission('SUPPLIERS_READ');
  const canManage = hasPermission('SUPPLIERS_MANAGE');
  const client = useQueryClient();
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<Supplier | null>(null);
  const [creating, setCreating] = useState(false);
  const [deactivating, setDeactivating] = useState<Supplier | null>(null);
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');
  const listing = useQuery({ queryKey: purchaseKeys.supplierPage(page),
    queryFn: () => supplierApi.page(page), enabled: canRead });
  async function changed() {
    await client.invalidateQueries({ queryKey: purchaseKeys.suppliers });
    setEditing(null); setCreating(false);
  }
  const create = useMutation({ mutationFn: supplierApi.create,
    onSuccess: async () => { await changed(); setNotice('Поставщик добавлен.'); } });
  const update = useMutation({ mutationFn: (payload: { id:string; data: Parameters<typeof supplierApi.update>[1] }) =>
    supplierApi.update(payload.id,payload.data),
    onSuccess: async () => { await changed(); setNotice('Поставщик обновлён.'); } });
  const active = useMutation({ mutationFn: (payload: { id:string; value:boolean }) =>
    supplierApi.active(payload.id,payload.value),
    onSuccess: async (saved) => { await changed(); setDeactivating(null); setError('');
      setNotice(saved.active ? 'Поставщик активирован.' : 'Поставщик деактивирован.'); },
    onError: cause => {setDeactivating(null);setError(userFacingApiError(cause));} });
  if (!canRead || listing.error instanceof ApiClientError && listing.error.status===403) return <ForbiddenPage />;
  return <div className="space-y-5 text-[#182d38] sm:space-y-6">
    <PageHeader title="Поставщики" description="Поставщики закупок; неактивные остаются в истории."
      action={canManage ? <Button className="min-h-11 rounded-xl bg-[#172f3c] text-white hover:bg-[#305063]" disabled={creating || !!editing} onClick={() => {setCreating(true);setNotice('');}}>
        <Plus className="size-4" aria-hidden="true"/>Добавить поставщика</Button> : undefined} />
    <PurchaseTabs />
    {listing.isSuccess && <SuppliersWorkspaceOverview suppliers={listing.data.items} total={listing.data.totalElements} page={listing.data.page}/>}
    {notice && <p role="status" className="rounded-xl bg-emerald-50 p-3 text-sm text-emerald-800">{notice}</p>}
    {error && <p role="alert" className="rounded-xl bg-red-50 p-3 text-sm text-red-700">{error}</p>}
    {creating && canManage && <SupplierForm onCancel={() => setCreating(false)}
      onSave={async data => { await create.mutateAsync(data); }} />}
    {editing && canManage && <SupplierForm key={editing.id} initial={editing} onCancel={() => setEditing(null)}
      onSave={async data => { await update.mutateAsync({id:editing.id,data}); }} />}
    {listing.isPending ? <LoadingState label="Загружаем поставщиков…" /> :
      listing.isError ? <ErrorState error={listing.error} onRetry={() => void listing.refetch()} /> :
      !listing.data?.items.length ? <EmptyState title="Поставщиков пока нет" description="Добавьте первого поставщика." /> :
      <section aria-label="Список поставщиков" className="overflow-hidden rounded-2xl border border-[#dfe7eb] bg-white">
        <div className="divide-y divide-[#eaf0f0]">{listing.data.items.map(s =>
          <article key={s.id} className="flex flex-wrap items-start justify-between gap-4 p-5 transition-colors hover:bg-[#f5faf8]">
            <div className="min-w-0 flex-1 space-y-1">
              <h3 className="break-words text-base font-bold text-[#182d38]">{s.name}</h3>
              <p className="flex items-start gap-2 whitespace-pre-wrap break-words text-sm text-[#405b65]"><Phone className="mt-0.5 size-4 shrink-0 text-[#286e66]" aria-hidden="true"/>{s.contactInformation || 'Контакт не указан'}</p>
              {s.comment && <p className="flex items-start gap-2 whitespace-pre-wrap break-words text-sm text-[#516977]"><MessageSquareText className="mt-0.5 size-4 shrink-0" aria-hidden="true"/>{s.comment}</p>}
              <StatusBadge tone={s.active ? 'success':'neutral'}>{s.active?'Активен':'Неактивен'}</StatusBadge>
            </div>
            {canManage && <div className="flex flex-wrap gap-2 sm:justify-end">
              <Button type="button" variant="outline" disabled={!!editing || creating || active.isPending}
                onClick={() => {setEditing(s);setCreating(false);setNotice('');}}>Изменить {s.name}</Button>
              <Button type="button" variant="outline" disabled={active.isPending}
                onClick={() => s.active ? setDeactivating(s) : active.mutate({id:s.id,value:true})}>
                {s.active?'Деактивировать':'Активировать'} {s.name}</Button>
            </div>}
          </article>)}</div>
        <PurchasePager page={listing.data.page} total={listing.data.totalElements}
          size={listing.data.size} label="поставщиков" onPage={setPage} />
      </section>}
    <ConfirmDialog open={!!deactivating} onOpenChange={open => {if(!open)setDeactivating(null);}}
      title="Деактивировать поставщика?" destructive busy={active.isPending} confirmLabel="Деактивировать"
      description="Поставщик останется в исторических закупках; для новых закупок он станет недоступен."
      onConfirm={() => {if(deactivating&&!active.isPending)active.mutate({id:deactivating.id,value:false});}} />
  </div>;
}
