import { useRef, useState, type FormEvent } from 'react';
import type { ProductVariant, VariantInput } from '../types';
import { palette, validateVariant, variantInput } from '../validation';
import { userFacingApiError } from '../../../shared/api/api-error';
import { Button } from '../../../shared/ui/button';
import { Input } from '../../../shared/ui/input';

export function VariantForm({ initial, onSave, onCancel }: {
  initial?: ProductVariant;
  onSave: (input: VariantInput) => Promise<void>;
  onCancel: () => void;
}) {
  const [name, setName] = useState(initial?.name ?? '');
  const [color, setColor] = useState(initial?.color ?? '');
  const [sku, setSku] = useState(initial?.sku ?? '');
  const [price, setPrice] = useState(initial ? String(initial.recommendedSalePrice) : '');
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const pending = useRef(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending.current) return;
    const problem = validateVariant({ name, color, sku, price });
    if (problem) { setError(problem); return; }
    pending.current = true;
    setSaving(true);
    setError(null);
    try {
      await onSave(variantInput({ name, color, sku, price }));
    } catch (cause) {
      setError(userFacingApiError(cause));
    } finally {
      pending.current = false;
      setSaving(false);
    }
  }
  return (
    <form aria-label={initial ? 'Редактирование вариации' : 'Создание вариации'}
      onSubmit={(event) => void submit(event)}
      className="space-y-4 rounded-2xl border border-indigo-100 bg-white p-5 shadow-sm sm:p-6">
      <h3 className="text-lg font-bold">{initial ? 'Изменить вариацию' : 'Новая вариация кресла'}</h3>
      <div className="grid gap-4 sm:grid-cols-2">
        <label className="space-y-2 text-sm font-semibold text-slate-700">Название вариации *
          <Input required maxLength={200} value={name} disabled={saving}
            onChange={(event) => setName(event.target.value)} placeholder="Например, Чёрный" />
        </label>
        <label className="space-y-2 text-sm font-semibold text-slate-700">Цвет
          <Input list="chairx-colors" maxLength={100} value={color} disabled={saving}
            onChange={(event) => setColor(event.target.value)}
            placeholder="Выберите или введите свой цвет" />
          <datalist id="chairx-colors">{palette.map((c) => <option key={c} value={c} />)}</datalist>
        </label>
        <label className="space-y-2 text-sm font-semibold text-slate-700">SKU (необязательно)
          <Input maxLength={100} value={sku} disabled={saving} placeholder="Артикул"
            onChange={(event) => setSku(event.target.value)} />
        </label>
        <label className="space-y-2 text-sm font-semibold text-slate-700">Рекомендуемая цена, сом *
          <Input required inputMode="decimal" value={price} disabled={saving} placeholder="8500,00"
            onChange={(event) => setPrice(event.target.value)} />
        </label>
      </div>
      {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
      <div className="flex flex-wrap gap-2">
        <Button type="submit" disabled={saving}>{saving ? 'Сохраняем…' : initial ? 'Сохранить вариацию' : 'Добавить вариацию'}</Button>
        <Button type="button" variant="outline" disabled={saving} onClick={onCancel}>Отмена</Button>
      </div>
    </form>
  );
}
