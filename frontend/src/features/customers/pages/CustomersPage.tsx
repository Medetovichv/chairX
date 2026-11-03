import { usePersistedListState } from '../../../shared/hooks/usePersistedListState';
import { useCallback } from 'react';
import { useSearchParams } from 'react-router';
import { QuickViewDrawer } from '../../../shared/components/QuickViewDrawer';
import { CustomerQuickView } from '../../../shared/components/QuickViewContent';
import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { Plus, UsersRound, Search } from 'lucide-react';
import { CustomerWorkspaceOverview } from '../components/CustomerWorkspaceOverview';
import { useAuth } from '../../auth/AuthProvider';
import { ApiClientError } from '../../../shared/api/api-error';
import { PageHeader } from '../../../shared/components/PageHeader';
import { LoadingState } from '../../../shared/components/LoadingState';
import { EmptyState } from '../../../shared/components/EmptyState';
import { ErrorState } from '../../../shared/components/ErrorState';
import { Button } from '../../../shared/ui/button';
import { ForbiddenPage } from '../../../pages/ForbiddenPage';
import { customerKeys, customersApi } from '../api';
import { CustomerForm } from '../components/CustomerForm';
import { CustomerPager } from '../components/CustomerPager';
import { CustomerSearch } from '../components/CustomerSearch';
import { CustomerTable } from '../components/CustomerTable';

