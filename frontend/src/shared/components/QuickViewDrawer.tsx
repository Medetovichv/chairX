import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { Link } from 'react-router';
import { ArrowLeft, ExternalLink, X } from 'lucide-react';

const DirtyContext = createContext<(dirty: boolean) => void>(() => {});
export const useQuickViewDirty = () => useContext(DirtyContext);
const LeaveContext = createContext<() => boolean>(() => true);
export const useQuickViewCanLeave = () => useContext(LeaveContext);

interface Props {
  title: string;
  detailUrl: string;
  onClose: () => void;
  children: ReactNode;
  previous?: () => void;
  next?: () => void;
}

/** Non-modal desktop split view; full-screen detail on mobile. */
export function QuickViewDrawer({ title, detailUrl, onClose, children, previous, next }: Props) {
  const [dirty, setDirty] = useState(false);
  const dirtyRef = useRef(false);
  dirtyRef.current = dirty;
  const allowLeave = () => {
    if (!dirtyRef.current) return true;
    if (!window.confirm('Есть несохранённые изменения. Продолжить без сохранения?')) return false;
    dirtyRef.current = false;
    setDirty(false);
    return true;
  };
  const guardedClose = () => { if (allowLeave()) { setDirty(false); onClose(); } };
  const guardedMove = (action?: () => void) => { if (action && allowLeave()) { setDirty(false); action(); } };
  const closeRef = useRef<HTMLButtonElement>(null);
  const previouslyFocused = useRef<HTMLElement | null>(null);
  useEffect(() => {
    previouslyFocused.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    closeRef.current?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') { if (allowLeave()) onClose(); }
      if (event.key === 'Tab' && window.matchMedia?.('(max-width: 1023px)').matches) {
        const nodes = Array.from(document.querySelectorAll<HTMLElement>('[data-quick-view] button:not([disabled]), [data-quick-view] a[href], [data-quick-view] input:not([disabled]), [data-quick-view] select:not([disabled]), [data-quick-view] textarea:not([disabled])')).filter(node => node.getClientRects().length > 0);
        if (!nodes.length) return;
        const first = nodes[0], last = nodes[nodes.length - 1];
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
        else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
      }
    };
    const onUnload = (event: BeforeUnloadEvent) => { if (dirtyRef.current) { event.preventDefault(); event.returnValue = ''; } };
    window.addEventListener('beforeunload', onUnload);
    window.addEventListener('keydown', onKey);
    return () => {
      window.removeEventListener('keydown', onKey);
      window.removeEventListener('beforeunload', onUnload);
      previouslyFocused.current?.focus();
    };
  }, [onClose]);
  const guardInternalLinks = (event: React.MouseEvent<HTMLElement>) => {
    if (!dirtyRef.current || event.defaultPrevented || event.button !== 0 ||
      event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
    const target = event.target;
    if (!(target instanceof Element) || !target.closest('a[href]')) return;
    if (!allowLeave()) { event.preventDefault(); event.stopPropagation(); }
  };
  return <DirtyContext.Provider value={setDirty}><LeaveContext.Provider value={allowLeave}><aside onClickCapture={guardInternalLinks} data-quick-view role="dialog" aria-modal={false} aria-label={'Быстрый просмотр: ' + title}
    className="fixed inset-0 z-40 flex min-w-0 flex-col overflow-hidden border-l border-slate-200 bg-white shadow-xl lg:sticky lg:top-4 lg:z-10 lg:h-[calc(100dvh-2rem)] lg:w-[min(34vw,520px)] lg:min-w-[400px] lg:shrink-0 lg:rounded-2xl lg:border">
    <header className="flex shrink-0 items-center gap-2 border-b border-slate-200 bg-white p-3 sm:p-4">
      <button ref={closeRef} type="button" onClick={guardedClose} aria-label="Назад к списку"
        className="inline-flex min-h-11 min-w-11 items-center justify-center rounded-xl hover:bg-slate-100">
        <ArrowLeft className="size-5 lg:hidden"/><X className="hidden size-5 lg:block"/>
      </button>
      <h2 className="min-w-0 flex-1 truncate font-semibold text-slate-900">{title}</h2>
      <div className="flex gap-1"><button type="button" aria-label="Предыдущая запись" disabled={!previous} onClick={() => guardedMove(previous)} className="min-h-11 min-w-11 rounded-lg px-2 py-2 disabled:opacity-30">←</button><button type="button" aria-label="Следующая запись" disabled={!next} onClick={() => guardedMove(next)} className="rounded-lg px-2 py-2 disabled:opacity-30">→</button></div>
      <Link to={detailUrl} aria-label="Полная страница" className="inline-flex min-h-11 items-center gap-2 rounded-xl px-3 text-sm font-medium text-blue-700 hover:bg-blue-50">
        <ExternalLink className="size-4" aria-hidden="true"/><span className="hidden sm:inline">Полная страница</span>
      </Link>
    </header>
    <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain p-4 pb-[max(1rem,env(safe-area-inset-bottom))] sm:p-5">
      {children}
    </div>
  </aside></LeaveContext.Provider></DirtyContext.Provider>;
}
