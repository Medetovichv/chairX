import { Boxes, CircleCheck, CirclePause, Package } from 'lucide-react';
import type { Product, ProductPage } from '../types';

export function CatalogWorkspaceOverview({ page }: { page: ProductPage<Product> }) {
  const active = page.items.filter(model => model.active).length;
  const inactive = page.items.length - active;
  const metrics = [
    { name: 'На странице', value: page.items.length, detail: 'Показано моделей', icon: Boxes },
    { name: 'Активные', value: active, detail: 'Доступные в каталоге', icon: CircleCheck },
    { name: 'Неактивные', value: inactive, detail: 'Скрытые из активного ассортимента', icon: CirclePause },
  ];
  return <section aria-label="Обзор каталога" className="space-y-3">
    <div className="flex flex-wrap items-center justify-between gap-2">
      <div>
        <h2 className="text-lg font-bold tracking-tight text-[#182d38]">Обзор ассортимента</h2>
        <p className="mt-1 text-sm text-[#516977]">Статусы только показанных моделей. Наличие товара проверяйте в разделе «Склады».</p>
      </div>
      <span className="inline-flex items-center gap-2 rounded-lg bg-[#e6efed] px-3 py-2 text-xs font-semibold tabular-nums text-[#285f58]">
        <Package className="size-4" aria-hidden="true"/>
        Страница {page.page + 1} · всего {page.totalElements.toLocaleString('ru-RU')}
      </span>
    </div>
    <div className="grid gap-3 sm:grid-cols-3">
      {metrics.map(({ name, value, detail, icon: Icon }) =>
        <div key={name} className="flex min-h-32 flex-col justify-between rounded-2xl border border-[#dfe7eb] bg-white p-5">
          <div className="flex items-center justify-between gap-2">
            <p className="text-sm font-semibold text-[#405b65]">{name}</p>
            <Icon className="size-5 text-[#286e66]" aria-hidden="true"/>
          </div>
          <div className="mt-4">
            <strong className="text-3xl font-bold tabular-nums text-[#182d38]">{value.toLocaleString('ru-RU')}</strong>
            <p className="mt-1 text-xs text-[#516977]">{detail}</p>
          </div>
        </div>)}
    </div>
  </section>;
}
