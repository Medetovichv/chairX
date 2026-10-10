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
import { InventoryPage } from '../../features/inventory/pages/InventoryPage';
import { PurchasesPage } from '../../features/purchases/pages/PurchasesPage';
import { PurchaseDetailPage } from '../../features/purchases/pages/PurchaseDetailPage';
import { ReceivingPage } from '../../features/purchases/pages/ReceivingPage';
import { SuppliersPage } from '../../features/purchases/pages/SuppliersPage';
import { PurchasePaymentsPage } from '../../features/purchases/pages/PurchasePaymentsPage';
import { CustomersPage } from '../../features/customers/pages/CustomersPage';
import { CustomerDetailPage } from '../../features/customers/pages/CustomerDetailPage';
import { SalesPage } from '../../features/sales/pages/SalesPage';
import { SaleDetailPage } from '../../features/sales/pages/SaleDetailPage';
import { CreateSalePage } from '../../features/sales/pages/CreateSalePage';
import { DeliveriesPage } from '../../features/deliveries/pages/DeliveriesPage';
import { CreateDeliveryPage } from '../../features/deliveries/pages/CreateDeliveryPage';
import { DeliveryDetailPage } from '../../features/deliveries/pages/DeliveryDetailPage';
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

function CustomerDetailGate() {
  const { hasPermission } = useAuth();
  return hasPermission('CUSTOMERS_READ') ? <CustomerDetailPage /> : <ForbiddenPage />;
}

function SaleDetailGate() {
  const { hasPermission } = useAuth();
  return hasPermission('SALES_READ') ? <SaleDetailPage /> : <ForbiddenPage />;
}

function SaleCreateGate() {
  const { hasPermission } = useAuth();
  return hasPermission('SALES_CREATE') ? <CreateSalePage /> : <ForbiddenPage />;
}

function DeliveryCreateGate() {
  const {hasPermission}=useAuth();
  return hasPermission('DELIVERIES_READ')&&hasPermission('DELIVERIES_MANAGE')
    ? <CreateDeliveryPage/> : <ForbiddenPage/>;
}
function DeliveryDetailGate() {
  const {hasPermission}=useAuth();
  return hasPermission('DELIVERIES_READ') ? <DeliveryDetailPage/> : <ForbiddenPage/>;
}

function PurchaseDetailGate() {
  const { hasPermission } = useAuth();
  return hasPermission('PURCHASE_READ') ? <PurchaseDetailPage /> : <ForbiddenPage />;
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
  if (path === '/sales' && !hasPermission('SALES_READ')) {
    return hasPermission('SALES_CREATE') ? <Navigate to="/sales/new" replace /> : <ForbiddenPage />;
  }
  if (path === '/sales') return <SalesPage />;
  if (path === '/deliveries') return <DeliveriesPage />;
  if (path === '/catalog') return <CatalogPage />;
  if (path === '/inventory') return <InventoryPage />;
  if (path === '/purchases') return <PurchasesPage />;
  if (path === '/purchases/receipts') return <ReceivingPage />;
  if (path === '/purchases/suppliers') return <SuppliersPage />;
  if (path === '/purchases/payments') return <PurchasePaymentsPage />;
  if (path === '/customers') return <CustomersPage />;
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
          <Route path="sales/new" element={<SaleCreateGate />} />
          <Route path="sales/:saleId" element={<SaleDetailGate />} />
          <Route path="deliveries/new" element={<DeliveryCreateGate />} />
          <Route path="deliveries/:deliveryId" element={<DeliveryDetailGate />} />
          <Route path="catalog/:productId" element={<CatalogDetailGate />} />
          <Route path="customers/:customerId" element={<CustomerDetailGate />} />
          <Route path="purchases/:purchaseId" element={<PurchaseDetailGate />} />
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
