import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router';
import { Package, Plus, Search, X } from 'lucide-react';
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
    const timer = setTimeout(() => {
      setPage(0);
      setFilters(previous => {
        const next = { ...previous, search: searchInput.trim(), category: categoryInput.trim() };
        return previous.search === next.search && previous.category === next.category ? previous : next;
      });
    }, 350);
    return () => clearTimeout(timer);
  }, [searchInput, categoryInput]);
  const listing = useQuery({
    queryKey: catalogKeys.products(page, filters),
    queryFn: () => catalogApi.listProducts(page, filters),
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
  return <div className="space-y-5 sm:space-y-6">
    <PageHeader title="Каталог товаров" description="Модели кресел, цвета, артикулы и цены — без фотографий."
      action={canManage ? <Button type="button" disabled={creating}
        className="bg-blue-600 hover:bg-blue-700" onClick={() => setCreating(true)}>
        <Plus className="size-4" aria-hidden="true" />Создать товар
      </Button> : undefined}/>
    {creating && canManage && <ProductForm onCancel={() => setCreating(false)}
      onSave={async input => { await create.mutateAsync(input); }}/>}
    <section aria-label="Фильтры каталога"
      className="space-y-4 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm sm:p-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="text-base font-bold text-slate-900">Модели кресел</h3>
        {data && <span className="rounded-lg bg-blue-50 px-3 py-1.5 text-sm font-semibold text-blue-700">
          Найдено: {data.totalElements}
        </span>}
      </div>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-[minmax(220px,1fr)_minmax(160px,.75fr)_170px]">
        <label className="space-y-1.5 text-sm font-semibold text-slate-700">
          <span className="inline-flex items-center gap-2"><Search className="size-4 text-blue-600" aria-hidden="true"/>Поиск модели или категории</span>
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
          <select className="min-h-11 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm"
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
      data ? <section aria-label="Список моделей" className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="hidden overflow-x-auto md:block">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50"><tr>
              {['Модель','Категория','Статус'].map(col=>
                <th scope="col" key={col} className="px-5 py-3 font-semibold text-slate-600">{col}</th>)}
            </tr></thead>
            <tbody className="divide-y divide-slate-100">
              {data.items.map(model=><tr key={model.id}
                className="entity-clickable-row group transition-colors hover:bg-blue-50">
                <td className="px-5 py-4 font-semibold text-blue-700">
                  <Link className="entity-stretched-link hover:underline"
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
        <div className="divide-y divide-slate-100 md:hidden">
          {data.items.map(model=><article key={model.id}
            className="entity-clickable-row group flex items-start gap-3 p-4 transition-colors hover:bg-blue-50">
            <span className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-blue-50 text-blue-600">
              <Package className="size-5" aria-hidden="true"/>
            </span>
            <div className="min-w-0 flex-1">
              <h3 className="font-semibold text-blue-700"><Link className="entity-stretched-link"
                to={'/catalog/'+encodeURIComponent(model.id)}>{model.name}</Link></h3>
              <p className="mt-1 text-sm text-slate-500">{model.category || 'Категория не указана'}</p>
            </div>
            <StatusBadge tone={model.active?'success':'neutral'}>{model.active?'Активен':'Неактивен'}</StatusBadge>
          </article>)}
        </div>
        <CatalogPager page={data.page} totalPages={data.totalPages}
          totalElements={data.totalElements} onPage={setPage}/>
      </section> : null}
  </div>;
}
