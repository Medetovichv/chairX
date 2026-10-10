import { useState } from 'react';
import { useLocation, useParams, Link } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../../features/auth/AuthProvider';
import { ApiClientError, userFacingApiError } from '../../../shared/api/api-error';
import { PageHeader } from '../../../shared/components/PageHeader';
import { EmptyState } from '../../../shared/components/EmptyState';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState';
import { StatusBadge } from '../../../shared/components/StatusBadge';
import { Button } from '../../../shared/ui/button';
import { ConfirmDialog } from '../../../shared/ui/confirm-dialog';
import { NotFoundPage } from '../../../pages/NotFoundPage';
import { catalogApi, catalogKeys } from '../api';
import type { Product, ProductVariant, VariantInput } from '../types';
import { ProductForm } from '../components/ProductForm';
import { VariantForm } from '../components/VariantForm';
import { CatalogPager } from '../components/CatalogPager';
import { formatCatalogPrice } from '../validation';

function readableDate(date: string): string {
  const value = new Date(date);
  return Number.isNaN(value.getTime()) ? '—' : new Intl.DateTimeFormat('ru-RU', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    timeZone: 'Asia/Bishkek',
  }).format(value);
}

type ActionTarget = { kind: 'product'; active: boolean } | { kind: 'variant'; id: string; active: boolean };

