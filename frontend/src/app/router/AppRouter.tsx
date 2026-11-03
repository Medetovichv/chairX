import { lazy, Suspense } from 'react';
import { Navigate, Outlet, Route, Routes, useLocation, useParams } from 'react-router';
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
import { InventoryTransfersPage } from '../../features/inventory/pages/InventoryTransfersPage';
import { WarehouseManagementPage } from '../../features/inventory/pages/WarehouseManagementPage';
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
import { ReturnsPage } from '../../features/returns/pages/ReturnsPage';
import { CreateReturnPage } from '../../features/returns/pages/CreateReturnPage';
import { ReturnDetailPage } from '../../features/returns/pages/ReturnDetailPage';
const DefectsPage = lazy(() => import('../../features/returns/pages/DefectsPage').then(module => ({ default: module.DefectsPage })));

import { DefectDetailPage } from '../../features/returns/pages/DefectDetailPage';
const FinanceOverviewPage = lazy(() => import('../../features/finance/pages/FinanceOverviewPage').then(module => ({ default: module.FinanceOverviewPage })));
const FinanceOperationsPage = lazy(() => import('../../features/finance/pages/FinanceOperationsPage').then(module => ({ default: module.FinanceOperationsPage })));
const FinanceSourceDocumentPage = lazy(() => import('../../features/finance/pages/FinanceSourceDocumentPage').then(module => ({ default: module.FinanceSourceDocumentPage })));
const FinanceObligationsPage = lazy(() => import('../../features/finance/pages/FinanceObligationsPage').then(module => ({ default: module.FinanceObligationsPage })));
const FinanceAnalyticsPage = lazy(() => import('../../features/finance/pages/FinanceAnalyticsPage').then(module => ({ default: module.FinanceAnalyticsPage })));
const FinanceReportPage = lazy(() => import('../../features/finance/pages/FinanceReportPage').then(module => ({ default: module.FinanceReportPage })));
const CashFlowPage = lazy(() => import('../../features/finance/pages/CashFlowPage').then(module => ({ default: module.CashFlowPage })));
const ExpensesPage = lazy(() => import('../../features/finance/pages/ExpensesPage').then(module => ({ default: module.ExpensesPage })));
const FinanceTransferPage = lazy(() => import('../../features/finance/pages/FinanceTransferPage').then(module => ({ default: module.FinanceTransferPage })));
const OpeningBalancesPage = lazy(() => import('../../features/finance/pages/OpeningBalancesPage').then(module => ({ default: module.OpeningBalancesPage })));
const AdminUsersPage = lazy(() => import('../../features/admin/pages/AdminUsersPage').then(module => ({ default: module.AdminUsersPage })));
const AdminUserDetailPage = lazy(() => import('../../features/admin/pages/AdminUserDetailPage').then(module => ({ default: module.AdminUserDetailPage })));
const AdminRolesPage = lazy(() => import('../../features/admin/pages/AdminRolesPage').then(module => ({ default: module.AdminRolesPage })));
const AdminRoleDetailPage = lazy(() => import('../../features/admin/pages/AdminRoleDetailPage').then(module => ({ default: module.AdminRoleDetailPage })));
const AdminAuditPage = lazy(() => import('../../features/admin/pages/AdminAuditPage').then(module => ({ default: module.AdminAuditPage })));
const ReturnRefundPage = lazy(() => import('../../features/finance/pages/ReturnRefundPage').then(module => ({ default: module.ReturnRefundPage })));
import {
  firstAccessiblePath, hasRoutePermission, legacyRedirects,
  navigationItems, routeDefinitions,
} from '../../shared/lib/navigation';

/** Keep the navigation shell visible while rarely used pages load. */
function DeferredPage({ children }: { children: React.ReactNode }) {
  return <Suspense fallback={<div role="status" className="rounded-[20px] bg-white p-6 text-sm text-[#516977]">Загружаем раздел…</div>}>{children}</Suspense>;
}

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

