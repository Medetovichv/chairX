import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router';
import { ArrowLeft, Plus, Pencil, Power, Warehouse as WarehouseIcon, House, Boxes, MapPin, CircleCheck, CircleOff } from 'lucide-react';
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
function warehouseCount(n: number): string {
  const suffix = n % 100 >= 11 && n % 100 <= 14 ? 'складов' :
    n % 10 === 1 ? 'склад' : n % 10 >= 2 && n % 10 <= 4 ? 'склада' : 'складов';
  return n.toLocaleString('ru-RU')+' '+suffix;
}

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
  return <div className="space-y-5 text-[#182d38] sm:space-y-6">
    <PageHeader title="Управление складами"
      description="Создание, изменение и активация складов. Операции защищены разрешением WAREHOUSES_MANAGE." />
    <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-[#dfe7eb] bg-white p-3 sm:p-4">
      <Button asChild variant="outline" className="border-[#dfe7eb]"><Link to="/inventory"><ArrowLeft className="size-4" aria-hidden="true"/>К остаткам</Link></Button>
      {!creating && !editing && <Button onClick={startCreate} className="bg-[#182d38] text-white hover:bg-[#305063]">
        <Plus className="size-4" aria-hidden="true"/>Новый склад
      </Button>}
    </div>
    {notice && <p role="status" className="rounded-xl bg-emerald-50 p-3 text-sm text-emerald-800">{notice}</p>}
    {error && <p role="alert" className="rounded-xl bg-amber-50 p-3 text-sm text-amber-900">{error}</p>}

    {(creating || editing) && <section aria-label={creating ? 'Создание склада' : 'Редактирование склада'}
      className="space-y-4 rounded-2xl border border-[#dfe7eb] bg-white p-4 sm:p-5">
      <div className="flex items-center gap-3">
        <span className="flex size-10 items-center justify-center rounded-xl bg-[#e6efed] text-[#286e66]"><WarehouseIcon className="size-5" aria-hidden="true"/></span>
        <div>
          <h3 className="text-lg font-bold">{creating ? 'Новый склад' : 'Изменить склад'}</h3>
          <p className="text-xs text-[#516977]">Название и адрес склада; код задаётся при создании.</p>
        </div>
      </div>
      <div className="grid min-w-0 gap-4 sm:grid-cols-2">
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
      <div className="flex flex-wrap gap-2 border-t border-[#e7eeed] pt-4">
        <Button disabled={write.isPending} onClick={submit}>{write.isPending ? 'Сохраняем…' : 'Сохранить склад'}</Button>
        <Button disabled={write.isPending} variant="outline" onClick={closeEditor}>Отмена</Button>
      </div>
    </section>}

    {warehouses.isPending ? <LoadingState label="Загружаем склады…" /> :
      warehouses.isError ? <ErrorState error={warehouses.error} onRetry={()=>void warehouses.refetch()} /> :
      !warehouses.data?.length ? <EmptyState title="Складов пока нет" /> :
      <section aria-label="Список складов" className="space-y-3">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h2 className="text-lg font-bold tracking-tight text-[#182d38]">Все склады</h2>
          <p className="text-xs text-[#516977]">{warehouseCount(warehouses.data.length)} · управление доступностью и адресами</p>
        </div>
        <div className="overflow-hidden rounded-2xl border border-[#dfe7eb] bg-white">
          <div className="divide-y divide-[#e7eeed]">
            {warehouses.data.map(w => {
              const Icon = w.code === 'HOME' ? House : w.code === 'OFFICE' ? WarehouseIcon : Boxes;
              return <article key={w.id} className="flex min-w-0 flex-wrap items-center gap-3 p-4 transition-colors hover:bg-[#f8fafb] sm:gap-4 sm:px-5">
                <span className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-[#e6efed] text-[#286e66]">
                  <Icon className="size-5" aria-hidden="true"/>
                </span>
                <div className="min-w-0 basis-[11rem] flex-1 space-y-1">
                  <strong className="block break-words text-sm font-bold text-[#182d38]">{w.name}</strong>
                  <p className="flex items-start gap-1.5 break-words text-xs text-[#516977]">
                    <MapPin className="mt-0.5 size-3.5 shrink-0" aria-hidden="true"/>{w.address || 'Адрес не указан'}
                  </p>
                </div>
                <span className={'inline-flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-xs font-semibold ' +
                  (w.active ? 'bg-[#e6efed] text-[#285f58]' : 'bg-[#f2f6f8] text-[#516977]')}>
                  {w.active ? <CircleCheck className="size-3.5" aria-hidden="true"/> : <CircleOff className="size-3.5" aria-hidden="true"/>}
                  {w.active ? 'Активен' : 'Неактивен'}
                </span>
                <div className="flex w-full flex-wrap gap-2 sm:w-auto">
                  <Button size="sm" variant="outline" className="border-[#dfe7eb]"
                    disabled={write.isPending || !!editing || creating}
                    onClick={()=>startEdit(w)}><Pencil className="size-4" aria-hidden="true"/>Изменить</Button>
                  <Button size="sm" variant="outline" className="border-[#dfe7eb]"
                    disabled={write.isPending || !!editing || creating}
                    onClick={()=>{setNotice('');setError('');setTarget({warehouse:w,active:!w.active});}}>
                    <Power className="size-4" aria-hidden="true"/>{w.active ? 'Деактивировать' : 'Активировать'}
                  </Button>
                </div>
              </article>;
            })}
          </div>
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
