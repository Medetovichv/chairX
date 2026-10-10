import { Armchair, ChevronRight } from 'lucide-react';
import { NavLink } from 'react-router';
import { navigationGroups } from '../shared/lib/navigation';
import { cn } from '../shared/lib/cn';

export function SidebarNavigation({ onNavigate }: { onNavigate?: () => void }) {
  return (
    <nav aria-label="Разделы ChairX" className="flex-1 overflow-y-auto px-3 pb-6">
      {navigationGroups.map((group) => (
        <div key={group.title} className="mt-6">
          <p className="px-3 pb-2 text-[11px] font-semibold tracking-wider text-slate-400 uppercase">
            {group.title}
          </p>
          <div className="space-y-0.5">
            {group.items.map((item) => {
              const Icon = item.icon;
              return (
                <NavLink
                  key={item.path}
                  to={item.path}
                  end={item.path === '/'}
                  onClick={onNavigate}
                  className={({ isActive }) =>
                    cn(
                      'group flex min-h-11 items-center gap-3 rounded-xl px-3 text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600',
                      isActive
                        ? 'bg-indigo-50 text-indigo-700'
                        : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900',
                    )
                  }
                >
                  <Icon aria-hidden="true" className="size-[19px] shrink-0" />
                  <span className="min-w-0 flex-1 truncate">{item.label}</span>
                  <ChevronRight aria-hidden="true" className="size-3.5 opacity-0 group-hover:opacity-60" />
                </NavLink>
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

