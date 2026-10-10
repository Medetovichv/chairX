import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { useAuth } from '../../auth/AuthProvider';
import { ApiClientError, userFacingApiError } from '../../../shared/api/api-error';
import { Button } from '../../../shared/ui/button';
import { Input } from '../../../shared/ui/input';
import { customerKeys, customersApi } from '../api';
import type { Customer, CustomerInput } from '../types';
import {
  customerFields, customerFormValues, customerInput, customerLabels,
  customerLimits, customerTitle, validateCustomer,
} from '../validation';

export function CustomerForm({ initial, onSave, onCancel }: {
  initial?: Customer;
  onSave: (data: CustomerInput) => Promise<void>;
  onCancel: () => void;
}) {
  const { hasPermission } = useAuth();
  const [values, setValues] = useState(() => customerFormValues(initial));
  const [duplicateTerm, setDuplicateTerm] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const pending = useRef(false);
  const contact = values.phone.trim() || values.secondaryPhone.trim() ||
    values.whatsappPhone.trim() || values.instagramUsername.trim();

  useEffect(() => {
    const timer = setTimeout(() => setDuplicateTerm(contact.length >= 4 ? contact : ''), 400);
    return () => clearTimeout(timer);
  }, [contact]);

  const duplicates = useQuery({
    queryKey: customerKeys.duplicates(duplicateTerm),
    queryFn: () => customersApi.search(duplicateTerm),
    enabled: !initial && hasPermission('CUSTOMERS_READ') && duplicateTerm.length >= 4,
  });

  function setField(field: keyof CustomerInput, value: string) {
    setValues((current) => ({ ...current, [field]: value }));
    setError('');
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending.current) return;
    const problem = validateCustomer(values);
    if (problem) { setError(problem); return; }
    pending.current = true;
    setSaving(true);
    setError('');
    try {
      await onSave(customerInput(values));
    } catch (cause) {
      const message = userFacingApiError(cause);
      setError(cause instanceof ApiClientError && cause.kind === 'network' && !initial
        ? message + ' Создание могло завершиться на сервере. Перед повторной отправкой найдите клиента по контакту.'
        : message);
    } finally {
      pending.current = false;
      setSaving(false);
    }
  }

  return <form aria-label={initial ? 'Редактирование клиента' : 'Создание клиента'}
    onSubmit={(event) => void submit(event)}
    className="space-y-5 rounded-2xl border border-indigo-100 bg-white p-4 shadow-sm sm:p-6">
    <div>
      <h3 className="text-lg font-bold text-slate-900">{initial ? 'Редактировать клиента' : 'Новый клиент'}</h3>
      <p className="mt-1 text-sm text-slate-500">Имя необязательно. Достаточно хотя бы одного контакта.</p>
    </div>
    <div className="grid gap-4 sm:grid-cols-2">
      {customerFields.map((field) => <label key={field}
        className={'block space-y-2 text-sm font-semibold text-slate-700 ' +
          (field === 'address' || field === 'comment' ? 'sm:col-span-2' : '')}>
        {customerLabels[field]}
        {field === 'comment' || field === 'address' ?
          <textarea aria-label={customerLabels[field]} rows={field === 'comment' ? 3 : 2}
            maxLength={customerLimits[field]} value={values[field]} disabled={saving}
            onChange={(event) => setField(field, event.target.value)}
            className="w-full rounded-xl border border-slate-200 bg-white px-3.5 py-3 text-sm font-normal outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100" />
          : <Input aria-label={customerLabels[field]} type="text" autoComplete="off"
            maxLength={customerLimits[field]} value={values[field]} disabled={saving}
            onChange={(event) => setField(field, event.target.value)} />}
      </label>)}
    </div>
    {!initial && duplicates.data && duplicates.data.length > 0 && <section role="status"
      className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
      <h4 className="font-bold">Возможно, такой клиент уже есть</h4>
      <p className="mt-1">Совпадение не блокирует создание. Проверьте контакт перед сохранением.</p>
      <ul className="mt-2 space-y-1">
        {duplicates.data.slice(0, 5).map((item) =>
          <li key={item.id}>
            <Link className="font-semibold underline" to={'/customers/' + encodeURIComponent(item.id)}>
              {customerTitle(item)}
            </Link>
          </li>)}
      </ul>
    </section>}
    {!initial && duplicates.isError && duplicateTerm.length >= 4 && <p className="text-sm text-amber-800">
      Проверка похожих контактов недоступна. Сохранение не заблокировано.
    </p>}
    {error && <p role="alert" className="rounded-xl bg-red-50 p-3 text-sm text-red-700">{error}</p>}
    <div className="flex flex-wrap gap-3">
      <Button type="submit" disabled={saving}>{saving ? 'Сохраняем…' : 'Сохранить клиента'}</Button>
      <Button type="button" variant="outline" disabled={saving} onClick={onCancel}>Отмена</Button>
    </div>
  </form>;
}
