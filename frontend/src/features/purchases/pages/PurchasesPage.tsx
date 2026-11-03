import { useState } from 'react';
import { Plus, Search, ArrowUpRight, Package, Truck, CalendarDays } from 'lucide-react';
import { PurchasesWorkspaceOverview } from '../components/PurchasesWorkspaceOverview';
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

function lastSevenDays(): {from:string;to:string} {
  const parts=new Intl.DateTimeFormat('en-US',{timeZone:'Asia/Bishkek',
    year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date());
  const fields=Object.fromEntries(parts.map(p=>[p.type,p.value]));
  const today=new Date(Date.UTC(Number(fields.year),Number(fields.month)-1,Number(fields.day),12));
  const from=new Date(today);from.setUTCDate(from.getUTCDate()-6);
  return {from:from.toISOString().slice(0,10),to:today.toISOString().slice(0,10)};
}
function initialFilters():PurchaseFilters {
  return {status:'',supplierId:'',...lastSevenDays(),number:'',page:0};
}
function PurchaseTable({ lines }: { lines: PurchaseLine[] }) {
  const detail = (id:string) => '/purchases/' + encodeURIComponent(id);
  return <section aria-label="Список закупок" className="overflow-hidden rounded-2xl border border-[#dfe7eb] bg-white">
    <div className="hidden overflow-x-auto xl:block"><table className="w-full text-left text-sm">
      <thead className="bg-[#f5f9f7] text-[#516977]"><tr>
        {['Закупка и дата', 'Поставщик', 'Статус', 'Поступление', 'Сумма закупки'].map(col=>
          <th key={col} scope="col" className="whitespace-nowrap px-5 py-4 text-xs font-bold">{col}</th>)}
      </tr></thead>
      <tbody className="divide-y divide-[#eaf0f0]">{lines.map(line=>
        <tr key={line.id} className="entity-clickable-row group transition-colors hover:bg-[#f5faf8]">
          <td className="px-5 py-4 align-top">
            <Link className="entity-stretched-link font-bold text-[#236e66] hover:underline focus-visible:outline-2 focus-visible:outline-[#267f79]"
              to={detail(line.id)}>Закупка от {formatDate(line.createdAt)}</Link>
            <p className="mt-1 text-xs text-[#516977]">{line.itemCount} позиций</p>
          </td>
          <td className="max-w-56 break-words px-5 py-4 align-top font-semibold text-[#263e49]">{line.supplierName}</td>
          <td className="px-5 py-4 align-top"><PurchaseStatusBadge status={line.status}/></td>
          <td className="px-5 py-4 align-top">
            <p className="font-semibold tabular-nums text-[#263e49]">{line.receivedQuantity} из {line.orderedQuantity} шт.</p>
            <p className="mt-1 text-xs text-[#516977]">Осталось: {line.remainingQuantity} шт.</p>
            {line.lastReceiptAt && <p className="mt-1 text-xs text-[#516977]">Последняя приёмка: {formatDate(line.lastReceiptAt)}</p>}
          </td>
          <td className="whitespace-nowrap px-5 py-4 align-top">
            <strong className="font-bold tabular-nums text-[#182d38]">{formatMoney(line.totalCost)}</strong>
            <p className="mt-1 text-xs text-[#516977]">Товары: {formatMoney(line.goodsCost)}</p>
            <p className="text-xs text-[#516977]">Карго: {formatMoney(line.cargoCost)}</p>
          </td>
        </tr>)}
      </tbody>
    </table></div>
    <div className="divide-y divide-[#eaf0f0] xl:hidden">{lines.map(line=>
      <article key={line.id} className="entity-clickable-row group space-y-4 p-4 transition-colors hover:bg-[#f5faf8] sm:p-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <Link className="entity-stretched-link inline-flex items-center gap-1.5 font-bold text-[#236e66] hover:underline"
              to={detail(line.id)}>
              Закупка от {formatDate(line.createdAt)}<ArrowUpRight className="size-4 shrink-0" aria-hidden="true"/>
            </Link>
            <p className="mt-1 flex items-center gap-1.5 text-xs text-[#516977]">
              <CalendarDays className="size-4" aria-hidden="true"/>{line.itemCount} позиций
            </p>
          </div>
          <PurchaseStatusBadge status={line.status}/>
        </div>
        <p className="break-words font-semibold text-[#263e49]">{line.supplierName}</p>
        <div className="grid gap-3 rounded-xl bg-[#f2f7f6] p-4 sm:grid-cols-2">
          <div className="flex items-start gap-2">
            <Package className="mt-0.5 size-4 shrink-0 text-[#286e66]" aria-hidden="true"/>
            <div>
              <p className="text-xs text-[#516977]">Поступило</p>
              <p className="font-bold tabular-nums text-[#182d38]">{line.receivedQuantity} / {line.orderedQuantity} шт.</p>
            </div>
          </div>
          <div className="flex items-start gap-2">
            <Truck className="mt-0.5 size-4 shrink-0 text-[#286e66]" aria-hidden="true"/>
            <div>
              <p className="text-xs text-[#516977]">Осталось принять</p>
              <p className="font-bold tabular-nums text-[#182d38]">{line.remainingQuantity} шт.</p>
            </div>
          </div>
        </div>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p className="text-sm text-[#516977]">Товары: {formatMoney(line.goodsCost)} · Карго: {formatMoney(line.cargoCost)}</p>
          <strong className="text-lg font-bold tabular-nums text-[#182d38]">{formatMoney(line.totalCost)}</strong>
        </div>
        {line.lastReceiptAt && <p className="text-xs text-[#516977]">Последнее поступление: {formatDate(line.lastReceiptAt)}</p>}
      </article>)}</div>
  </section>;
}
export function PurchasesPage() {
  const { hasPermission } = useAuth();
  const allowed = hasPermission('PURCHASE_READ');
  const canCreate = hasPermission('PURCHASE_CREATE');
  const [filters, setFilters] = useState<PurchaseFilters>(initialFilters);
  const [includeOpenOlder,setIncludeOpenOlder]=useState(true);
  const [creating, setCreating] = useState(false);
  const [notice, setNotice] = useState('');
  const navigate = useNavigate();
  const client = useQueryClient();
  function change<K extends keyof PurchaseFilters>(field: K, value: PurchaseFilters[K]) {
    if(field==='from'||field==='to')setIncludeOpenOlder(false);
    setFilters(f => ({ ...f, [field]: value, page:0 }));
  }
  const overview = useQuery({
    queryKey: [...purchaseKeys.overview(filters),includeOpenOlder], queryFn: () => purchaseApi.overview(filters,includeOpenOlder), enabled: allowed && !creating,
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
  return <div className="space-y-5 text-[#182d38] sm:space-y-6">
    <PageHeader title={creating ? 'Новая закупка' : 'Закупки'} description="Заказы поставщикам, приёмка товаров и контроль оставшихся количеств."
      action={canCreate && !creating ? <Button className="min-h-11 rounded-xl bg-[#172f3c] px-5 text-white hover:bg-[#305063]" onClick={() => setCreating(true)}><Plus className="size-4" aria-hidden="true"/>Новая закупка</Button> : undefined} />
    {!creating && <PurchaseTabs />}
    {!creating && overview.isSuccess && <PurchasesWorkspaceOverview lines={overview.data.items} total={overview.data.total} page={overview.data.page}/>}
    {notice && <p role="status">{notice}</p>}
    {creating && canCreate && <PurchaseForm onCancel={() => setCreating(false)}
      onSave={async value => { await create.mutateAsync(value); }} />}
    {!creating && <><section aria-label="Фильтры закупок" className="space-y-4 rounded-2xl border border-[#dfe7eb] bg-white p-4 sm:p-6">
      <div className="flex flex-wrap items-center justify-between gap-3"><div><h2 className="flex items-center gap-2 font-bold text-[#182d38]"><Search className="size-4 text-[#267f79]" aria-hidden="true"/>Поиск и фильтры</h2><p className="text-xs text-slate-500">Выбирайте статус, поставщика или период. Технические идентификаторы не требуются.</p></div><Button variant="outline" onClick={()=>{setFilters(initialFilters());setIncludeOpenOlder(true);}}>Сбросить фильтры</Button></div>
      <div className="flex flex-wrap gap-2">
        <Button variant="outline" size="sm" className="h-auto min-h-11 w-full max-w-full whitespace-normal py-2 text-center leading-snug sm:w-auto" onClick={()=>{setFilters(old=>({...old,...lastSevenDays(),page:0}));setIncludeOpenOlder(true);}}>Последние 7 дней + незавершённые</Button>
        <Button variant="outline" size="sm" onClick={()=>{setFilters(old=>({...old,from:'',to:'',page:0}));setIncludeOpenOlder(false);}}>Все даты</Button>
      </div>
      <div aria-label="Быстрые статусы закупок" className="flex flex-wrap gap-2">{([["","Все"],["DRAFT","Черновики"],["CONFIRMED","Ожидают поступления"],["PARTIALLY_RECEIVED","Частично получены"],["RECEIVED","Получены"]] as const).map(([status,label])=><Button key={label} className={filters.status===status?"min-h-11 rounded-xl bg-[#172f3c] text-white hover:bg-[#305063]":"min-h-11 rounded-xl"} variant={filters.status===status?"default":"outline"} size="sm" onClick={()=>change("status",status)}>{label}</Button>)}</div>
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
        <label className="space-y-1 text-sm font-semibold">Статус
          <select aria-label="Фильтр статуса закупки" value={filters.status}
            onChange={e => change('status', e.target.value as PurchaseStatus | '')}
            className="min-h-11 w-full rounded-xl border border-[#dce6eb] bg-[#f8fafb] px-3 text-[#182d38] focus-visible:outline-2 focus-visible:outline-[#416c82]">
            <option value="">Все статусы</option>{statuses.map(s => <option key={s} value={s}>{statusNames[s]}</option>)}
          </select>
        </label>
        {hasPermission('SUPPLIERS_READ') && <label className="space-y-1 text-sm font-semibold">Поставщик
          <select aria-label="Фильтр поставщика" value={filters.supplierId}
            disabled={suppliers.isPending || suppliers.isError}
            onChange={e => change('supplierId',e.target.value)}
            className="min-h-11 w-full rounded-xl border border-[#dce6eb] bg-[#f8fafb] px-3 text-[#182d38] focus-visible:outline-2 focus-visible:outline-[#416c82]">
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
      <div className="space-y-3"><h2 className="font-bold text-[#182d38]">Список закупок</h2><PurchaseTable lines={overview.data.items} />
        <PurchasePager page={overview.data.page} total={overview.data.total} size={overview.data.size}
          label="закупок" onPage={page => setFilters(old => ({...old,page}))} /></div>}</>}
  </div>;
}
