import { ArrowUpRight, Armchair, LayoutGrid, ShieldCheck, Smartphone } from 'lucide-react';
import { Link } from 'react-router';
import { navigationItems, isNavigationAllowed } from '../shared/lib/navigation';
import { useAuth } from '../features/auth/AuthProvider';

const highlights = [
  {
    icon: LayoutGrid,
    title: 'Всё в одном месте',
    description: 'Единая навигация для операций, остатков и управления.',
  },
  {
    icon: Smartphone,
    title: 'С телефона и компьютера',
    description: 'Интерфейс адаптируется под размер вашего экрана.',
  },
  {
    icon: ShieldCheck,
    title: 'Основа для безопасной работы',
    description: 'Доступ к разделам зависит от разрешений вашей учётной записи.',
  },
];

export function HomePage() {
  const { hasAnyPermission } = useAuth();
  return (
    <div className="space-y-9">
      <section className="relative overflow-hidden rounded-3xl border border-indigo-100 bg-white p-6 shadow-sm sm:p-9">
        <div className="pointer-events-none absolute -top-18 -right-18 size-65 rounded-full bg-indigo-50" />
        <div className="relative max-w-2xl">
          <div className="mb-6 inline-flex items-center gap-2 rounded-full bg-indigo-50 px-3 py-1.5 text-xs font-semibold text-indigo-700">
            <span className="size-1.5 rounded-full bg-indigo-500" aria-hidden="true" />
            Интерфейс в разработке
          </div>
          <div className="mb-5 flex size-14 items-center justify-center rounded-2xl bg-indigo-600 text-white">
            <Armchair className="size-8" aria-hidden="true" />
          </div>
          <h2 className="text-3xl font-extrabold tracking-tight text-slate-900 sm:text-4xl">
            Добро пожаловать в ChairX
          </h2>
          <p className="mt-4 max-w-xl text-base leading-7 text-slate-600">
            Рабочее пространство для магазина офисных кресел. Здесь будут объединены
            продажи, закупки, склад, клиенты и финансовый учёт.
          </p>
          <p className="mt-4 text-sm text-slate-500">
            Сейчас доступна навигация. Бизнес-разделы будут подключаться поэтапно.
          </p>
        </div>
      </section>

      <section aria-labelledby="advantages">
        <h2 id="advantages" className="mb-4 text-lg font-bold text-slate-900">Как устроено приложение</h2>
        <div className="grid gap-4 md:grid-cols-3">
          {highlights.map((highlight) => {
            const Icon = highlight.icon;
            return (
              <article key={highlight.title} className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
                <div className="flex size-11 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600">
                  <Icon className="size-5" aria-hidden="true" />
                </div>
                <h3 className="mt-4 font-semibold text-slate-900">{highlight.title}</h3>
                <p className="mt-2 text-sm leading-6 text-slate-500">{highlight.description}</p>
              </article>
            );
          })}
        </div>
      </section>

      <section aria-labelledby="sections">
        <div className="mb-4 flex items-center justify-between gap-3">
          <div>
            <h2 id="sections" className="text-lg font-bold text-slate-900">Разделы системы</h2>
            <p className="mt-1 text-sm text-slate-500">Все разделы пока находятся в подготовке.</p>
          </div>
        </div>
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          {navigationItems.filter((item) => item.path !== '/' && isNavigationAllowed(item, hasAnyPermission)).map((item) => {
            const Icon = item.icon;
            return (
              <Link
                key={item.path}
                to={item.path}
                className="group flex min-w-0 items-center gap-3 rounded-2xl border border-slate-200 bg-white px-4 py-4 transition-colors hover:border-indigo-200 hover:bg-indigo-50/40 focus-visible:outline-2 focus-visible:outline-indigo-600"
              >
                <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-slate-100 text-slate-600 group-hover:bg-indigo-100 group-hover:text-indigo-700">
                  <Icon className="size-5" aria-hidden="true" />
                </span>
                <span className="min-w-0 flex-1 truncate font-semibold text-slate-800">{item.label}</span>
                <ArrowUpRight className="size-4 shrink-0 text-slate-400 group-hover:text-indigo-600" aria-hidden="true" />
              </Link>
            );
          })}
        </div>
      </section>
    </div>
  );
}
