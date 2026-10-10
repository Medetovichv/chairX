import { Route, Routes } from 'react-router';
import { AppLayout } from '../../layouts/AppLayout';
import { HomePage } from '../../pages/HomePage';
import { ComingSoonPage } from '../../pages/ComingSoonPage';
import { NotFoundPage } from '../../pages/NotFoundPage';
import { navigationItems } from '../../shared/lib/navigation';

export function AppRouter() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<HomePage />} />
        {navigationItems
          .filter((item) => item.path !== '/')
          .map((item) => (
            <Route
              key={item.path}
              path={item.path.slice(1)}
              element={<ComingSoonPage />}
            />
          ))}
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}

