import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router';
import { useAuth } from '../../auth/AuthProvider';
import { ApiClientError } from '../../../shared/api/api-error';
import { PageHeader } from '../../../shared/components/PageHeader';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState';
import { EmptyState } from '../../../shared/components/EmptyState';
import { ForbiddenPage } from '../../../pages/ForbiddenPage';
import { Button } from '../../../shared/ui/button';
import { Input } from '../../../shared/ui/input';
import { purchaseApi, purchaseKeys, supplierApi } from '../api';
import { formatDate, formatMoney, statuses, statusNames } from '../validation';
import type { PurchaseFilters, PurchaseLine, PurchaseStatus } from '../types';
import { PurchaseForm } from '../components/PurchaseForm';
import { PurchaseTabs } from '../components/PurchaseTabs';
import { PurchasePager } from '../components/PurchasePager';
import { PurchaseStatusBadge } from '../components/PurchaseStatusBadge';

const initial: PurchaseFilters = { status:'', supplierId:'', from:'', to:'', number:'', page:0 };
function PurchaseTable({ lines }: { lines: PurchaseLine[] }) {
  return <section aria-label="Список закупок" className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-xs">
    <div className="hidden overflow-x-auto xl:block"><table className="w-full text-left text-sm">
      <thead className="bg-slate-50"><tr>{['Закупка','Поставщик','Дата','Статус','Позиций','Заказано','Принято','Осталось','Товары','Карго','Итого'].map(col =>
        <th scope="col" className="whitespace-nowrap px-3 py-3 font-semibold text-slate-600" key={col}>{col}</th>)}</tr></thead>
      <tbody className="divide-y divide-slate-100">{lines.map(line => <tr key={line.id}>
        <td className="px-3 py-3"><Link className="inline-flex min-h-11 items-center font-semibold text-indigo-700 hover:underline"
          to={'/purchases/'+encodeURIComponent(line.id)}>{formatDate(line.createdAt)}</Link></td>
        <td className="px-3 py-3">{line.supplierName}</td><td className="px-3 py-3">{formatDate(line.createdAt)}</td>
        <td className="px-3 py-3"><PurchaseStatusBadge status={line.status} /></td>
        <td className="px-3 py-3">{line.itemCount}</td><td className="px-3 py-3">{line.orderedQuantity}</td>
        <td className="px-3 py-3">{line.receivedQuantity}</td><td className="px-3 py-3">{line.remainingQuantity}</td>
        <td className="px-3 py-3">{formatMoney(line.goodsCost)}</td><td className="px-3 py-3">{formatMoney(line.cargoCost)}</td>
        <td className="px-3 py-3 font-bold">{formatMoney(line.totalCost)}</td>
      </tr>)}</tbody>
    </table></div>
    <div className="divide-y divide-slate-100 xl:hidden">{lines.map(line => <article key={line.id}
      className="space-y-3 p-4 text-sm">
      <div className="flex flex-wrap justify-between gap-2">
        <Link className="inline-flex min-h-11 items-center font-semibold text-indigo-700" to={'/purchases/'+encodeURIComponent(line.id)}>
          Закупка от {formatDate(line.createdAt)}</Link><PurchaseStatusBadge status={line.status} />
      </div>
      <p className="font-semibold">{line.supplierName} · {formatDate(line.createdAt)}</p>
      <p>Позиций: {line.itemCount} · Заказано: {line.orderedQuantity} · Принято: {line.receivedQuantity} · Осталось: {line.remainingQuantity}</p>
      <p>Товары: {formatMoney(line.goodsCost)} · Карго: {formatMoney(line.cargoCost)}</p>
      <p className="font-bold">Итого: {formatMoney(line.totalCost)}</p>
    </article>)}</div>
  </section>;
}
export function PurchasesPage() {
  const { hasPermission } = useAuth();
  const allowed = hasPermission('PURCHASE_READ');
  const canCreate = hasPermission('PURCHASE_CREATE');
  const [filters, setFilters] = useState<PurchaseFilters>(initial);
  const [creating, setCreating] = useState(false);
  const [notice, setNotice] = useState('');
  const navigate = useNavigate();
  const client = useQueryClient();
  function change<K extends keyof PurchaseFilters>(field: K, value: PurchaseFilters[K]) {
    setFilters(f => ({ ...f, [field]: value, page:0 }));
  }
  const overview = useQuery({
    queryKey: purchaseKeys.overview(filters), queryFn: () => purchaseApi.overview(filters), enabled: allowed && !creating,
  });
  const suppliers = useQuery({
    queryKey: purchaseKeys.supplierAll, queryFn: supplierApi.all,
    enabled: allowed && hasPermission('SUPPLIERS_READ'),
  });
  const create = useMutation({ mutationFn: purchaseApi.create, onSuccess: async purchase => {
    await client.invalidateQueries({ queryKey: purchaseKeys.overviews });
    setCreating(false); setNotice('');
    navigate('/purchases/'+encodeURIComponent(purchase.id), { state:{notice:'Закупка создана как черновик.'} });
  } });
  if (!allowed || overview.error instanceof ApiClientError && overview.error.status===403) return <ForbiddenPage />;
  return <div className="space-y-6">
    <PageHeader title={creating ? 'Новая закупка' : 'Закупки'}
      action={canCreate && !creating ? <Button onClick={() => setCreating(true)}>+ Новая закупка</Button> : undefined} />
    {!creating && <PurchaseTabs />}
    {notice && <p role="status">{notice}</p>}
    {creating && canCreate && <PurchaseForm onCancel={() => setCreating(false)}
      onSave={async value => { await create.mutateAsync(value); }} />}
    {!creating && <><section aria-label="Фильтры закупок" className="space-y-4 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-3"><div><h2 className="font-bold text-slate-900">Найти закупку</h2><p className="text-xs text-slate-500">Выбирайте статус, поставщика или период. Технические идентификаторы не требуются.</p></div><Button variant="outline" onClick={()=>setFilters(initial)}>Сбросить фильтры</Button></div>
      <div aria-label="Быстрые статусы закупок" className="flex flex-wrap gap-2">{([["","Все"],["DRAFT","Черновики"],["CONFIRMED","Ожидают поступления"],["PARTIALLY_RECEIVED","Частично получены"],["RECEIVED","Получены"]] as const).map(([status,label])=><Button key={label} variant={filters.status===status?"default":"outline"} size="sm" onClick={()=>change("status",status)}>{label}</Button>)}</div>
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
        <label className="space-y-1 text-sm font-semibold">Статус
          <select aria-label="Фильтр статуса закупки" value={filters.status}
            onChange={e => change('status', e.target.value as PurchaseStatus | '')}
            className="min-h-11 w-full rounded-xl border border-slate-200 bg-white px-3">
            <option value="">Все статусы</option>{statuses.map(s => <option key={s} value={s}>{statusNames[s]}</option>)}
          </select>
        </label>
        {hasPermission('SUPPLIERS_READ') && <label className="space-y-1 text-sm font-semibold">Поставщик
          <select aria-label="Фильтр поставщика" value={filters.supplierId}
            disabled={suppliers.isPending || suppliers.isError}
            onChange={e => change('supplierId',e.target.value)}
            className="min-h-11 w-full rounded-xl border border-slate-200 bg-white px-3">
            <option value="">Все поставщики</option>{suppliers.data?.map(s =>
              <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
        </label>}
        <label className="space-y-1 text-sm font-semibold">С даты
          <Input type="date" aria-label="Период с" value={filters.from}
            onChange={e => change('from',e.target.value)} />
        </label>
        <label className="space-y-1 text-sm font-semibold">По дату
          <Input type="date" aria-label="Период по" value={filters.to}
            onChange={e => change('to',e.target.value)} />
        </label>
      </div>
      {filters.from && filters.to && filters.from>filters.to &&
        <p role="alert" className="mt-3 text-sm text-red-700">Начало периода позже его окончания.</p>}
      {suppliers.isError && hasPermission('SUPPLIERS_READ') &&
        <p className="mt-2 text-sm text-amber-700">Не удалось загрузить фильтр поставщиков. Список закупок остаётся доступен.</p>}
    </section>
    {filters.from && filters.to && filters.from>filters.to ?
      <EmptyState title="Неверный период" description="Исправьте даты, чтобы увидеть список." /> :
      overview.isPending ? <LoadingState label="Загружаем закупки…" /> :
      overview.isError ? <ErrorState error={overview.error} onRetry={() => void overview.refetch()} /> :
      !overview.data?.items.length ? <EmptyState title="Закупки не найдены" description="Измените фильтры или добавьте первую закупку." /> :
      <div><PurchaseTable lines={overview.data.items} />
        <PurchasePager page={overview.data.page} total={overview.data.total} size={overview.data.size}
          label="закупок" onPage={page => setFilters(old => ({...old,page}))} /></div>}</>}
  </div>;
}
