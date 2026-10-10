import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router';
import { useAuth } from '../../features/auth/AuthProvider';
import { AppLayout } from '../../layouts/AppLayout';
import { HomePage } from '../../pages/HomePage';
import { ComingSoonPage } from '../../pages/ComingSoonPage';
import { ForbiddenPage } from '../../pages/ForbiddenPage';
import { LoginPage, safeRedirect } from '../../pages/LoginPage';
import { NotFoundPage } from '../../pages/NotFoundPage';
import { navigationItems } from '../../shared/lib/navigation';

function RequireAuthentication() {
  const { isAuthenticated } = useAuth();
  const location = useLocation();
  return isAuthenticated
    ? <Outlet />
    : <Navigate to="/login" state={{ from: location.pathname + location.search }} replace />;
}

function ProtectedSection({ permissions }: { permissions: readonly string[] }) {
  const { hasAnyPermission } = useAuth();
  return hasAnyPermission(permissions) ? <ComingSoonPage /> : <ForbiddenPage />;
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
          {navigationItems.filter((item) => item.path !== '/').map((item) => (
            <Route key={item.path} path={item.path.slice(1)}
              element={<ProtectedSection permissions={item.permissions} />} />
          ))}
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  );
}

