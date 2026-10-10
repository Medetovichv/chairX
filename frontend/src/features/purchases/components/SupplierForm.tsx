import { useRef, useState, type FormEvent } from 'react';
import { Button } from '../../../shared/ui/button';
import { Input } from '../../../shared/ui/input';
import { userFacingApiError } from '../../../shared/api/api-error';
import { validateSupplier } from '../validation';
import type { Supplier, SupplierInput } from '../types';

export function SupplierForm({ initial, onSave, onCancel }: {
  initial?: Supplier; onSave: (data: SupplierInput) => Promise<void>; onCancel: () => void;
}) {
  const [name, setName] = useState(initial?.name ?? '');
  const [contact, setContact] = useState(initial?.contactInformation ?? '');
  const [comment, setComment] = useState(initial?.comment ?? '');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (pending.current) return;
    const data = { name: name.trim(), contactInformation: contact.trim() || null,
      comment: comment.trim() || null };
    const issue = validateSupplier(data);
    if (issue) { setError(issue); return; }
    pending.current = true; setBusy(true); setError('');
    try { await onSave(data); } catch (cause) { setError(userFacingApiError(cause)); }
    finally { pending.current = false; setBusy(false); }
  }
  return <form aria-label={initial ? 'Редактирование поставщика' : 'Создание поставщика'}
    onSubmit={e => void submit(e)} className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5">
    <h3 className="text-lg font-bold">{initial ? 'Изменить поставщика' : 'Новый поставщик'}</h3>
    <label className="block space-y-1 text-sm font-semibold">Название *
      <Input aria-label="Название поставщика" value={name} maxLength={200}
        disabled={busy} onChange={e => setName(e.target.value)} />
    </label>
    <label className="block space-y-1 text-sm font-semibold">Контактная информация
      <textarea aria-label="Контактная информация" value={contact} maxLength={1000}
        disabled={busy} rows={2} onChange={e => setContact(e.target.value)}
        className="w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm" />
    </label>
    <label className="block space-y-1 text-sm font-semibold">Комментарий
      <textarea aria-label="Комментарий поставщика" value={comment} maxLength={4000}
        disabled={busy} rows={3} onChange={e => setComment(e.target.value)}
        className="w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm" />
    </label>
    {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
    <div className="flex flex-wrap gap-2">
      <Button type="submit" disabled={busy}>{busy ? 'Сохраняем…' : 'Сохранить поставщика'}</Button>
      <Button type="button" variant="outline" disabled={busy} onClick={onCancel}>Отмена</Button>
    </div>
  </form>;
}
