import { useEffect, useState } from 'react';
import { Armchair, ChevronDown, ChevronRight } from 'lucide-react';
import { NavLink, useLocation } from 'react-router';
import { useAuth } from '../features/auth/AuthProvider';
import { childrenOf, hasRoutePermission, isNavigationAllowed, navigationGroups } from '../shared/lib/navigation';
import { cn } from '../shared/lib/cn';

export function SidebarNavigation({ onNavigate }: { onNavigate?: () => void }) {
  const { hasAnyPermission, hasPermission } = useAuth();
  const { pathname } = useLocation();
  const [expanded, setExpanded] = useState<ReadonlySet<string>>(new Set<string>());

  useEffect(() => {
    const active = navigationGroups.flatMap((group) => group.items)
      .find((item) => pathname.startsWith(item.path + '/'));
    if (active) setExpanded((prev) => new Set([...prev, active.path]));
  }, [pathname]);

  function toggle(path: string) {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(path)) next.delete(path); else next.add(path);
      return next;
    });
  }

  return (
    <nav aria-label="Разделы ChairX" className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-3 pb-6">
      {navigationGroups.filter((group) => group.items.some((item) => isNavigationAllowed(item, hasAnyPermission))).map((group) => (
        <div key={group.title} className="mt-6">
          <p className="px-3 pb-2 text-[11px] font-semibold tracking-wider text-slate-400 uppercase">
            {group.title}
          </p>
          <div className="space-y-0.5">
            {group.items.filter((item) => isNavigationAllowed(item, hasAnyPermission)).map((item) => {
              const Icon = item.icon;
              const children = childrenOf(item.path).filter((child) => hasRoutePermission(child.permissions, hasPermission));
              const isCurrent = pathname === item.path || (item.path !== '/' && pathname.startsWith(item.path + '/'));
              const isExpanded = expanded.has(item.path);
              return (
                <div key={item.path}>
                  <div className={cn(
                    'group flex items-center rounded-xl',
                    isCurrent ? 'bg-indigo-50' : 'hover:bg-slate-50',
                  )}>
                    <NavLink
                      to={item.path}
                      end={item.path === '/'}
                      onClick={onNavigate}
                      aria-current={isCurrent ? 'page' : undefined}
                      className={cn(
                        'flex min-h-11 min-w-0 flex-1 items-center gap-3 rounded-xl px-3 text-sm font-medium',
                        'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600',
                        isCurrent ? 'text-indigo-700' : 'text-slate-600 hover:text-slate-900',
                      )}
                    >
                      <Icon aria-hidden="true" className="size-[19px] shrink-0" />
                      <span className="min-w-0 flex-1 truncate">{item.label}</span>
                    </NavLink>
                    {children.length > 0 && (
                      <button type="button" aria-label={(isExpanded ? 'Свернуть ' : 'Развернуть ') + item.label}
                        aria-expanded={isExpanded} onClick={() => toggle(item.path)}
                        className="mr-1 flex size-11 shrink-0 items-center justify-center rounded-lg text-slate-500 hover:bg-slate-100 focus-visible:outline-2 focus-visible:outline-indigo-600">
                        {isExpanded ? <ChevronDown aria-hidden="true" className="size-4" /> :
                          <ChevronRight aria-hidden="true" className="size-4" />}
                      </button>
                    )}
                  </div>
                  {children.length > 0 && isExpanded && (
                    <div className="ml-7 space-y-0.5 border-l border-slate-200 pl-2">
                      {children.map((child) => (
                        <NavLink key={child.path} to={child.path} end onClick={onNavigate}
                          className={({ isActive }) => cn(
                            'flex min-h-11 items-center rounded-lg px-3 text-sm focus-visible:outline-2 focus-visible:outline-indigo-600',
                            isActive ? 'bg-indigo-50 font-semibold text-indigo-700' :
                              'text-slate-500 hover:bg-slate-50 hover:text-slate-900',
                          )}>
                          {child.label}
                        </NavLink>
                      ))}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      ))}
    </nav>
  );
}

export function SidebarBrand() {
  return (
    <div className="flex h-19 shrink-0 items-center gap-3 border-b border-slate-100 px-5">
      <div className="flex size-10 items-center justify-center rounded-xl bg-indigo-600 text-white shadow-sm">
        <Armchair className="size-6" aria-hidden="true" />
      </div>
      <div>
        <div className="text-lg font-extrabold tracking-tight text-slate-900">ChairX</div>
        <div className="-mt-0.5 text-[11px] font-semibold tracking-[0.2em] text-slate-400 uppercase">
          Управление бизнесом
        </div>
      </div>
    </div>
  );
}

export function Sidebar() {
  return (
    <aside className="hidden h-screen w-66 shrink-0 flex-col border-r border-slate-200 bg-white lg:sticky lg:top-0 lg:flex">
      <SidebarBrand />
      <SidebarNavigation />
      <div className="border-t border-slate-100 px-6 py-4 text-xs text-slate-400">
        ChairX · ERP для малого бизнеса
      </div>
    </aside>
  );
}
