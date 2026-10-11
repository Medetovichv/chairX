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
      className="space-y-5">
      <div className="rounded-2xl border border-slate-200 bg-white px-5 py-3 text-xs font-semibold text-slate-600">
        1. Сведения о модели → 2. Сохранение → 3. Цвета и вариации
      </div>
      <div className="grid items-start gap-5 xl:grid-cols-[minmax(0,1fr)_minmax(17rem,20rem)]">
        <section aria-label="Сведения для новой модели"
          className="space-y-4 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm sm:p-6">
          <h3 className="text-lg font-bold text-slate-900">
            {initial ? 'Изменить модель' : '1. Сведения о модели'}
          </h3>
          <p className="text-sm text-slate-600">
            Модель — это общая карточка кресла. Отдельные цвета и SKU добавляются после сохранения.
          </p>
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="space-y-2 text-sm font-semibold text-slate-700">Название *
              <Input value={name} maxLength={200} required onChange={(event) => setName(event.target.value)}
                disabled={saving} placeholder="Например, ChairX Pro" />
            </label>
            <label className="space-y-2 text-sm font-semibold text-slate-700">Категория
              <Input value={category} maxLength={120} onChange={(event) => setCategory(event.target.value)}
                disabled={saving} placeholder="Например, офисные кресла" />
            </label>
          </div>
          <label className="block space-y-2 text-sm font-semibold text-slate-700">Описание
            <textarea rows={4} maxLength={4000} disabled={saving} value={description}
              onChange={(event) => setDescription(event.target.value)}
              className="w-full rounded-xl border border-slate-200 bg-white px-3.5 py-3 text-sm font-normal outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
              placeholder="Описание модели (необязательно)" />
          </label>
        </section>
        <aside aria-label="Проверка модели"
          className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <h3 className="text-lg font-bold text-slate-900">2. Проверка модели</h3>
          <dl className="space-y-3 text-sm">
            <div><dt className="text-slate-500">Название</dt>
              <dd className="mt-1 break-words font-semibold">{name.trim() || 'Не указано'}</dd></div>
            <div><dt className="text-slate-500">Категория</dt>
              <dd className="mt-1 font-semibold">{category.trim() || 'Без категории'}</dd></div>
            <div><dt className="text-slate-500">Описание</dt>
              <dd className="mt-1 line-clamp-3 whitespace-pre-wrap break-words">{description.trim() || 'Не указано'}</dd></div>
          </dl>
          <p className="rounded-xl bg-blue-50 p-3 text-xs leading-relaxed text-blue-950">
            После создания откроется карточка модели, где можно добавить вариации, цвета, SKU и рекомендуемые цены.
            Создание модели не изменяет остатки на складах.
          </p>
          {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
          <Button className="w-full" type="submit" disabled={saving}>
            {saving ? 'Сохраняем…' : initial ? 'Сохранить модель' : 'Создать модель'}
          </Button>
          <Button className="w-full" type="button" variant="outline" disabled={saving}
            onClick={onCancel}>Отмена</Button>
        </aside>
      </div>
    </form>
  );
}
