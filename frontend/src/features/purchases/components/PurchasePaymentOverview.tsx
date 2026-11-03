import { BadgeCheck, Banknote, Truck } from 'lucide-react';
import type { PaymentSummary } from '../types';
import { formatMoney } from '../validation';

export function PurchasePaymentOverview({ summary }: { summary: PaymentSummary }) {
  return <section aria-label="Обзор обязательств закупки" className="space-y-3">
    <div>
      <h2 className="text-lg font-bold tracking-tight text-[#182d38]">Расчёты по закупке</h2>
      <p className="mt-1 text-sm text-[#516977]">Точные суммы из реестра оплат, без округления и пересчёта на клиенте.</p>
    </div>
    <div className="grid gap-3 lg:grid-cols-2">
      <div className="flex min-h-40 flex-col justify-between rounded-2xl bg-[#172f3c] p-5 text-white">
        <div className="flex items-center gap-2 text-sm font-semibold text-[#c4d9dc]">
          <Banknote className="size-5" aria-hidden="true"/>Осталось поставщику
        </div>
        <strong className="mt-4 break-words text-3xl font-bold tracking-tight tabular-nums">{formatMoney(summary.remainingSupplier)}</strong>
        <p className="mt-3 text-xs text-[#d0e4e6]">Обязательства перед поставщиком: {formatMoney(summary.supplierTotal)} · Оплачено: {formatMoney(summary.paidSupplier)}</p>
      </div>
      <div className="flex min-h-40 flex-col justify-between rounded-2xl border border-[#dfe7eb] bg-white p-5 text-[#182d38]">
        <div className="flex items-center gap-2 text-sm font-semibold text-[#405b65]">
          <Truck className="size-5 text-[#286e66]" aria-hidden="true"/>Осталось по карго
        </div>
        <strong className="mt-4 break-words text-3xl font-bold tracking-tight tabular-nums">{formatMoney(summary.remainingCargo)}</strong>
        <p className="mt-3 text-xs text-[#516977]">Обязательства по карго: {formatMoney(summary.cargoTotal)} · Оплачено: {formatMoney(summary.paidCargo)}</p>
      </div>
    </div>
    <p className="flex items-start gap-2 text-xs text-[#516977]">
      <BadgeCheck className="mt-0.5 size-4 shrink-0 text-[#286e66]" aria-hidden="true"/>
      Обязательства поставщику и по карго показаны отдельно, без пересчёта сумм на клиенте.
    </p>
  </section>;
}
