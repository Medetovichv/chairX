import { useEffect, useRef } from 'react';
import { X } from 'lucide-react';
import { Button } from '../shared/ui/button';
import { SidebarBrand, SidebarNavigation } from './Sidebar';

interface MobileNavigationProps {
  open: boolean;
  onClose: () => void;
}

export function MobileNavigation({ open, onClose }: MobileNavigationProps) {
  const closeButton = useRef<HTMLButtonElement>(null);
  const dialogRef = useRef<HTMLElement>(null);
  const previousFocus = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (!open) return;
    previousFocus.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    closeButton.current?.focus();
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') { onClose(); return; }
      if (event.key !== 'Tab') return;
      const dialog = dialogRef.current;
      if (!dialog) return;
      const focusable = Array.from(dialog.querySelectorAll<HTMLElement>(
        'button:not([disabled]), a[href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
      )).filter(node => node.getClientRects().length > 0);
      if (!focusable.length) { event.preventDefault(); return; }
      const first = focusable[0]!;
      const last = focusable[focusable.length - 1]!;
      if (!dialog.contains(document.activeElement)) {
        event.preventDefault();
        first.focus();
      } else if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }
    document.addEventListener('keydown', onKeyDown);
    const before = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = before;
      previousFocus.current?.focus();
    };
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 lg:hidden">
      <button
        type="button"
        aria-label="Закрыть меню"
        className="absolute inset-0 w-full bg-[#071a25]/65"
        onClick={onClose}
      />
      <aside
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-label="Мобильная навигация"
        className="relative flex h-full w-[min(19rem,calc(100vw-3rem))] flex-col bg-[#172f3c] text-white shadow-2xl"
      >
        <div className="relative">
          <SidebarBrand />
          <Button
            ref={closeButton}
            aria-label="Закрыть боковое меню"
            variant="ghost"
            size="icon"
            className="absolute top-4 right-2 text-white hover:bg-white/15 focus-visible:outline-2 focus-visible:outline-white"
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
