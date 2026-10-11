import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { catalogApi, catalogKeys } from '../../catalog/api';
import { Button } from '../../../shared/ui/button';
import { ErrorState } from '../../../shared/components/ErrorState';
import { LoadingState } from '../../../shared/components/LoadingState';
import { PurchasePager } from './PurchasePager';

/** The catalog only supports server pagination; selection is deliberately paged, not a partial search. */
export function ProductVariantPicker({ value, onChange, disabled = false }: {
  value: string; onChange: (id: string) => void; disabled?: boolean;
}) {
  const [modelPage, setModelPage] = useState(0);
  const [modelId, setModelId] = useState('');
  const [variantPage, setVariantPage] = useState(0);
  const [open, setOpen] = useState(false);
  const [modelSearch, setModelSearch] = useState('');
  const [search, setSearch] = useState('');
  useEffect(() => {
    const timer = setTimeout(() => setSearch(modelSearch.trim()), 300);
    return () => clearTimeout(timer);
  }, [modelSearch]);
  const selectedVariant = useQuery({
    queryKey: ['catalog', 'variant', value],
    queryFn: () => catalogApi.getVariant(value),
    enabled: Boolean(value),
  });
  const selectedProduct = useQuery({
    queryKey: catalogKeys.product(selectedVariant.data?.productId ?? ''),
    queryFn: () => catalogApi.getProduct(selectedVariant.data!.productId),
    enabled: Boolean(selectedVariant.data?.productId),
  });
  const products = useQuery({
    queryKey: catalogKeys.products(modelPage, {search}), 
    queryFn: () => catalogApi.listProducts(modelPage, {search}),
    enabled: open,
  });
  const variants = useQuery({
    queryKey: catalogKeys.variants(modelId, variantPage),
    queryFn: () => catalogApi.listVariants(modelId, variantPage),
    enabled: open && Boolean(modelId),
  });
  const model = products.data?.items.find(p => p.id === modelId);
  return <div className="space-y-3">
    <div className="flex flex-wrap items-center gap-2">
      <Button type="button" variant="outline" disabled={disabled} onClick={() => setOpen(!open)}>
        {value ? 'Сменить вариацию' : 'Выбрать вариацию'}
      </Button>
      {value && <div className="min-w-0 text-sm text-slate-600">
        {selectedVariant.data ? <span>
          Выбрано: <strong>{selectedProduct.data?.name ?? 'Модель кресла'} · {selectedVariant.data.name}</strong>
          {selectedVariant.data.sku ? ' · ' + selectedVariant.data.sku : ''}
          {selectedProduct.data && <>{' · '}<Link className="font-semibold text-blue-700 hover:underline"
            to={'/catalog/' + encodeURIComponent(selectedProduct.data.id)}>Карточка товара</Link></>}
        </span> : selectedVariant.isError ?
          <span role="alert">Не удалось загрузить название выбранной вариации.</span> :
          <span>Загружаем выбранную вариацию…</span>}
      </div>}
    </div>
    {open && !disabled && <div className="space-y-3 rounded-xl border border-slate-200 bg-slate-50 p-3">
      <label className="block space-y-2 text-sm font-semibold">
        Найти модель кресла
        <input type="search" aria-label="Поиск моделей для закупки" value={modelSearch}
          disabled={disabled} maxLength={200}
          placeholder="Введите название модели"
          onChange={e => {setModelSearch(e.target.value);setModelPage(0);setModelId('');setVariantPage(0);}}
          className="min-h-11 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm"/>
      </label>
      <p className="text-xs text-slate-600">
        Поиск по всем моделям выполняется сервером. Вариации выбираются постранично.
        Для закупки доступны и варианты без складских остатков.
      </p>
      {products.isPending ? <LoadingState label="Загружаем модели…" /> :
        products.isError ? <ErrorState error={products.error} onRetry={() => void products.refetch()} /> :
        <div className="space-y-2">
          <label className="block text-sm font-semibold">Модель
            <select aria-label="Модель для вариации" value={modelId}
              onChange={e => { setModelId(e.target.value); setVariantPage(0); }}
              className="mt-1 min-h-11 w-full rounded-xl border border-slate-200 bg-white px-3">
              <option value="">Выберите модель</option>
              {products.data?.items.filter(p => p.active).map(p =>
                <option value={p.id} key={p.id}>{p.name}</option>)}
            </select>
          </label>
          <PurchasePager page={products.data?.page ?? modelPage}
            total={products.data?.totalElements ?? 0} size={20} label="моделей"
            onPage={p => { setModelPage(p); setModelId(''); setVariantPage(0); }} />
        </div>}
      {modelId && <div>
        <p className="text-sm font-semibold">Вариации {model?.name ?? ''}</p>
        {variants.isPending ? <LoadingState label="Загружаем вариации…" /> :
          variants.isError ? <ErrorState error={variants.error} onRetry={() => void variants.refetch()} /> :
          <div className="space-y-2">
            {variants.data?.items.filter(v => v.active).map(v =>
              <Button key={v.id} type="button" variant={value === v.id ? 'default' : 'outline'}
                className="mr-2 mb-2 whitespace-normal break-words" onClick={() => { onChange(v.id); setOpen(false); }}>
                {v.name}{v.color ? ' · ' + v.color : ''}{v.sku ? ' · ' + v.sku : ''}
              </Button>)}
            {(variants.data?.items.filter(v => v.active).length ?? 0) === 0 &&
              <p className="text-sm text-slate-500">На этой странице нет активных вариаций.</p>}
            <PurchasePager page={variants.data?.page ?? variantPage}
              total={variants.data?.totalElements ?? 0} size={20} label="вариаций"
              onPage={setVariantPage} />
          </div>}
      </div>}
    </div>}
  </div>;
}
