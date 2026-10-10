import { ArrowLeft, SearchX } from 'lucide-react';
import { Link } from 'react-router';
import { Button } from '../shared/ui/button';

export function NotFoundPage() {
  return (
    <div className="flex min-h-[65vh] items-center justify-center">
      <section className="max-w-xl text-center">
        <div className="mx-auto mb-5 flex size-16 items-center justify-center rounded-2xl bg-slate-200 text-slate-600">
          <SearchX className="size-8" aria-hidden="true" />
        </div>
        <p className="font-bold text-indigo-600">404</p>
        <h2 className="mt-2 text-3xl font-extrabold text-slate-900">Страница не найдена</h2>
        <p className="mt-3 text-sm leading-7 text-slate-600">Проверьте адрес или вернитесь на главную страницу.</p>
        <Button asChild className="mt-6">
          <Link to="/"><ArrowLeft className="size-4" aria-hidden="true" />На главную</Link>
        </Button>
      </section>
    </div>
  );
}

