import { useEffect, useState } from 'react';
import { Armchair, ChevronDown, ChevronRight, CircleDot } from 'lucide-react';
import { NavLink, useLocation } from 'react-router';
import { useAuth } from '../features/auth/AuthProvider';
import { childrenOf, hasRoutePermission, navigationGroups } from '../shared/lib/navigation';
import { cn } from '../shared/lib/cn';

export function SidebarNavigation({ onNavigate }: { onNavigate?: () => void }) {
  const { user, hasAnyPermission, hasPermission } = useAuth();
  const canDisplay = (item: { path: string; permissions: readonly string[] }) =>
    (item.path !== '/admin' || user?.roles.includes('ADMIN')) &&
    (item.permissions.length === 0 || hasAnyPermission(item.permissions));
  const { pathname } = useLocation();
  const [expanded, setExpanded] = useState<ReadonlySet<string>>(new Set<string>());

  useEffect(() => {
    const active = navigationGroups.flatMap(group => group.items)
      .find(item => pathname.startsWith(item.path + '/'));
    if (active) setExpanded(prev => new Set([...prev, active.path]));
  }, [pathname]);

  function toggle(path: string) {
    setExpanded(prev => {
      const next = new Set(prev);
      if (next.has(path)) next.delete(path); else next.add(path);
      return next;
    });
  }

  return <nav aria-label="Разделы ChairX"
    className="min-h-0 flex-1 space-y-6 overflow-y-auto overscroll-contain px-3 pb-6 pt-5">
    {navigationGroups.filter(group => group.items.some(canDisplay)).map(group =>
      <div key={group.title}>
        <p className="px-3 pb-2 text-[10px] font-bold uppercase tracking-[0.16em] text-[#9eb8c1]">
          {group.title}
        </p>
        <div className="space-y-1">
          {group.items.filter(canDisplay).map(item => {
            const Icon = item.icon;
            const children = childrenOf(item.path).filter(child => hasRoutePermission(child.permissions, hasPermission));
            const current = pathname === item.path || (item.path !== '/' && pathname.startsWith(item.path + '/'));
            const open = expanded.has(item.path);
            return <div key={item.path} className="min-w-0">
              <div className={cn(
                'group flex min-w-0 items-center rounded-xl transition-colors',
                current ? 'bg-[#a7e2ce] text-[#143b3a]' : 'text-[#c4d4d9] hover:bg-white/10 hover:text-white',
              )}>
                <NavLink to={item.path} end={item.path === '/'} onClick={onNavigate}
                  aria-current={current ? 'page' : undefined}
                  className={cn(
                    'flex min-h-11 min-w-0 flex-1 items-center gap-3 rounded-xl px-3 text-sm font-semibold',
                    'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white',
                  )}>
                  <Icon aria-hidden="true" className="size-[19px] shrink-0" />
                  <span className="min-w-0 flex-1 truncate">{item.label}</span>
                  {current && children.length === 0 && <span aria-hidden="true" className="size-1.5 shrink-0 rounded-full bg-[#215e55]" />}
                </NavLink>
                {children.length > 0 && <button type="button"
                  aria-label={(open ? 'Свернуть ' : 'Развернуть ') + item.label}
                  aria-expanded={open} onClick={() => toggle(item.path)}
                  className={cn(
                    'mr-1 flex size-10 shrink-0 items-center justify-center rounded-lg',
                    'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white',
                    current ? 'hover:bg-[#91d6be]' : 'hover:bg-white/10',
                  )}>
                  {open ? <ChevronDown aria-hidden="true" className="size-4" /> :
                    <ChevronRight aria-hidden="true" className="size-4" />}
                </button>}
              </div>
              {children.length > 0 && open && <div className="ml-5 mt-1 space-y-1 border-l border-[#54737b] pl-4">
                {children.map(child => <NavLink key={child.path} to={child.path} end onClick={onNavigate}
                  className={({ isActive }) => cn(
                    'flex min-h-10 items-center gap-2 rounded-lg px-3 text-xs font-medium transition-colors',
                    'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white',
                    isActive ? 'bg-white/15 font-bold text-[#b9f5db]' : 'text-[#b2c6cc] hover:bg-white/10 hover:text-white',
                  )}>
                  <CircleDot className="size-3 shrink-0 opacity-65" aria-hidden="true" />
                  <span className="min-w-0 break-words">{child.label}</span>
                </NavLink>)}
              </div>}
            </div>;
          })}
        </div>
      </div>)}
  </nav>;
}

export function SidebarBrand() {
  return <div className="flex h-21 shrink-0 items-center gap-3 border-b border-white/10 px-5">
    <div className="flex size-11 shrink-0 items-center justify-center rounded-2xl bg-[#a7e2ce] text-[#173b3a]">
      <Armchair className="size-6" aria-hidden="true" />
    </div>
    <div className="min-w-0">
      <div className="text-[25px] font-extrabold tracking-[-0.05em] text-white">Chair<span className="text-[#a7e2ce]">X</span></div>
      <p className="text-[10px] font-semibold uppercase tracking-[0.12em] text-[#a6c0c7]">Workspace</p>
    </div>
  </div>;
}

export function Sidebar() {
  return <aside className="hidden h-screen w-67 shrink-0 flex-col bg-[#172f3c] lg:sticky lg:top-0 lg:flex">
    <SidebarBrand />
    <SidebarNavigation />
    <div className="border-t border-white/10 px-6 py-5 text-xs text-[#adc4ca]">
      ChairX · Управление бизнесом
    </div>
  </aside>;
}
