import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router';
import { ArrowUpRight, Package, Plus, Search, X } from 'lucide-react';
import { CatalogWorkspaceOverview } from '../components/CatalogWorkspaceOverview';
import { useAuth } from '../../../features/auth/AuthProvider';
import { PageHeader } from '../../../shared/components/PageHeader';
import { EmptyState } from '../../../shared/components/EmptyState';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState';
import { StatusBadge } from '../../../shared/components/StatusBadge';
import { Button } from '../../../shared/ui/button';
import { Input } from '../../../shared/ui/input';
import { catalogApi, catalogKeys, type CatalogListFilters } from '../api';
import { CatalogPager } from '../components/CatalogPager';
import { ProductForm } from '../components/ProductForm';

export function CatalogPage() {
  const { hasPermission } = useAuth();
  const canManage = hasPermission('CATALOG_MANAGE');
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const [searchInput, setSearchInput] = useState('');
  const [categoryInput, setCategoryInput] = useState('');
  const [filters, setFilters] = useState<CatalogListFilters>({ search: '', category: '', active: null });
  useEffect(() => {
    // Do not reset pagination on initial mount or a no-op filter update.
    // Otherwise the delayed 350ms callback can race a user's "Next" click.
    if (searchInput.trim() === filters.search && categoryInput.trim() === filters.category) return;
    const timer = setTimeout(() => {
      setPage(0);
      setFilters(previous => {
        const next = { ...previous, search: searchInput.trim(), category: categoryInput.trim() };
        return previous.search === next.search && previous.category === next.category ? previous : next;
      });
    }, 350);
    return () => clearTimeout(timer);
  }, [searchInput, categoryInput, filters.search, filters.category]);
  const listing = useQuery({
    queryKey: catalogKeys.products(page, filters),
    queryFn: () => catalogApi.listProducts(page, filters),
    enabled: !creating,
  });
  const create = useMutation({
    mutationFn: catalogApi.createProduct,
    onSuccess: async model => {
      await queryClient.invalidateQueries({ queryKey: catalogKeys.productsPrefix });
      setCreating(false);
      navigate('/catalog/' + encodeURIComponent(model.id), { state: { notice: 'Модель успешно создана.' } });
    },
  });
  const data = listing.data;
  const hasFilters = !!filters.search || !!filters.category || filters.active !== null;
  const reset = () => {
    setSearchInput('');
    setCategoryInput('');
    setFilters({ search: '', category: '', active: null });
    setPage(0);
  };
  return <div className="space-y-5 text-[#182d38] sm:space-y-6">
    <PageHeader title={creating ? 'Новая модель кресла' : 'Каталог товаров'}
      description={creating ? 'Сначала создайте модель, затем добавьте цвета и артикулы в её карточке.' :
        'Модели кресел, цвета, артикулы и цены — без фотографий.'}
      action={canManage && !creating ? <Button type="button" disabled={creating}
        className="min-h-11 rounded-xl bg-[#172f3c] px-5 text-white hover:bg-[#305063]" onClick={() => setCreating(true)}>
        <Plus className="size-4" aria-hidden="true" />Создать товар
      </Button> : undefined}/>
    {creating && canManage && <ProductForm onCancel={() => setCreating(false)}
      onSave={async input => { await create.mutateAsync(input); }}/>}
    {!creating && <>
    {data && <CatalogWorkspaceOverview page={data}/>}
    <section aria-label="Фильтры каталога"
      className="space-y-4 rounded-2xl border border-[#dfe7eb] bg-white p-4 sm:p-6">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="flex items-center gap-2 text-base font-bold tracking-tight text-[#182d38]"><Search className="size-4 text-[#267f79]" aria-hidden="true"/>Поиск и фильтры</h2>
        {data && <span className="rounded-full bg-[#eaf2f6] px-3 py-1.5 text-sm font-semibold tabular-nums text-[#416c82]">
          Найдено: {data.totalElements}
        </span>}
      </div>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-[minmax(220px,1fr)_minmax(160px,.75fr)_170px]">
        <label className="space-y-1.5 text-sm font-semibold text-slate-700">
          <span className="inline-flex items-center gap-2"><Search className="size-4 text-[#416c82]" aria-hidden="true"/>Поиск модели или категории</span>
          <Input type="search" value={searchInput} maxLength={200}
            aria-label="Поиск модели или категории"
            onChange={e=>setSearchInput(e.target.value)} placeholder="Например, Ergo или офисные"/>
        </label>
        <label className="space-y-1.5 text-sm font-semibold text-slate-700">
          Категория
          <Input type="search" value={categoryInput} maxLength={120}
            aria-label="Категория товара"
            onChange={e=>setCategoryInput(e.target.value)} placeholder="Точное название категории"/>
        </label>
        <label className="space-y-1.5 text-sm font-semibold text-slate-700">
          Статус
          <select className="min-h-11 w-full rounded-xl border border-[#dce6eb] bg-[#f8fafb] px-3 text-sm text-[#182d38] focus-visible:outline-2 focus-visible:outline-[#416c82]"
            aria-label="Статус товара"
            value={filters.active === null ? 'all' : filters.active ? 'active' : 'inactive'}
            onChange={e=>{
              const value=e.target.value;
              setFilters(old=>({...old, active: value === 'all' ? null : value === 'active'}));
              setPage(0);
            }}>
            <option value="all">Все модели</option>
            <option value="active">Активные</option>
            <option value="inactive">Неактивные</option>
          </select>
        </label>
      </div>
      {hasFilters && <Button type="button" variant="ghost" size="sm" onClick={reset}>
        <X className="size-4" aria-hidden="true"/>Сбросить фильтры
      </Button>}
      <p className="text-xs text-slate-500">Поиск и фильтры применяются ко всему каталогу на сервере, до пагинации.</p>
    </section>
    {listing.isPending ? <LoadingState label="Загружаем модели кресел…"/> :
      listing.isError ? <ErrorState error={listing.error} onRetry={() => void listing.refetch()}/> :
      data && data.totalElements === 0 ? <EmptyState
        title={hasFilters ? 'По вашему запросу ничего не найдено' : 'Каталог пока пуст'}
        description={hasFilters ? 'Измените параметры поиска или сбросьте фильтры.' :
          'Здесь появятся модели после добавления первого товара.'}
        action={hasFilters ? <Button variant="outline" onClick={reset}>Сбросить фильтры</Button> :
          canManage ? <Button disabled={creating} onClick={()=>setCreating(true)}>Создать товар</Button> : undefined}/> :
      data ? <section aria-label="Список моделей" className="overflow-hidden rounded-2xl border border-[#dfe7eb] bg-white">
        <div className="hidden overflow-x-auto md:block">
          <table className="w-full text-left text-sm">
            <thead className="bg-[#f5f9f7]"><tr>
              {['Модель','Категория','Статус'].map(col=>
                <th scope="col" key={col} className="px-5 py-3 font-semibold text-[#516977]">{col}</th>)}
            </tr></thead>
            <tbody className="divide-y divide-slate-100">
              {data.items.map(model=><tr key={model.id}
                className="entity-clickable-row group transition-colors hover:bg-[#f5faf8]">
                <td className="px-5 py-4 font-bold text-[#236e66]">
                  <Link className="entity-stretched-link [overflow-wrap:anywhere] hover:underline focus-visible:outline-2 focus-visible:outline-[#267f79]"
                    to={'/catalog/'+encodeURIComponent(model.id)}>{model.name}</Link>
                </td>
                <td className="px-5 py-4 text-slate-600">{model.category || '—'}</td>
                <td className="px-5 py-4">
                  <StatusBadge tone={model.active?'success':'neutral'}>{model.active?'Активен':'Неактивен'}</StatusBadge>
                </td>
              </tr>)}
            </tbody>
          </table>
        </div>
        <div className="divide-y divide-[#eaf0f0] md:hidden">
          {data.items.map(model=><article key={model.id}
            className="entity-clickable-row group space-y-3 p-4 transition-colors hover:bg-[#f5faf8] sm:p-5">
            <div className="flex items-center justify-between gap-3">
              <span className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-[#e7f4ee] text-[#27756d]">
                <Package className="size-5" aria-hidden="true"/>
              </span>
              <StatusBadge tone={model.active?'success':'neutral'}>{model.active?'Активен':'Неактивен'}</StatusBadge>
            </div>
            <div className="min-w-0">
              <h3 className="font-bold text-[#236e66]"><Link className="entity-stretched-link flex items-center gap-2 break-words"
                to={'/catalog/'+encodeURIComponent(model.id)}>{model.name}<ArrowUpRight className="size-4 shrink-0" aria-hidden="true"/></Link></h3>
              <p className="mt-1 text-sm text-slate-500 [overflow-wrap:anywhere]">{model.category || 'Категория не указана'}</p>
            </div>
          </article>)}
        </div>
        <CatalogPager page={data.page} totalPages={data.totalPages}
          totalElements={data.totalElements} onPage={setPage}/>
      </section> : null}
    </>}
  </div>;
}