function Detail({ id, product }: { id: string; product: Product }) {
  const { hasPermission } = useAuth();
  const canManage = hasPermission('CATALOG_MANAGE');
  const queryClient = useQueryClient();
  const location = useLocation();
  const initialNotice = (location.state as { notice?: unknown } | null)?.notice;
  const [notice, setNotice] = useState(typeof initialNotice === 'string' ? initialNotice : '');
  const [actionError, setActionError] = useState('');
  const [editingProduct, setEditingProduct] = useState(false);
  const [creatingVariant, setCreatingVariant] = useState(false);
  const [editingVariant, setEditingVariant] = useState<ProductVariant | null>(null);
  const [target, setTarget] = useState<ActionTarget | null>(null);
  const [page, setPage] = useState(0);

  const variants = useQuery({
    queryKey: catalogKeys.variants(id, page),
    queryFn: () => catalogApi.listVariants(id, page),
  });
  const update = useMutation({
    mutationFn: (input: Parameters<typeof catalogApi.updateProduct>[1]) => catalogApi.updateProduct(id, input),
    onSuccess: async (saved) => {
      queryClient.setQueryData(catalogKeys.product(id), saved);
      await queryClient.invalidateQueries({ queryKey: catalogKeys.productsPrefix });
      setEditingProduct(false); setActionError(''); setNotice('Модель обновлена.');
    },
  });
  const addVariant = useMutation({
    mutationFn: (input: VariantInput) => catalogApi.createVariant(id, input),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: catalogKeys.variantsPrefix(id) });
      setCreatingVariant(false); setPage(0); setNotice('Вариация добавлена.');
    },
  });
  const editVariant = useMutation({
    mutationFn: ({ variantId, input }: { variantId: string; input: VariantInput }) =>
      catalogApi.updateVariant(variantId, input),
    onSuccess: async (saved) => {
      queryClient.setQueryData(['catalog', 'variant', saved.id], saved);
      await queryClient.invalidateQueries({ queryKey: catalogKeys.variantsPrefix(id) });
      setEditingVariant(null); setNotice('Вариация обновлена.');
    },
  });
  const status = useMutation({
    mutationFn: async (action: ActionTarget) => action.kind === 'product'
      ? catalogApi.setProductActive(id, action.active)
      : catalogApi.setVariantActive(action.id, action.active),
    onSuccess: async (saved, action) => {
      if (action.kind === 'product') {
        queryClient.setQueryData(catalogKeys.product(id), saved);
        await queryClient.invalidateQueries({ queryKey: catalogKeys.productsPrefix });
      } else {
        await queryClient.invalidateQueries({ queryKey: catalogKeys.variantsPrefix(id) });
      }
      setTarget(null); setActionError(''); setNotice(action.active ? 'Запись активирована.' : 'Запись деактивирована.');
    },
    onError: (error) => {
      setTarget(null); setActionError(userFacingApiError(error)); setNotice('');
    },
  });

  function requestChange(action: ActionTarget) {
    setActionError(''); setNotice('');
    if (action.active) status.mutate(action);
    else setTarget(action);
  }

  return (
    <div className="space-y-6">
      <div><Link to="/catalog" className="inline-flex min-h-10 items-center text-sm font-semibold text-indigo-700">← Вернуться к каталогу</Link></div>
      <PageHeader title={product.name} description="Модель кресла и связанные с ней вариации."
        action={canManage ? <div className="flex flex-wrap gap-2">
          <Button type="button" variant="outline" onClick={() => setEditingProduct(true)} disabled={editingProduct}>Изменить модель</Button>
          <Button type="button" variant={product.active ? 'outline' : 'default'}
            disabled={status.isPending} onClick={() => requestChange({ kind: 'product', active: !product.active })}>
            {product.active ? 'Деактивировать модель' : 'Активировать модель'}
          </Button>
        </div> : undefined} />
      {notice && <p role="status" className="rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">{notice}</p>}
      {actionError && <p role="alert" className="rounded-xl border border-red-200 bg-red-50 p-3 text-sm text-red-700">{actionError}</p>}
      {editingProduct && canManage && (
        <ProductForm key={product.updatedAt} initial={product}
          onSave={async (input) => { await update.mutateAsync(input); }}
          onCancel={() => setEditingProduct(false)} />
      )}
      <section aria-label="Сведения о модели" className="grid gap-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-xs sm:grid-cols-2 lg:grid-cols-3">
        <div><dt className="text-xs text-slate-500">Категория</dt><dd className="mt-1 font-semibold">{product.category || '—'}</dd></div>
        <div><dt className="text-xs text-slate-500">Статус</dt><dd className="mt-2"><StatusBadge tone={product.active ? 'success' : 'neutral'}>{product.active ? 'Активен' : 'Неактивен'}</StatusBadge></dd></div>
        <div><dt className="text-xs text-slate-500">Дата создания</dt><dd className="mt-1 font-semibold">{readableDate(product.createdAt)}</dd></div>
        <div><dt className="text-xs text-slate-500">Дата изменения</dt><dd className="mt-1 font-semibold">{readableDate(product.updatedAt)}</dd></div>
        <div className="sm:col-span-2 lg:col-span-3"><dt className="text-xs text-slate-500">Описание</dt><dd className="mt-1 whitespace-pre-wrap text-sm text-slate-700">{product.description || 'Описание не указано'}</dd></div>
      </section>
      <section aria-label="Вариации товара" className="space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h3 className="text-xl font-bold text-slate-900">Вариации товара</h3>
          {canManage && <Button disabled={creatingVariant} onClick={() => { setEditingVariant(null); setCreatingVariant(true); }}>+ Добавить вариацию</Button>}
        </div>
        {creatingVariant && canManage && (
          <VariantForm onCancel={() => setCreatingVariant(false)}
            onSave={async (input) => { await addVariant.mutateAsync(input); }} />
        )}
        {editingVariant && canManage && (
          <VariantForm key={editingVariant.id} initial={editingVariant}
            onCancel={() => setEditingVariant(null)}
            onSave={async (input) => { await editVariant.mutateAsync({ variantId: editingVariant.id, input }); }} />
        )}
        {variants.isPending ? <LoadingState label="Загружаем вариации…" />
          : variants.isError ? <ErrorState error={variants.error} onRetry={() => void variants.refetch()} />
            : variants.data && variants.data.totalElements === 0 ? (
              <EmptyState title="У этой модели пока нет вариаций"
                description="Каждая вариация получает собственный UUID и может иметь отдельный цвет, SKU и цену."
                action={canManage ? <Button disabled={creatingVariant} onClick={() => setCreatingVariant(true)}>Добавить первую вариацию</Button> : undefined} />
            ) : variants.data ? (
              <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-xs">
                <div className="hidden overflow-x-auto md:block">
                  <table className="w-full text-left text-sm">
                    <thead className="bg-slate-50"><tr>{['Название', 'Цвет', 'SKU', 'Цена', 'Статус', 'Действия'].map((x) => (
                      <th key={x} scope="col" className="px-4 py-3 font-semibold text-slate-600">{x}</th>
                    ))}</tr></thead>
                    <tbody className="divide-y divide-slate-100">
                      {variants.data.items.map((variant) => (
                        <tr key={variant.id}>
                          <td className="px-4 py-4 font-semibold">{variant.name}</td>
                          <td className="px-4 py-4">{variant.color || '—'}</td>
                          <td className="px-4 py-4">{variant.sku || '—'}</td>
                          <td className="whitespace-nowrap px-4 py-4">{formatCatalogPrice(variant.recommendedSalePrice)}</td>
                          <td className="px-4 py-4"><StatusBadge tone={variant.active ? 'success' : 'neutral'}>{variant.active ? 'Активна' : 'Неактивна'}</StatusBadge></td>
                          <td className="px-4 py-4">{canManage && (
                            <div className="flex flex-wrap gap-2">
                              <Button variant="outline" size="sm" onClick={() => { setCreatingVariant(false); setEditingVariant(variant); }}>Изменить {variant.name}</Button>
                              <Button variant="outline" size="sm" disabled={status.isPending}
                                onClick={() => requestChange({ kind: 'variant', id: variant.id, active: !variant.active })}>
                                {variant.active ? 'Деактивировать' : 'Активировать'} {variant.name}
                              </Button>
                            </div>
                          )}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <div className="divide-y divide-slate-100 md:hidden">
                  {variants.data.items.map((variant) => (
                    <article key={variant.id} className="space-y-3 p-4">
                      <div className="flex items-start justify-between gap-2">
                        <h4 className="font-semibold">{variant.name}</h4>
                        <StatusBadge tone={variant.active ? 'success' : 'neutral'}>{variant.active ? 'Активна' : 'Неактивна'}</StatusBadge>
                      </div>
                      <p className="text-sm text-slate-600">Цвет: {variant.color || '—'} · SKU: {variant.sku || '—'}</p>
                      <p className="font-semibold">{formatCatalogPrice(variant.recommendedSalePrice)}</p>
                      {canManage && <div className="flex flex-wrap gap-2">
                        <Button variant="outline" onClick={() => { setCreatingVariant(false); setEditingVariant(variant); }}>Изменить {variant.name}</Button>
                        <Button variant="outline" disabled={status.isPending}
                          onClick={() => requestChange({ kind: 'variant', id: variant.id, active: !variant.active })}>
                          {variant.active ? 'Деактивировать' : 'Активировать'} {variant.name}
                        </Button>
                      </div>}
                    </article>
                  ))}
                </div>
                <CatalogPager page={variants.data.page} totalPages={variants.data.totalPages}
                  totalElements={variants.data.totalElements} onPage={setPage} />
              </div>
            ) : null}
      </section>
      <ConfirmDialog open={target !== null} onOpenChange={(open) => { if (!open) setTarget(null); }}
        title="Деактивировать запись?"
        description="Деактивация не удаляет модель или вариацию и не меняет существующие заказы. Повторно активировать запись можно позже."
        confirmLabel="Деактивировать" destructive busy={status.isPending}
        onConfirm={() => { if (target && !status.isPending) status.mutate(target); }} />
    </div>
  );
}

export function ProductDetailPage() {
  const { productId } = useParams();
  const id = productId ?? '';
  const valid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id);
  const item = useQuery({
    queryKey: catalogKeys.product(id),
    queryFn: () => catalogApi.getProduct(id),
    enabled: valid,
  });
  if (!valid) return <NotFoundPage />;
  if (item.isPending) return <LoadingState label="Загружаем карточку модели…" />;
  if (item.isError) {
    if (item.error instanceof ApiClientError && item.error.status === 404) return <NotFoundPage />;
    return <ErrorState error={item.error} onRetry={() => void item.refetch()} />;
  }
  if (!item.data) return <NotFoundPage />;
  return <Detail key={id} id={id} product={item.data} />;
}
