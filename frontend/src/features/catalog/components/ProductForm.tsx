import { useRef, useState, type FormEvent } from 'react';
import type { Product, ProductInput } from '../types';
import { productInput, validateProduct } from '../validation';
import { Input } from '../../../shared/ui/input';
import { Button } from '../../../shared/ui/button';
import { userFacingApiError } from '../../../shared/api/api-error';

export function ProductForm({ initial, onSave, onCancel }: {
  initial?: Product;
  onSave: (input: ProductInput) => Promise<void>;
  onCancel: () => void;
}) {
  const [name, setName] = useState(initial?.name ?? '');
  const [category, setCategory] = useState(initial?.category ?? '');
  const [description, setDescription] = useState(initial?.description ?? '');
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const pending = useRef(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending.current) return;
    const issue = validateProduct({ name, category, description });
    if (issue) { setError(issue); return; }
    pending.current = true;
    setSaving(true);
    setError(null);
    try {
      await onSave(productInput({ name, category, description }));
    } catch (cause) {
      setError(userFacingApiError(cause));
    } finally {
      pending.current = false;
      setSaving(false);
    }
  }
  return (
    <form onSubmit={(event) => void submit(event)}
      aria-label={initial ? 'Редактирование модели' : 'Создание модели'}
      className="space-y-4 rounded-2xl border border-indigo-100 bg-white p-5 shadow-sm sm:p-6">
      <h3 className="text-lg font-bold text-slate-900">{initial ? 'Изменить модель' : 'Новая модель кресла'}</h3>
      <div className="grid gap-4 sm:grid-cols-2">
        <label className="space-y-2 text-sm font-semibold text-slate-700">Название *
          <Input value={name} maxLength={200} required onChange={(event) => setName(event.target.value)}
            disabled={saving} placeholder="Название модели" />
        </label>
        <label className="space-y-2 text-sm font-semibold text-slate-700">Категория
          <Input value={category} maxLength={120} onChange={(event) => setCategory(event.target.value)}
            disabled={saving} placeholder="Например, офисные кресла" />
        </label>
      </div>
      <label className="block space-y-2 text-sm font-semibold text-slate-700">Описание
        <textarea rows={4} maxLength={4000} disabled={saving} value={description}
          onChange={(event) => setDescription(event.target.value)}
          className="w-full rounded-xl border border-slate-200 bg-white px-3.5 py-3 text-sm font-normal outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100"
          placeholder="Описание модели" />
      </label>
      {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
      <div className="flex flex-wrap gap-2">
        <Button type="submit" disabled={saving}>{saving ? 'Сохраняем…' : initial ? 'Сохранить модель' : 'Создать модель'}</Button>
        <Button type="button" variant="outline" disabled={saving} onClick={onCancel}>Отмена</Button>
      </div>
    </form>
  );
}
