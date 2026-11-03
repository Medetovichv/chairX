import { ContactRound, Factory, CirclePause } from 'lucide-react';
import type { Supplier } from '../types';

/** These figures represent only the current server page, never all suppliers. */
export function SuppliersWorkspaceOverview({ suppliers, total, page }: {
  suppliers: Supplier[];
  total: number;
  page: number;
}) {
  const active = suppliers.filter(s => s.active).length;
  const contacts = suppliers.filter(s => Boolean(s.contactInformation?.trim())).length;
  const metrics = [
    {label:'Активные',value:active,detail:'Из показанных поставщиков',icon:Factory},
    {label:'Неактивные',value:suppliers.length-active,detail:'Сохранены для истории',icon:CirclePause},
    {label:'С контактами',value:contacts,detail:'Указаны контактные данные',icon:ContactRound},
  ];
  return <section aria-label="Обзор поставщиков" className="space-y-3">
    <div className="flex flex-wrap items-center justify-between gap-2">
      <div>
        <h2 className="text-lg font-bold tracking-tight text-[#182d38]">Обзор поставщиков</h2>
        <p className="mt-1 text-sm text-[#516977]">Показатели только по текущей странице, а не по всей базе.</p>
      </div>
      <span className="rounded-lg bg-[#e6efed] px-3 py-2 text-xs font-semibold tabular-nums text-[#285f58]">
        Страница {page+1} · {suppliers.length} из {total.toLocaleString('ru-RU')}
      </span>
    </div>
    <div className="grid gap-3 sm:grid-cols-3">
      {metrics.map(({label,value,detail,icon:Icon})=>
        <div key={label} className="flex min-h-32 flex-col justify-between rounded-2xl border border-[#dfe7eb] bg-white p-5">
          <div className="flex items-center justify-between">
            <span className="text-sm font-semibold text-[#405b65]">{label}</span>
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
