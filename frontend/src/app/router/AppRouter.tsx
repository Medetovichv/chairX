import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router';
import { useAuth } from '../../features/auth/AuthProvider';
import { AppLayout } from '../../layouts/AppLayout';
import { HomePage } from '../../pages/HomePage';
import { OperationalPage } from '../../pages/OperationalPage';
import { DailyClosingPage } from '../../pages/DailyClosingPage';
import { ForbiddenPage } from '../../pages/ForbiddenPage';
import { LoginPage, safeRedirect } from '../../pages/LoginPage';
import { NotFoundPage } from '../../pages/NotFoundPage';
import { CatalogPage } from '../../features/catalog/pages/CatalogPage';
import { ProductDetailPage } from '../../features/catalog/pages/ProductDetailPage';
import {
  firstAccessiblePath, hasRoutePermission, legacyRedirects,
  navigationItems, routeDefinitions,
} from '../../shared/lib/navigation';

function RequireAuthentication() {
  const { isAuthenticated } = useAuth();
  const location = useLocation();
  return isAuthenticated
    ? <Outlet />
    : <Navigate to="/login" state={{ from: location.pathname + location.search }} replace />;
}

function CatalogDetailGate() {
  const { hasPermission } = useAuth();
  return hasPermission('CATALOG_READ') ? <ProductDetailPage /> : <ForbiddenPage />;
}

function ProtectedSection({ path }: { path: string }) {
  const { hasPermission } = useAuth();
  const route = routeDefinitions.find((item) => item.path === path);
  if (!route) return <NotFoundPage />;
  if (['/purchases', '/returns', '/finance', '/admin'].includes(path)) {
    const target = firstAccessiblePath(path, hasPermission);
    if (!target) return <ForbiddenPage />;
    if (target !== path) return <Navigate to={target} replace />;
    if (path === '/admin') return <ForbiddenPage />;
  } else if (!hasRoutePermission(route.permissions, hasPermission)) {
    return <ForbiddenPage />;
  }
  if (path === '/catalog') return <CatalogPage />;
  return path === '/daily-closing' ? <DailyClosingPage /> : <OperationalPage path={path} />;
}

function LoginRoute() {
  const { isAuthenticated } = useAuth();
  const location = useLocation();
  const from = (location.state as { from?: unknown } | null)?.from;
  return isAuthenticated ? <Navigate to={safeRedirect(from)} replace /> : <LoginPage />;
}

export function AppRouter() {
  return (
    <Routes>
      <Route path="/login" element={<LoginRoute />} />
      <Route element={<RequireAuthentication />}>
        <Route element={<AppLayout />}>
          <Route index element={<HomePage />} />
          {routeDefinitions.filter((item) => item.path !== '/').map((item) => (
            <Route key={item.path} path={item.path.slice(1)} element={<ProtectedSection path={item.path} />} />
          ))}
          <Route path="catalog/:productId" element={<CatalogDetailGate />} />
          {Object.entries(legacyRedirects).map(([oldPath, target]) => (
            <Route key={oldPath} path={oldPath.slice(1)} element={<Navigate to={target} replace />} />
          ))}
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  );
}

export const mainMenuPaths = navigationItems.map((item) => item.path);
