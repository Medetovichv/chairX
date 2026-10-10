import { AlertCircle } from 'lucide-react';
import { userFacingApiError } from '../api/api-error';
import { Button } from '../ui/button';

interface ErrorStateProps {
  error?: unknown;
  title?: string;
  onRetry?: () => void;
}

export function ErrorState({ error, title = 'Не удалось загрузить данные', onRetry }: ErrorStateProps) {
  return (
    <div role="alert" className="rounded-2xl border border-red-200 bg-white p-6 sm:p-8">
      <AlertCircle className="mb-3 size-7 text-red-600" aria-hidden="true" />
      <h2 className="text-base font-semibold text-slate-900">{title}</h2>
      <p className="mt-2 text-sm leading-6 text-slate-600">
        {userFacingApiError(error)}
      </p>
      {onRetry && <Button className="mt-5" variant="outline" onClick={onRetry}>Повторить</Button>}
    </div>
  );
}

