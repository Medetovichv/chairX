import { Banknote, CreditCard, ReceiptText, ShoppingBag } from 'lucide-react';

interface Props {
  sales: string;
  orders: number;
  chairs: number;
  cash: string;
  bank: string;
  expenses: string;
}

/** Display-only: monetary amounts are supplied by the existing exact-som closing logic. */
export function EmployeeClosingMetrics({ sales, orders, chairs, cash, bank, expenses }: Props) {
  const metrics = [
    { title: 'Продажи', value: sales, detail: orders + ' заказов · ' + chairs + ' кресел', icon: ShoppingBag },
    { title: 'Расчёт наличных', value: cash, detail: 'Из операционного отчёта', icon: Banknote },
    { title: 'Расчёт переводов', value: bank, detail: 'Из операционного отчёта', icon: CreditCard },
    { title: 'Расходы сотрудников', value: expenses, detail: 'Все сотрудники за день', icon: ReceiptText },
  ];
  return <section aria-label="Показатели дня" className="space-y-3">
    <div>
      <h2 className="text-lg font-bold tracking-tight text-[#182d38]">Показатели дня</h2>
      <p className="mt-1 text-sm text-[#516977]">Операционные суммы для выбранной даты, без клиентского пересчёта.</p>
    </div>
    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
      {metrics.map(({title,value,detail,icon:Icon})=>
        <article key={title} className="flex min-h-40 min-w-0 flex-col justify-between rounded-2xl border border-[#dfe7eb] bg-white p-5">
          <div className="flex items-center justify-between gap-2">
            <p className="text-sm font-semibold text-[#405b65]">{title}</p>
            <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-[#e9f3ef] text-[#286e66]">
              <Icon className="size-5" aria-hidden="true"/>
            </span>
          </div>
          <div className="mt-4">
            <strong className="block break-words text-2xl font-bold tracking-tight tabular-nums text-[#182d38]">{value}</strong>
            <p className="mt-2 text-xs text-[#516977]">{detail}</p>
          </div>
        </article>)}
    </div>
  </section>;
}
