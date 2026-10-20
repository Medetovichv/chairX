import { useCallback } from 'react';
import { useSearchParams } from 'react-router';
import { QuickViewDrawer } from '../../../shared/components/QuickViewDrawer';
import { CustomerQuickView } from '../../../shared/components/QuickViewContent';
import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
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
  const { hasPermission } = useAuth();
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
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
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
  return <div className="flex min-w-0 items-start gap-4"><div onClickCapture={interceptDetail} className="space-y-6 min-w-0 flex-1">
    <PageHeader title={creating?'Новый клиент':'Клиенты'}
      action={canCreate&&!creating ? <Button onClick={() => setCreating(true)}>+ Добавить клиента</Button> : undefined} />
    {creating && canCreate && <CustomerForm onSave={async (data) => { await create.mutateAsync(data); }}
      onCancel={() => setCreating(false)} />}
    {!creating&&<section aria-label="Поиск и список клиентов" className="space-y-3">
      <div className="rounded-2xl border border-slate-200 bg-white p-4 sm:p-5">
        <CustomerSearch value={search} onChange={(value) => { setSearch(value); setPage(0); }} />
      </div>
      {!withSales && <p className="text-sm text-slate-600">
        Статистика продаж скрыта: для её просмотра требуется дополнительное разрешение SALES_READ.
      </p>}
      {listing.isPending ? <LoadingState label="Загружаем клиентов…" />
        : listing.isError ? <ErrorState error={listing.error} onRetry={() => void listing.refetch()} />
          : items.length === 0 ? <EmptyState title={query ? 'Клиенты не найдены' : 'Клиентов пока нет'}
            description={query ? 'Попробуйте другой поисковый запрос.' : 'Добавьте первого клиента, чтобы начать вести базу контактов.'} />
            : <div>
              <CustomerTable items={items} withSales={result?.kind === 'overview'} />
              {result?.kind === 'limited-search' ?
                <p className="rounded-b-xl border border-slate-200 bg-slate-50 p-3 text-sm text-slate-600">
                  Поиск показывает не более 20 совпадений. Это не полный список; уточните запрос.
                </p> : total !== null ? <CustomerPager page={pageIndex} size={pageSize}
                  total={total} onPage={setPage} /> : null}
            </div>}
    </section>}
  </div>{selected && <QuickViewDrawer title={'Клиент'} detailUrl={'/customers/' + encodeURIComponent(selected)} onClose={closeQuick} previous={selectedIndex > 0 ? () => goQuick(visibleIds[selectedIndex - 1]!) : undefined} next={selectedIndex >= 0 && selectedIndex < visibleIds.length - 1 ? () => goQuick(visibleIds[selectedIndex + 1]!) : undefined}><CustomerQuickView key={selected} id={selected}/></QuickViewDrawer>}</div>;
}
