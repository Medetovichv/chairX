import { LoaderCircle } from 'lucide-react';

export function LoadingState({ label = 'Загрузка данных…' }: { label?: string }) {
  return (
    <div role="status" aria-live="polite" className="flex min-h-52 flex-col items-center justify-center gap-3 rounded-2xl border border-slate-200 bg-white p-8 text-center text-sm text-slate-600">
      <LoaderCircle aria-hidden="true" className="size-7 animate-spin text-indigo-600" />
      <span>{label}</span>
    </div>
  );
}

