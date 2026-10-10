import { ShieldAlert } from 'lucide-react';
import { Link } from 'react-router';
import { Button } from '../shared/ui/button';

export function ForbiddenPage() {
  return (
    <div className="flex min-h-[65vh] items-center justify-center">
      <section className="max-w-lg text-center">
        <div className="mx-auto mb-5 flex size-16 items-center justify-center rounded-2xl bg-amber-50 text-amber-600">
          <ShieldAlert className="size-8" aria-hidden="true" />
        </div>
        <p className="font-bold text-amber-600">403</p>
        <h2 className="mt-2 text-3xl font-extrabold text-slate-900">Доступ запрещён</h2>
        <p className="mt-3 text-sm leading-7 text-slate-600">
          У вашей учётной записи нет разрешения на просмотр этого раздела.
        </p>
        <Button asChild className="mt-6"><Link to="/">На главную</Link></Button>
      </section>
    </div>
  );
}

