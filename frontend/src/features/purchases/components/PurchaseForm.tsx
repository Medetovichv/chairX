import { useRef, useState, type FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { supplierApi, purchaseKeys } from '../api';
import { userFacingApiError } from '../../../shared/api/api-error';
import { Input } from '../../../shared/ui/input';
import { Button } from '../../../shared/ui/button';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState';
import { ProductVariantPicker } from './ProductVariantPicker';
import { integerQuantity, purchaseInput, validatePurchase } from '../validation';
import type { Purchase, PurchaseInput } from '../types';

type Row = { key: number; variantId: string; quantity: string; unitCost: string };
let nextKey = 0;
const newRow = (): Row => ({ key: ++nextKey, variantId: '', quantity: '1', unitCost: '' });
export function PurchaseForm({ initial, onSave, onCancel }: {
  initial?: Purchase; onSave: (input: PurchaseInput) => Promise<void>; onCancel: () => void;
}) {
  const { hasPermission } = useAuth();
  const mayChoose = hasPermission('SUPPLIERS_READ') && hasPermission('CATALOG_READ');
  const suppliers = useQuery({ queryKey: purchaseKeys.supplierAll, queryFn: supplierApi.all, enabled: mayChoose });
  const [supplierId, setSupplierId] = useState(initial?.supplierId ?? '');
  const [rows, setRows] = useState<Row[]>(() => initial?.items.map(item => ({
    key: ++nextKey, variantId: item.productVariantId, quantity: String(item.orderedQuantity),
    unitCost: String(item.purchaseUnitCost),
  })) ?? [newRow()]);
  const [cargo, setCargo] = useState(initial?.cargoCost === null || initial?.cargoCost === undefined ? '' : String(initial.cargoCost));
  const [comment, setComment] = useState(initial?.comment ?? '');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const pending = useRef(false);
  function update(key: number, values: Partial<Row>) {
    setRows(previous => previous.map(row => row.key === key ? { ...row, ...values } : row));
    setError('');
  }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending.current || !mayChoose) return;
    const payload = purchaseInput(supplierId, rows.map(row => ({
      productVariantId: row.variantId,
      orderedQuantity: integerQuantity(row.quantity) ?? 0,
      purchaseUnitCost: row.unitCost,
    })), cargo, comment);
    const issue = validatePurchase(payload);
    if (issue) { setError(issue); return; }
    pending.current = true; setSaving(true); setError('');
    try { await onSave(payload); } catch (cause) { setError(userFacingApiError(cause)); }
    finally { pending.current = false; setSaving(false); }
  }
  return <form aria-label={initial ? 'Редактирование закупки' : 'Создание закупки'}
    onSubmit={e => void submit(e)} className="space-y-5 rounded-2xl border border-indigo-100 bg-white p-4 shadow-sm sm:p-6">
    <h3 className="text-xl font-bold">{initial ? 'Изменить черновик' : 'Новая закупка'}</h3>
    {!mayChoose && <p role="alert" className="rounded-xl bg-amber-50 p-3 text-sm text-amber-900">
      Для выбора поставщика и товаров дополнительно требуются SUPPLIERS_READ и CATALOG_READ.
    </p>}
    {suppliers.isPending && mayChoose ? <LoadingState label="Загружаем поставщиков…" /> :
      suppliers.isError ? <ErrorState error={suppliers.error} onRetry={() => void suppliers.refetch()} /> :
      <label className="block space-y-2 text-sm font-semibold">Поставщик *
        <select aria-label="Поставщик закупки" value={supplierId} required disabled={saving || !mayChoose}
          onChange={e => { setSupplierId(e.target.value); setError(''); }}
          className="min-h-11 w-full rounded-xl border border-slate-200 bg-white px-3">
          <option value="">Выберите поставщика</option>
          {suppliers.data?.filter(s => s.active || s.id === initial?.supplierId).map(s =>
            <option key={s.id} value={s.id}>{s.name}{s.active ? '' : ' (неактивен)'}</option>)}
        </select>
      </label>}
    <div className="space-y-3">
      <h4 className="font-bold">Позиции закупки</h4>
      {rows.map((row, index) => <div key={row.key}
        className="space-y-3 rounded-xl border border-slate-200 bg-slate-50 p-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h5 className="font-semibold">Позиция {index + 1}</h5>
          <Button type="button" size="sm" variant="outline" disabled={saving || rows.length === 1}
            onClick={() => setRows(old => old.filter(r => r.key !== row.key))}>Удалить строку</Button>
        </div>
        {mayChoose && <ProductVariantPicker value={row.variantId} disabled={saving}
          onChange={variantId => update(row.key, { variantId })} />}
        {!mayChoose && <p className="text-xs">Текущая вариация: {row.variantId || 'не выбрана'}</p>}
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="space-y-1 text-sm font-semibold">Количество *
            <Input aria-label={'Количество позиции ' + (index + 1)} inputMode="numeric" value={row.quantity}
              disabled={saving} onChange={e => update(row.key, { quantity: e.target.value })} />
          </label>
          <label className="space-y-1 text-sm font-semibold">Закупочная цена, сом *
            <Input aria-label={'Цена позиции ' + (index + 1)} inputMode="decimal" value={row.unitCost}
              disabled={saving} onChange={e => update(row.key, { unitCost: e.target.value })} />
          </label>
        </div>
      </div>)}
      <Button type="button" variant="outline" disabled={saving || rows.length >= 1000}
        onClick={() => setRows(old => [...old, newRow()])}>+ Добавить позицию</Button>
    </div>
    <div className="grid gap-4 sm:grid-cols-2">
      <label className="block space-y-2 text-sm font-semibold">Карго, сом (можно оставить пустым)
        <Input aria-label="Карго закупки" inputMode="decimal" value={cargo} disabled={saving}
          onChange={e => { setCargo(e.target.value); setError(''); }} placeholder="Например, 1500.00" />
      </label>
      <label className="block space-y-2 text-sm font-semibold">Комментарий
        <textarea aria-label="Комментарий закупки" maxLength={4000} rows={3} value={comment}
          disabled={saving} onChange={e => setComment(e.target.value)}
          className="w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm" />
      </label>
    </div>
    <p className="text-xs text-slate-600">Карго должно быть явно указано до первой приёмки. Для нулевого карго введите 0.</p>
    {error && <p role="alert" className="rounded-xl bg-red-50 p-3 text-sm text-red-700">{error}</p>}
    <div className="flex flex-wrap gap-2">
      <Button type="submit" disabled={saving || !mayChoose || suppliers.isPending || suppliers.isError}>
        {saving ? 'Сохраняем…' : 'Сохранить закупку'}
      </Button>
      <Button type="button" variant="outline" disabled={saving} onClick={onCancel}>Отмена</Button>
    </div>
  </form>;
}