function ReturnCreateGate(){const {hasPermission}=useAuth();return hasPermission('RETURNS_CREATE')?<CreateReturnPage/>:<ForbiddenPage/>;}
function ReturnRefundGate(){const {hasPermission}=useAuth(),{returnId}=useParams();return hasPermission('REFUNDS_CREATE')&&hasPermission('RETURNS_READ')?<DeferredPage><ReturnRefundPage key={returnId}/></DeferredPage>:<ForbiddenPage/>;}
function ReturnDetailGate(){const {hasPermission}=useAuth();return hasPermission('RETURNS_READ')?<ReturnDetailPage/>:<ForbiddenPage/>;}
function DefectDetailGate(){const {hasPermission}=useAuth();return hasPermission('DEFECTS_READ')?<DefectDetailPage/>:<ForbiddenPage/>;}

function PurchaseDetailGate() {
  const { hasPermission } = useAuth();
  return hasPermission('PURCHASE_READ') ? <PurchaseDetailPage /> : <ForbiddenPage />;
}

function AdminGate({permission,children}:{permission:string;children:React.ReactNode}) {
  const {user,hasPermission}=useAuth();
  return user?.roles.includes('ADMIN')&&hasPermission(permission)?children:<ForbiddenPage/>;
}
function ProtectedSection({ path }: { path: string }) {
  const { hasPermission,user } = useAuth();
  if(path.startsWith('/admin')&&!user?.roles.includes('ADMIN'))return <ForbiddenPage/>;
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
  if (path === '/returns') return <ReturnsPage />;
  if (path === '/returns/defects') return <DeferredPage><DefectsPage /></DeferredPage>;
  if (path === '/finance') return <DeferredPage><FinanceOverviewPage /></DeferredPage>;
  if (path === '/finance/expenses') return <DeferredPage><ExpensesPage /></DeferredPage>;
  if (path === '/finance/operations') return <DeferredPage><FinanceOperationsPage /></DeferredPage>;
  if (path === '/finance/obligations') return <DeferredPage><FinanceObligationsPage /></DeferredPage>;
  if (path === '/finance/analytics') return <DeferredPage><FinanceAnalyticsPage /></DeferredPage>;
  if (path === '/finance/report') return <DeferredPage><FinanceReportPage /></DeferredPage>;
  if (path === '/finance/cash-flow') return <DeferredPage><CashFlowPage /></DeferredPage>;
  if (path === '/finance/transfers') return <DeferredPage><FinanceTransferPage /></DeferredPage>;
  if (path === '/finance/opening-balances') return <DeferredPage><OpeningBalancesPage /></DeferredPage>;
  if(path==='/admin/users')return <DeferredPage><AdminUsersPage/></DeferredPage>;
  if(path==='/admin/roles')return <DeferredPage><AdminRolesPage/></DeferredPage>;
  if(path==='/admin/audit')return <DeferredPage><AdminAuditPage/></DeferredPage>;
  if (path === '/catalog') return <CatalogPage />;
  if (path === '/inventory') return <InventoryPage />;
  if (path === '/inventory/transfers') return <InventoryTransfersPage />;
  if (path === '/inventory/warehouses') return hasPermission('INVENTORY_READ') && hasPermission('WAREHOUSES_MANAGE') ?
    <WarehouseManagementPage /> : <ForbiddenPage />;
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
          <Route path="finance/documents/:kind/:documentId" element={<DeferredPage><FinanceSourceDocumentPage/></DeferredPage>}/>
          <Route path="returns/new" element={<ReturnCreateGate/>}/>
          <Route path="returns/:returnId/refund" element={<ReturnRefundGate/>}/>
          <Route path="returns/defects/:defectId" element={<DefectDetailGate/>}/>
          <Route path="returns/:returnId" element={<ReturnDetailGate/>}/>
          <Route path="sales/new" element={<SaleCreateGate />} />
          <Route path="sales/:saleId" element={<SaleDetailGate />} />
          <Route path="deliveries/new" element={<DeliveryCreateGate />} />
          <Route path="deliveries/:deliveryId" element={<DeliveryDetailGate />} />
          <Route path="catalog/:productId" element={<CatalogDetailGate />} />
          <Route path="admin/users/:userId" element={<AdminGate permission="USERS_READ"><DeferredPage><AdminUserDetailPage/></DeferredPage></AdminGate>}/>
          <Route path="admin/roles/:roleId" element={<AdminGate permission="ROLES_READ"><DeferredPage><AdminRoleDetailPage/></DeferredPage></AdminGate>}/>
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
