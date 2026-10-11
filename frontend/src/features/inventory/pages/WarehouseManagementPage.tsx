import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router';
import { ArrowLeft, Plus, Pencil, Power, Warehouse as WarehouseIcon } from 'lucide-react';
import { useAuth } from '../../auth/AuthProvider';
import { PageHeader } from '../../../shared/components/PageHeader';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState';
import { EmptyState } from '../../../shared/components/EmptyState';
import { Button } from '../../../shared/ui/button';
import { Input } from '../../../shared/ui/input';
import { ConfirmDialog } from '../../../shared/ui/confirm-dialog';
import { userFacingApiError } from '../../../shared/api/api-error';
import { inventoryApi, inventoryKeys } from '../api';
import type { Warehouse, WarehouseCreateInput, WarehouseUpdateInput } from '../types';

type Target = { warehouse: Warehouse; active: boolean } | null;
const clean = (v: string) => v.trim();
function validName(value: string): boolean { return clean(value).length > 0 && clean(value).length <= 200; }
function validAddress(value: string): boolean { return value.trim().length <= 1000; }
const isCodeValid = (code: string): boolean => /^[A-Z0-9][A-Z0-9_-]{0,49}$/.test(code);

export function WarehouseManagementPage() {
  const { hasPermission } = useAuth();
  const canRead = hasPermission('INVENTORY_READ');
  const canManage = hasPermission('WAREHOUSES_MANAGE');
  const client = useQueryClient();
  const warehouses = useQuery({
    queryKey: inventoryKeys.warehouses, queryFn: inventoryApi.warehouses,
    enabled: canRead && canManage,
  });
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<Warehouse | null>(null);
  const [name, setName] = useState('');
  const [code, setCode] = useState('');
  const [address, setAddress] = useState('');
  const [target, setTarget] = useState<Target>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const refresh = async () => {
    await Promise.all([
      client.invalidateQueries({ queryKey: inventoryKeys.warehouses }),
      client.invalidateQueries({ queryKey: inventoryKeys.warehouseSummary }),
      client.invalidateQueries({ queryKey: inventoryKeys.overviewPrefix }),
    ]);
  };
  const write = useMutation({
    mutationFn: async (input: { kind: 'create'; value: WarehouseCreateInput } |
      { kind: 'update'; id: string; value: WarehouseUpdateInput } |
      { kind: 'toggle'; id: string; active: boolean }) => {
      if (input.kind === 'create') return inventoryApi.createWarehouse(input.value);
      if (input.kind === 'update') return inventoryApi.updateWarehouse(input.id, input.value);
      return input.active ? inventoryApi.activateWarehouse(input.id) :
        inventoryApi.deactivateWarehouse(input.id);
    },
    onSuccess: async () => {
      setError('');
      setNotice('Изменения склада сохранены.');
      setCreating(false); setEditing(null); setTarget(null);
      await refresh();
    },
    onError: (err) => {
      setError(userFacingApiError(err));
      setTarget(null);
    },
  });

  const closeEditor = () => {
    if (write.isPending) return;
    setEditing(null); setCreating(false); setError('');
  };
  const startEdit = (w: Warehouse) => {
    setEditing(w); setCreating(false);
    setName(w.name); setCode(w.code); setAddress(w.address ?? ''); setNotice(''); setError('');
  };
  const startCreate = () => {
    setEditing(null); setCreating(true);
    setName(''); setCode(''); setAddress(''); setNotice(''); setError('');
  };
  const submit = () => {
    setError(''); setNotice('');
    if (!validName(name)) { setError('Укажите название склада (до 200 символов).'); return; }
    if (!validAddress(address)) { setError('Адрес не должен превышать 1000 символов.'); return; }
    if (creating && !isCodeValid(code)) {
      setError('Код: заглавные латинские буквы, цифры, дефис или подчёркивание (до 50 символов).'); return;
    }
    if (creating) {
      write.mutate({ kind: 'create', value: {name: clean(name), code, address: clean(address)||null} });
    } else if (editing) {
      write.mutate({ kind: 'update', id: editing.id, value: { name: clean(name), address: clean(address)||null } });
    }
  };

  if (!canRead || !canManage) {
    return <div role="alert" className="rounded-2xl border border-amber-200 bg-amber-50 p-5 text-amber-900">
      Нет прав на управление складами.
    </div>;
  }
  return <div className="space-y-5">
    <PageHeader title="Управление складами"
      description="Создание, изменение и активация складов. Операции защищены разрешением WAREHOUSES_MANAGE." />
    <div className="flex flex-wrap items-center justify-between gap-3">
      <Button asChild variant="outline"><Link to="/inventory"><ArrowLeft className="size-4" />К остаткам</Link></Button>
      {!creating && !editing && <Button onClick={startCreate}>
        <Plus className="size-4" />Новый склад
      </Button>}
    </div>
    {notice && <p role="status" className="rounded-xl bg-emerald-50 p-3 text-sm text-emerald-800">{notice}</p>}
    {error && <p role="alert" className="rounded-xl bg-amber-50 p-3 text-sm text-amber-900">{error}</p>}

    {(creating || editing) && <section aria-label={creating ? 'Создание склада' : 'Редактирование склада'}
      className="space-y-4 rounded-2xl border border-blue-200 bg-white p-5 shadow-sm">
      <h3 className="text-lg font-bold">{creating ? 'Новый склад' : 'Изменить склад'}</h3>
      <div className="grid gap-4 sm:grid-cols-2">
        <label className="space-y-1 text-sm font-medium">Название
          <Input aria-label="Название склада" maxLength={200} value={name} disabled={write.isPending}
            onChange={e=>setName(e.target.value)} placeholder="Например, Офисный" />
        </label>
        <label className="space-y-1 text-sm font-medium">Код склада
          <Input aria-label="Код склада" value={code} maxLength={50} disabled={!creating || write.isPending}
            onChange={e=>setCode(e.target.value.toUpperCase())}
            placeholder="OFFICE_2" />
          {!creating && <span className="block text-xs text-slate-500">Код не меняется после создания.</span>}
        </label>
      </div>
      <label className="block space-y-1 text-sm font-medium">Адрес (необязательно)
        <Input aria-label="Адрес склада" value={address} maxLength={1000} disabled={write.isPending}
          onChange={e=>setAddress(e.target.value)} placeholder="Адрес помещения" />
      </label>
      <div className="flex flex-wrap gap-2">
        <Button disabled={write.isPending} onClick={submit}>{write.isPending ? 'Сохраняем…' : 'Сохранить склад'}</Button>
        <Button disabled={write.isPending} variant="outline" onClick={closeEditor}>Отмена</Button>
      </div>
    </section>}

    {warehouses.isPending ? <LoadingState label="Загружаем склады…" /> :
      warehouses.isError ? <ErrorState error={warehouses.error} onRetry={()=>void warehouses.refetch()} /> :
      !warehouses.data?.length ? <EmptyState title="Складов пока нет" /> :
      <section aria-label="Список складов" className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="divide-y divide-slate-100">
          {warehouses.data.map(w =>
            <article key={w.id} className="flex flex-wrap items-center gap-3 p-4">
              <span className="flex size-10 items-center justify-center rounded-xl bg-blue-50 text-blue-600">
                <WarehouseIcon className="size-5" aria-hidden="true" />
              </span>
              <div className="min-w-44 flex-1">
                <strong className="text-slate-950">{w.name}</strong>
                <p className="text-xs text-slate-500">{w.address || 'Адрес не указан'}</p>
              </div>
              <span className={'rounded-full px-2 py-1 text-xs font-semibold ' +
                (w.active ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-700')}>
                {w.active ? 'Активен' : 'Неактивен'}</span>
              <Button size="sm" variant="outline" disabled={write.isPending || !!editing || creating}
                onClick={()=>startEdit(w)}><Pencil className="size-4" />Изменить</Button>
              <Button size="sm" variant="outline" disabled={write.isPending || !!editing || creating}
                onClick={()=>{setNotice('');setError('');setTarget({warehouse:w,active:!w.active});}}>
                <Power className="size-4" />{w.active ? 'Деактивировать' : 'Активировать'}
              </Button>
            </article>)}
        </div>
      </section>}
    <ConfirmDialog open={!!target} onOpenChange={open=>{if(!open&&!write.isPending)setTarget(null);}}
      title={target?.active ? 'Активировать склад?' : 'Деактивировать склад?'}
      description={target ? target.warehouse.name + '. '+(target.active
        ? 'Склад снова будет доступен для операций.'
        : 'При деактивации склад перестанет быть доступным для новых перемещений. Исторические остатки сохранятся.') : ''}
      confirmLabel="Подтвердить" busy={write.isPending} destructive={target?.active===false}
      onConfirm={()=>{if(target)write.mutate({kind:'toggle',id:target.warehouse.id,active:target.active});}} />
  </div>;
}
