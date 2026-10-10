import { LogOut, Menu } from 'lucide-react';
import { useNavigate } from 'react-router';
import { useAuth } from '../features/auth/AuthProvider';
import { Button } from '../shared/ui/button';

interface HeaderProps {
  title: string;
  onOpenMenu: () => void;
}

export function Header({ title, onOpenMenu }: HeaderProps) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  function onLogout() {
    logout();
    navigate('/login', { replace: true });
  }
  return (
    <header className="sticky top-0 z-30 flex min-h-18 items-center justify-between gap-3 border-b border-slate-200 bg-white/95 px-4 backdrop-blur-sm sm:px-7 lg:px-10">
      <div className="flex min-w-0 items-center gap-3">
        <Button
          type="button"
          variant="ghost"
          size="icon"
          className="lg:hidden"
          aria-label="Открыть меню"
          onClick={onOpenMenu}
        >
          <Menu className="size-5" aria-hidden="true" />
        </Button>
        <div className="min-w-0">
          <p className="hidden text-xs text-slate-400 sm:block">Рабочее пространство / ChairX</p>
          <h1 className="truncate text-lg font-bold tracking-tight text-slate-900 sm:text-xl">{title}</h1>
        </div>
      </div>
      <div className="flex min-w-0 shrink-0 items-center gap-2 sm:gap-3">
        <div className="block min-w-0 max-w-24 text-right sm:max-w-40">
          <p className="max-w-40 truncate text-sm font-semibold text-slate-800">{user?.displayName}</p>
          <p className="hidden max-w-40 truncate text-xs text-slate-500 sm:block">
            {user?.username} · {user?.roles.join(', ')}
          </p>
        </div>
        <Button type="button" variant="outline" size="sm" onClick={onLogout} aria-label="Выйти из ChairX">
          <LogOut className="size-4" aria-hidden="true" />
          <span className="hidden sm:inline">Выйти</span>
        </Button>
      </div>
    </header>
  );
}
