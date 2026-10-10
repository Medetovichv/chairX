import { useState } from 'react';
import { Search } from 'lucide-react';

// Visual controls only. No fake data and no API requests in F02.1.
export function PageToolbar({ filters = [], searchPlaceholder }: {
  filters?: readonly string[]; searchPlaceholder?: string;
}) {
  const [active, setActive] = useState(filters[0] ?? '');
  return (
    <div className="space-y-3 rounded-2xl border border-slate-200 bg-white p-4">
      {searchPlaceholder && (
        <div className="relative max-w-md">
          <Search aria-hidden="true" className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-slate-400" />
          <input aria-label={searchPlaceholder} type="search" disabled
            placeholder={searchPlaceholder}
            title="Поиск будет подключён в следующем пакете"
            className="min-h-11 w-full rounded-xl border border-slate-200 bg-slate-50 pl-9 pr-3 text-sm placeholder:text-slate-400" />
        </div>
      )}
      {filters.length > 0 && (
        <div aria-label="Режим просмотра" role="group" className="flex flex-wrap gap-2">
          {filters.map((filter) => (
            <button key={filter} type="button" aria-pressed={active === filter}
              onClick={() => setActive(filter)}
              className={'min-h-11 rounded-xl border px-3 text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-indigo-600 ' +
                (active === filter ? 'border-indigo-200 bg-indigo-50 text-indigo-700' :
                  'border-slate-200 bg-white text-slate-600 hover:bg-slate-50')}>
              {filter}
            </button>
          ))}
        </div>
      )}
      <p className="text-xs text-slate-400">Фильтры подготовлены; загрузка и фильтрация данных появятся в следующих пакетах.</p>
    </div>
  );
}