export function CustomersPage() {
  const { hasPermission, user } = useAuth();
  const [quickParams, setQuickParams] = useSearchParams();
  const selected = quickParams.get('quick');
  const goQuick = (id: string) => setQuickParams(prev => { const next = new URLSearchParams(prev); next.set('quick', id); return next; });
  const closeQuick = useCallback(() => setQuickParams(prev => {
    const next = new URLSearchParams(prev); next.delete('quick'); return next;
  }), [setQuickParams]);
  const interceptDetail = (event: React.MouseEvent<HTMLDivElement>) => {
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    const target = event.target;
    if (!(target instanceof Element)) return;
    const link = target.closest('a[href]');
    if (!link) return;
    const path = new URL(link.getAttribute('href') || '', window.location.origin).pathname;
    if (!path.startsWith('/customers/')) return;
    const id = path.slice('/customers/'.length);
    if (!id || id.includes('/')) return;
    event.preventDefault();
    setQuickParams(prev => { const next = new URLSearchParams(prev); next.set('quick', decodeURIComponent(id)); return next; });
  };

  const canRead = hasPermission('CUSTOMERS_READ');
  const canCreate = hasPermission('CUSTOMERS_CREATE');
  const withSales = hasPermission('SALES_READ');
  const navigate = useNavigate();
  const client = useQueryClient();
  const [search, setSearch] = usePersistedListState('customer-search',user?.id,'');
  const [query, setQuery] = useState('');
  const [page, setPage] = usePersistedListState('customer-page',user?.id,0);
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => setQuery(search.trim()), 350);
    return () => clearTimeout(timer);
  }, [search]);

  const listing = useQuery({
    queryKey: customerKeys.listing(withSales, query, page),
    queryFn: () => customersApi.list(withSales, query, page),
    enabled: canRead,
  });
  useEffect(() => {
    if (!listing.isSuccess || listing.data.kind === 'limited-search') return;
    // A persisted page can become invalid when records disappear or a user
    // returns to an older tab. Recover rather than reporting a false empty list.
    const { total, size } = listing.data.kind === 'overview'
      ? listing.data.data
      : { total: listing.data.data.totalElements, size: listing.data.data.size };
    const lastPage = Math.max(0, Math.ceil(total / Math.max(1, size)) - 1);
    if (!Number.isSafeInteger(page) || page < 0 || page > lastPage) setPage(lastPage);
  }, [listing.isSuccess, listing.data, page, setPage]);
  const create = useMutation({
    mutationFn: customersApi.create,
    onSuccess: async (saved) => {
      await client.invalidateQueries({ queryKey: customerKeys.lists });
      setCreating(false);
      navigate('/customers/' + encodeURIComponent(saved.id),
        { state: { notice: 'Клиент успешно создан.' } });
    },
  });

  if (!canRead) return <ForbiddenPage />;
  if (listing.error instanceof ApiClientError && listing.error.status === 403) return <ForbiddenPage />;

  const result = listing.data;
  const items = result?.kind === 'limited-search' ? result.items : result?.data.items ?? [];
  const total = result?.kind === 'overview' ? result.data.total :
    result?.kind === 'basic' ? result.data.totalElements : null;
  const pageIndex = result && result.kind !== 'limited-search' ? result.data.page : page;
  const pageSize = result && result.kind !== 'limited-search' ? result.data.size : 20;

  const visibleIds = (items ?? []).map(record => record.id);
  const selectedIndex = visibleIds.indexOf(selected ?? '');
  return <div className="flex min-w-0 items-start gap-4"><div onClickCapture={interceptDetail} className="min-w-0 flex-1 space-y-5 text-[#182d38] sm:space-y-6">
    <PageHeader description="Контакты, история заказов и быстрое обслуживание покупателей." title={creating?'Новый клиент':'Клиенты'}
      action={canCreate&&!creating ? <Button className="min-h-11 rounded-xl bg-[#172f3c] px-5 text-white hover:bg-[#305063]" onClick={() => setCreating(true)}><Plus className="size-4" aria-hidden="true"/>Добавить клиента</Button> : undefined} />
    {creating && canCreate && <CustomerForm onSave={async (data) => { await create.mutateAsync(data); }}
      onCancel={() => setCreating(false)} />}
    {!creating&&<section aria-label="Поиск и список клиентов" className="space-y-5">
      {listing.isSuccess && <CustomerWorkspaceOverview items={items} total={total} page={pageIndex}
        limitedSearch={result?.kind === 'limited-search'} withSales={result?.kind === 'overview'}/>} 
      <div className="space-y-3 rounded-2xl border border-[#dfe7eb] bg-white p-4 sm:p-6">
        <h2 className="flex items-center gap-2 text-base font-bold text-[#182d38]"><Search className="size-4 text-[#267f79]" aria-hidden="true"/>Поиск клиентов</h2>
        <CustomerSearch value={search} onChange={(value) => { setSearch(value); setPage(0); }} />
      </div>
      {!withSales && <p className="rounded-xl border border-[#dfe7eb] bg-white px-4 py-3 text-sm text-[#516977]">
        Статистика продаж скрыта: для её просмотра требуется дополнительное разрешение SALES_READ.
      </p>}
      {listing.isPending ? <LoadingState label="Загружаем клиентов…" />
        : listing.isError ? <ErrorState error={listing.error} onRetry={() => void listing.refetch()} />
          : items.length === 0 ? <EmptyState title={query ? 'Клиенты не найдены' : 'Клиентов пока нет'}
            description={query ? 'Попробуйте другой поисковый запрос.' : 'Добавьте первого клиента, чтобы начать вести базу контактов.'} />
            : <div className="space-y-3">
              <h2 className="flex items-center gap-2 text-base font-bold text-[#182d38]"><UsersRound className="size-4 text-[#267f79]" aria-hidden="true"/>Список клиентов</h2>
              <CustomerTable items={items} withSales={result?.kind === 'overview'} />
              {result?.kind === 'limited-search' ?
                <p className="rounded-b-xl border border-[#e3e9ee] bg-[#f2f6f8] p-3 text-sm text-[#516977]">
                  Поиск показывает не более 20 совпадений. Это не полный список; уточните запрос.
                </p> : total !== null ? <CustomerPager page={pageIndex} size={pageSize}
                  total={total} onPage={setPage} /> : null}
            </div>}
    </section>}
  </div>{selected && <QuickViewDrawer title={'Клиент'} detailUrl={'/customers/' + encodeURIComponent(selected)} onClose={closeQuick} previous={selectedIndex > 0 ? () => goQuick(visibleIds[selectedIndex - 1]!) : undefined} next={selectedIndex >= 0 && selectedIndex < visibleIds.length - 1 ? () => goQuick(visibleIds[selectedIndex + 1]!) : undefined}><CustomerQuickView key={selected} id={selected}/></QuickViewDrawer>}</div>;
}
