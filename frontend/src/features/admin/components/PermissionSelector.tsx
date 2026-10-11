import { useState } from 'react';
import type { Permission } from '../types';
import { canChoosePermission } from '../validation';
import { Input } from '../../../shared/ui/input';

export function PermissionSelector({ all, selected, onChange, disabled }: {
  all: Permission[]; selected: string[]; onChange: (values: string[]) => void; disabled?: boolean;
}) {
  const [search, setSearch] = useState('');
  const filtered = all.filter(p => p.code.toLowerCase().includes(search.toLowerCase()) ||
    p.description.toLowerCase().includes(search.toLowerCase()));
  const groups = filtered.reduce<Record<string, Permission[]>>((acc, entry) => {
    (acc[entry.group] ??= []).push(entry);
    return acc;
  }, {});
  const selectedSet = new Set(selected);
  const sensitive = selected.some(x => all.find(p => p.code === x)?.sensitive);
  return <section aria-label="Выбор разрешений" className="space-y-3">
    <div className="flex flex-wrap justify-between gap-2">
      <h3 className="font-bold">Разрешения</h3>
      <strong className="text-sm">Выбрано: {selected.length}</strong>
    </div>
    <Input type="search" aria-label="Поиск разрешений" placeholder="Поиск по коду или описанию"
      value={search} onChange={e => setSearch(e.target.value)} />
    {sensitive && <p role="note" className="rounded-xl bg-amber-50 p-3 text-sm text-amber-900">
      Выбраны чувствительные права, включая возможные финансовые операции. Проверьте обязанности сотрудника.</p>}
    {Object.entries(groups).map(([group, items]) => <details key={group}
      className="rounded-xl border border-slate-200 p-3">
      <summary className="cursor-pointer font-semibold">{group} ({items?.length ?? 0})</summary>
      <div className="mt-3 grid max-h-60 gap-2 overflow-auto">
        {items?.map(p => <label key={p.code} className="flex items-start gap-3 rounded-lg p-2 text-sm hover:bg-slate-50">
          <input type="checkbox" className="mt-1 size-4" disabled={disabled || !canChoosePermission(p.code,p.adminOnly)}
            checked={selectedSet.has(p.code)} onChange={e =>
              onChange(e.target.checked ? [...selected,p.code] : selected.filter(x => x !== p.code))} />
          <span><span className="font-semibold">{p.code}</span>
            <span className="block text-slate-600">{p.description}</span>
            {p.sensitive && <span className="font-semibold text-amber-800">Чувствительное</span>}
            {!canChoosePermission(p.code,p.adminOnly) && <span className="block text-red-700">Недоступно пользовательским ролям</span>}
          </span>
        </label>)}
      </div>
    </details>)}
    {filtered.length === 0 && <p className="text-sm text-slate-500">Ничего не найдено</p>}
  </section>;
}
