import { useEffect } from 'react';
import { X } from 'lucide-react';
import { Button } from '../shared/ui/button';
import { SidebarBrand, SidebarNavigation } from './Sidebar';

interface MobileNavigationProps {
  open: boolean;
  onClose: () => void;
}

export function MobileNavigation({ open, onClose }: MobileNavigationProps) {
  useEffect(() => {
    if (!open) return;
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', onKeyDown);
    const before = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = before;
    };
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 lg:hidden">
      <button
        type="button"
        aria-label="Закрыть меню"
        className="absolute inset-0 w-full bg-slate-900/50"
        onClick={onClose}
      />
      <aside
        role="dialog"
        aria-modal="true"
        aria-label="Мобильная навигация"
        className="relative flex h-full w-[min(19rem,calc(100vw-3rem))] flex-col bg-white shadow-2xl"
      >
        <div className="relative">
          <SidebarBrand />
          <Button
            aria-label="Закрыть боковое меню"
            variant="ghost"
            size="icon"
            className="absolute top-4 right-2"
            onClick={onClose}
          >
            <X className="size-5" aria-hidden="true" />
          </Button>
        </div>
        <SidebarNavigation onNavigate={onClose} />
      </aside>
    </div>
  );
}

