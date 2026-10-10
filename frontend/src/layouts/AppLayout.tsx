import { useEffect, useState } from 'react';
import { Outlet, useLocation } from 'react-router';
import { routeLabel } from '../shared/lib/navigation';
import { Breadcrumbs } from './Breadcrumbs';
import { Header } from './Header';
import { MobileNavigation } from './MobileNavigation';
import { Sidebar } from './Sidebar';

export function AppLayout() {
  const { pathname } = useLocation();
  const [mobileOpen, setMobileOpen] = useState(false);

  useEffect(() => {
    setMobileOpen(false);
  }, [pathname]);

  return (
    <div className="flex min-h-screen min-w-0 bg-slate-50">
      <Sidebar />
      <div className="flex min-w-0 flex-1 flex-col">
        <Header title={routeLabel(pathname)} onOpenMenu={() => setMobileOpen(true)} />
        <main id="main-content" className="mx-auto w-full max-w-350 min-w-0 flex-1 px-4 py-6 sm:px-7 sm:py-8 lg:px-10 lg:py-10">
          <Breadcrumbs />
          <Outlet />
        </main>
        <footer className="px-4 py-5 text-center text-xs text-slate-400">
          ChairX · Кыргызстан · KGS
        </footer>
      </div>
      <MobileNavigation open={mobileOpen} onClose={() => setMobileOpen(false)} />
    </div>
  );
}
