import { ArrowLeft, Construction } from 'lucide-react';
import { Link, useLocation } from 'react-router';
import { routeLabel } from '../shared/lib/navigation';
import { Button } from '../shared/ui/button';

export function ComingSoonPage() {
  const { pathname } = useLocation();
  const label = routeLabel(pathname);
  return (
    <div className="flex min-h-[65vh] items-center justify-center">
      <section className="w-full max-w-xl rounded-3xl border border-slate-200 bg-white p-7 text-center shadow-xs sm:p-12">
        <div className="mx-auto flex size-16 items-center justify-center rounded-2xl bg-indigo-50 text-indigo-600">
          <Construction className="size-8" aria-hidden="true" />
        </div>
        <div className="mt-6 text-xs font-bold tracking-[0.16em] text-indigo-600 uppercase">
          Скоро появится
        </div>
        <h2 className="mt-2 text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
          {label}
        </h2>
        <p className="mx-auto mt-4 max-w-sm text-sm leading-7 text-slate-600">
          Этот раздел ещё разрабатывается. Здесь пока нет реальных данных и доступных операций.
        </p>
        <Button className="mt-7" asChild>
          <Link to="/"><ArrowLeft className="size-4" aria-hidden="true" />На главную</Link>
        </Button>
      </section>
    </div>
  );
}

