import type { ReactNode } from 'react';

// A purely presentational component; no financial or operational state is invented.
export function StatusBadge({ children, tone = 'neutral' }: {
  children: ReactNode; tone?: 'neutral' | 'success' | 'warning' | 'danger';
}) {
  const colors = {
    neutral: 'bg-slate-100 text-slate-700',
    success: 'bg-emerald-50 text-emerald-700',
    warning: 'bg-amber-50 text-amber-800',
    danger: 'bg-red-50 text-red-700',
  };
  return <span className={'inline-flex items-center rounded-full px-2.5 py-1 text-xs font-semibold ' + colors[tone]}>{children}</span>;
}
