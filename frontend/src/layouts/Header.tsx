import { Menu, MapPin } from 'lucide-react';
import { Button } from '../shared/ui/button';
import { formatBusinessDate } from '../shared/lib/format';

interface HeaderProps {
  title: string;
  onOpenMenu: () => void;
}

export function Header({ title, onOpenMenu }: HeaderProps) {
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
      <div className="hidden shrink-0 items-center gap-2 text-xs text-slate-500 md:flex">
        <MapPin className="size-4 text-slate-400" aria-hidden="true" />
        <span>Бишкек · {formatBusinessDate(new Date())}</span>
      </div>
    </header>
  );
}

