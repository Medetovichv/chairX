import { NavLink } from 'react-router';
import type { RouteMeta } from '../lib/navigation';
import { cn } from '../lib/cn';

export function PageTabs({ tabs }: { tabs: readonly RouteMeta[] }) {
  if (tabs.length < 2) return null;
  return (
    <nav aria-label="Вкладки раздела" className="flex gap-1 overflow-x-auto border-b border-slate-200" >
      {tabs.map((tab) => (
        <NavLink key={tab.path} to={tab.path} end
          className={({ isActive }) => cn(
            'flex min-h-11 shrink-0 items-center border-b-2 px-4 text-sm font-medium transition-colors',
            'focus-visible:outline-2 focus-visible:outline-indigo-600',
            isActive ? 'border-indigo-600 text-indigo-700' : 'border-transparent text-slate-500 hover:text-slate-900',
          )}>
          {tab.label}
        </NavLink>
      ))}
    </nav>
  );
}
