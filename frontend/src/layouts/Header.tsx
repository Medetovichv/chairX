import { LogOut, Menu } from 'lucide-react';
import { useNavigate } from 'react-router';
import { useAuth } from '../features/auth/AuthProvider';
import { Button } from '../shared/ui/button';

interface HeaderProps {
  title: string;
  onOpenMenu: () => void;
}

function initials(name: string): string {
  return name.trim().split(/\s+/u).slice(0, 2).map(word => [...word][0] ?? '').join('').toLocaleUpperCase('ru');
}

export function Header({ title, onOpenMenu }: HeaderProps) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const onLogout = () => { logout(); navigate('/login', { replace: true }); };
  const isAdmin = user?.roles.includes('ADMIN');
  return (
    <header className="sticky top-0 z-30 flex min-h-17 items-center justify-between gap-3 border-b border-slate-200/80 bg-white/95 px-4 shadow-[0_1px_3px_rgba(15,23,42,0.03)] backdrop-blur-md sm:px-7 lg:px-8">
      <div className="flex min-w-0 items-center gap-3">
        <Button type="button" variant="ghost" size="icon" className="lg:hidden"
          aria-label="Открыть меню" onClick={onOpenMenu}>
          <Menu className="size-5" aria-hidden="true" />
        </Button>
        <div className="min-w-0">
          <p className="hidden text-[11px] font-medium text-slate-400 sm:block">ChairX / Рабочее пространство</p>
          <p className="truncate text-sm font-semibold text-slate-700">{title}</p>
        </div>
      </div>
      <div className="flex min-w-0 items-center gap-3">
        <span aria-hidden="true" className="flex size-9 shrink-0 items-center justify-center rounded-full bg-blue-100 text-xs font-extrabold text-blue-800">
          {initials(user?.displayName || user?.username || 'С')}
        </span>
        <div className="hidden min-w-0 max-w-48 sm:block">
          <p className="truncate text-sm font-semibold text-slate-900">{user?.displayName || user?.username}</p>
          <p className="truncate text-xs text-slate-500">{isAdmin ? 'Администратор' : 'Сотрудник'}</p>
        </div>
        <Button type="button" variant="ghost" size="sm" onClick={onLogout} aria-label="Выйти из ChairX"
          className="text-slate-500 hover:text-blue-700">
          <LogOut className="size-4" aria-hidden="true" />
          <span className="hidden md:inline">Выйти</span>
        </Button>
      </div>
    </header>
  );
}
