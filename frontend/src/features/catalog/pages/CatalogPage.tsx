import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router';
import { useAuth } from '../../../features/auth/AuthProvider';
import { PageHeader } from '../../../shared/components/PageHeader';
import { EmptyState } from '../../../shared/components/EmptyState';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState';
import { StatusBadge } from '../../../shared/components/StatusBadge';
import { Button } from '../../../shared/ui/button';
import { catalogApi, catalogKeys } from '../api';
import { CatalogPager } from '../components/CatalogPager';
import { ProductForm } from '../components/ProductForm';

export function CatalogPage() {
  const { hasPermission } = useAuth();
  const canManage = hasPermission('CATALOG_MANAGE');
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const listing = useQuery({ queryKey: catalogKeys.products(page), queryFn: () => catalogApi.listProducts(page) });
  const create = useMutation({
    mutationFn: catalogApi.createProduct,
    onSuccess: async (model) => {
      await queryClient.invalidateQueries({ queryKey: catalogKeys.productsPrefix });
      setCreating(false);
      navigate('/catalog/' + encodeURIComponent(model.id), { state: { notice: 'Модель успешно создана.' } });
    },
  });
  const data = listing.data;

  return (
    <div className="space-y-6">
      <PageHeader title="Каталог товаров" description="Управление моделями кресел и их вариациями."
        action={canManage ? <Button type="button" disabled={creating}
          onClick={() => setCreating(true)}>+ Создать товар</Button> : undefined} />
      {creating && canManage && (
        <ProductForm onCancel={() => setCreating(false)}
          onSave={async (input) => { await create.mutateAsync(input); }} />
      )}
      <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">
        <p className="font-medium">Поиск по названию, категории и активности</p>
        <p className="mt-1">Глобальные поиск и фильтры пока недоступны: backend поддерживает только пагинацию. Список не фильтруется частично на одной странице.</p>
      </div>

      {listing.isPending ? <LoadingState label="Загружаем модели кресел…" />
        : listing.isError ? <ErrorState error={listing.error} onRetry={() => void listing.refetch()} />
          : data && data.items.length === 0 && data.totalElements === 0 ? (
            <EmptyState title="Каталог пока пуст"
              description="Здесь появятся модели после добавления первого товара."
              action={canManage ? <Button disabled={creating} onClick={() => setCreating(true)}>+ Создать товар</Button> : undefined} />
          ) : data ? (
            <section aria-label="Список моделей" className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-xs">
              <div className="hidden overflow-x-auto md:block">
                <table className="w-full text-left text-sm">
                  <thead className="bg-slate-50">
                    <tr>{['Название', 'Категория', 'Статус', 'Действия'].map((col) => (
                      <th scope="col" key={col} className="px-5 py-3 font-semibold text-slate-600">{col}</th>
                    ))}</tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {data.items.map((model) => (
                      <tr key={model.id}>
                        <td className="px-5 py-4 font-semibold text-slate-800">{model.name}</td>
                        <td className="px-5 py-4 text-slate-600">{model.category || '—'}</td>
                        <td className="px-5 py-4"><StatusBadge tone={model.active ? 'success' : 'neutral'}>{model.active ? 'Активен' : 'Неактивен'}</StatusBadge></td>
                        <td className="px-5 py-4"><Link className="font-semibold text-indigo-700 hover:underline" to={'/catalog/' + model.id}>Открыть</Link></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="divide-y divide-slate-100 md:hidden">
                {data.items.map((model) => (
                  <article key={model.id} className="space-y-3 p-4">
                    <div className="flex items-start justify-between gap-2">
                      <h3 className="font-semibold text-slate-900">{model.name}</h3>
                      <StatusBadge tone={model.active ? 'success' : 'neutral'}>{model.active ? 'Активен' : 'Неактивен'}</StatusBadge>
                    </div>
                    <p className="text-sm text-slate-500">{model.category || 'Категория не указана'}</p>
                    <Link className="inline-flex min-h-11 items-center font-semibold text-indigo-700" to={'/catalog/' + model.id}>Открыть модель</Link>
                  </article>
                ))}
              </div>
              <CatalogPager page={data.page} totalPages={data.totalPages}
                totalElements={data.totalElements} onPage={setPage} />
            </section>
          ) : null}
    </div>
  );
}
