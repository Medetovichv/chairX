import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { Link, UNSAFE_DataRouterContext, useBlocker } from 'react-router';
import { ArrowDown, ArrowLeft, ChevronLeft, ChevronRight, ExternalLink, X } from 'lucide-react';

const DirtyContext = createContext<(dirty: boolean) => void>(() => {});
export const useQuickViewDirty = () => useContext(DirtyContext);
const FooterHostContext = createContext<HTMLElement | null>(null);
export const useQuickCreateFooterHost = () => useContext(FooterHostContext);
const LeaveContext = createContext<() => boolean>(() => true);
export const useQuickViewCanLeave = () => useContext(LeaveContext);

/** Only mount the data-router blocker where RouterProvider is present (unit tests use MemoryRouter). */
function HistoryGuard({ dirtyRef, discard }: { dirtyRef: { current: boolean }; discard: () => void }) {
  const context = useContext(UNSAFE_DataRouterContext);
  return context ? <ActiveHistoryGuard dirtyRef={dirtyRef} discard={discard}/> : null;
}
function ActiveHistoryGuard({ dirtyRef, discard }: { dirtyRef: { current: boolean }; discard: () => void }) {
  // The confirmation in allowLeave clears the ref synchronously, before navigation.
  // A stale render-time boolean would otherwise block that same confirmed navigation again.
  const blocker = useBlocker(() => dirtyRef.current);
  useEffect(() => {
    if (blocker.state !== 'blocked') return;
    if (window.confirm('Есть несохранённые изменения. Продолжить без сохранения?')) {
      discard();
      blocker.proceed();
    } else blocker.reset();
  }, [blocker, discard]);
  return null;
}

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
  const [footerHost,setFooterHost] = useState<HTMLDivElement|null>(null);
  const creating = detailUrl.endsWith('/new');
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
  const scrollRef = useRef<HTMLDivElement>(null);
  const [canScrollDown, setCanScrollDown] = useState(false);
  const updateScrollState = () => {
    const node = scrollRef.current;
    if (node) setCanScrollDown(node.scrollTop + node.clientHeight < node.scrollHeight - 24);
  };
  useEffect(() => {
    const node = scrollRef.current;
    if (!node) return;
    updateScrollState();
    if (typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(updateScrollState);
    observer.observe(node);
    for (const child of node.children) observer.observe(child);
    return () => observer.disconnect();
  }, [children]);
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;
  const previouslyFocused = useRef<HTMLElement | null>(null);
  useEffect(() => {
    previouslyFocused.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    closeRef.current?.focus();
    const onKey = (event: KeyboardEvent) => {
      // A portalled child dialog owns keyboard interaction until it closes.
      if(event.defaultPrevented||document.querySelector('[role="dialog"][aria-modal="true"]'))return;
      if (event.key === 'Escape') { if (allowLeave()) onCloseRef.current(); }
      if (event.key === 'Tab' && window.matchMedia?.('(max-width: 1279px)').matches) {
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
  }, []);
  const guardInternalLinks = (event: React.MouseEvent<HTMLElement>) => {
    if (!dirtyRef.current || event.defaultPrevented || event.button !== 0 ||
      event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
    const target = event.target;
    if (!(target instanceof Element) || !target.closest('a[href]')) return;
    if (!allowLeave()) { event.preventDefault(); event.stopPropagation(); }
  };
  return <FooterHostContext.Provider value={creating?footerHost:null}><DirtyContext.Provider value={setDirty}><LeaveContext.Provider value={allowLeave}><HistoryGuard dirtyRef={dirtyRef} discard={() => { dirtyRef.current = false; setDirty(false); }}/><aside onClickCapture={guardInternalLinks} data-quick-view role="dialog" aria-modal={false} aria-label={'Быстрый просмотр: ' + title}
    className="fixed inset-0 z-40 flex min-w-0 flex-col overflow-hidden border-l border-slate-200 bg-white shadow-xl xl:sticky xl:top-4 xl:z-10 xl:h-[calc(100dvh-10rem)] xl:w-[min(52vw,720px)] xl:min-w-0 xl:shrink-0 xl:rounded-2xl xl:border">
    <header className="flex shrink-0 items-center gap-2 border-b border-slate-200 bg-white p-3 sm:p-4">
      <button ref={closeRef} type="button" onClick={guardedClose} aria-label="Назад к списку"
        className="inline-flex min-h-11 min-w-11 items-center justify-center rounded-xl hover:bg-[#edf5f3] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#267f79]">
        <ArrowLeft className="size-5 xl:hidden"/><X className="hidden size-5 xl:block"/>
      </button>
      <h2 className="min-w-0 flex-1 truncate font-semibold text-slate-900">{title}</h2>
      {!creating&&<nav aria-label="Переход между записями" className="flex shrink-0 gap-1">
        <button type="button" aria-label="Предыдущая запись" disabled={!previous}
          onClick={() => guardedMove(previous)}
          className="inline-flex min-h-11 min-w-11 items-center justify-center rounded-xl text-[#416c82] hover:bg-[#edf5f3] disabled:cursor-not-allowed disabled:opacity-30 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#267f79]">
          <ChevronLeft aria-hidden="true" className="size-5"/>
        </button>
        <button type="button" aria-label="Следующая запись" disabled={!next}
          onClick={() => guardedMove(next)}
          className="inline-flex min-h-11 min-w-11 items-center justify-center rounded-xl text-[#416c82] hover:bg-[#edf5f3] disabled:cursor-not-allowed disabled:opacity-30 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#267f79]">
          <ChevronRight aria-hidden="true" className="size-5"/>
        </button>
      </nav>}
      <Link to={detailUrl} aria-label={creating ? "Открыть отдельную страницу создания" : "Полная страница"} className="inline-flex min-h-11 items-center gap-2 rounded-xl px-3 text-sm font-medium text-[#267f79] hover:bg-[#edf5f3] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#267f79]">
        <ExternalLink className="size-4" aria-hidden="true"/><span className="hidden sm:inline">{creating ? "Открыть отдельно" : "Полная страница"}</span>
      </Link>
    </header>
    <div ref={scrollRef} data-quick-view-scroll onScroll={updateScrollState} tabIndex={0} aria-label="Прокручиваемое содержимое панели"
      className={"min-h-0 min-w-0 flex-1 overflow-y-auto overscroll-y-auto scroll-smooth [scrollbar-gutter:stable] [touch-action:pan-y] p-4 sm:p-5 "+(creating?"pb-6":"pb-[max(5rem,env(safe-area-inset-bottom))] sm:pb-20")}>
      {children}
    </div>
    {canScrollDown && !creating && <button type="button" aria-label="Прокрутить панель вниз"
      onClick={() => { const node = scrollRef.current; if (node) node.scrollBy({ top: Math.max(320, node.clientHeight * 0.8), behavior: 'smooth' }); }}
      className="absolute bottom-4 right-5 z-20 flex size-11 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-700 shadow-lg hover:bg-blue-50 focus-visible:outline-2 focus-visible:outline-blue-600">
      <ArrowDown aria-hidden="true" className="size-5"/>
    </button>}
    {creating&&<div ref={setFooterHost} data-quick-create-footer role="region" aria-label={detailUrl.startsWith("/deliveries/") ? "Стоимость и создание доставки" : "Итог и сохранение продажи"}
      className="shrink-0 border-t border-[#dfe7eb] bg-white px-4 py-3 shadow-[0_-8px_24px_rgba(18,42,55,0.08)] sm:px-5 pb-[max(0.75rem,env(safe-area-inset-bottom))]"/>}
  </aside></LeaveContext.Provider></DirtyContext.Provider></FooterHostContext.Provider>;
}
