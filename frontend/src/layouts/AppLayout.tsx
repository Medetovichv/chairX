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
    <div className="flex min-h-screen w-full min-w-0 max-w-full overflow-x-clip bg-[#f5f9fe]">
      <Sidebar />
      <div className="flex min-w-0 flex-1 flex-col">
        <Header title={routeLabel(pathname)} onOpenMenu={() => setMobileOpen(true)} />
        <main id="main-content" className="mx-auto w-full max-w-[1600px] min-w-0 flex-1 px-4 py-5 sm:px-7 sm:py-7 lg:px-8 lg:py-8">
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
