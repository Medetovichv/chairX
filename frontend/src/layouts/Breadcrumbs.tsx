import { ChevronRight } from 'lucide-react';
import { Link, useLocation } from 'react-router';
import { breadcrumbsFor } from '../shared/lib/navigation';
import { useAuth } from '../features/auth/AuthProvider';
import { firstAccessiblePath } from '../shared/lib/navigation';

export function Breadcrumbs() {
  const { pathname } = useLocation();
  const { hasPermission } = useAuth();
  const crumbs = breadcrumbsFor(pathname);
  if (!crumbs.length) return null;
  return (
    <nav aria-label="Навигационная цепочка" className="mb-5">
      <ol className="flex flex-wrap items-center gap-2 text-sm text-slate-500">
        {crumbs.map((item, index) => {
          const last = index === crumbs.length - 1;
          const target = firstAccessiblePath(item.path, hasPermission);
          return (
            <li key={item.path} className="flex items-center gap-2">
              {index > 0 && <ChevronRight aria-hidden="true" className="size-3.5 text-slate-400" />}
              {last || target === null
                ? <span aria-current={last ? 'page' : undefined} className={last ? 'font-semibold text-slate-800' : ''}>{item.label}</span>
                : <Link to={target} className="hover:text-indigo-600 focus-visible:outline-2 focus-visible:outline-indigo-600">{item.label}</Link>}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
