import {
  ArrowLeftRight, BadgeAlert, CalendarCheck2, ClipboardList, Handshake,
  House, Package, ReceiptText, RotateCcw, Settings2, ShoppingCart,
  Truck, Users, Warehouse, WalletCards, type LucideIcon,
} from 'lucide-react';

export type NavigationGroup = 'Обзор' | 'Ежедневная работа' | 'Управление' | 'Система';

export interface RouteMeta {
  path: string;
  label: string;
  permissions: readonly string[];
  parent?: string;
  tabLabel?: string;
}

export interface NavigationItem extends RouteMeta {
  icon: LucideIcon;
  group: NavigationGroup;
}

export const navigationItems: readonly NavigationItem[] = [
  { path: '/', label: 'Главная', icon: House, group: 'Обзор', permissions: [] },
  { path: '/sales', label: 'Продажи', icon: ShoppingCart, group: 'Ежедневная работа', permissions: ['SALES_READ','SALES_CREATE'] },
  { path: '/deliveries', label: 'Доставки', icon: Truck, group: 'Ежедневная работа', permissions: ['DELIVERIES_READ'] },
  { path: '/inventory', label: 'Склады', icon: Warehouse, group: 'Ежедневная работа', permissions: ['INVENTORY_READ'] },
  { path: '/customers', label: 'Клиенты', icon: Users, group: 'Ежедневная работа', permissions: ['CUSTOMERS_READ'] },
  { path: '/daily-closing', label: 'Закрытие дня', icon: CalendarCheck2, group: 'Ежедневная работа', permissions: ['DAILY_CLOSING_READ', 'FINANCE_READ'] },
  { path: '/catalog', label: 'Каталог товаров', icon: Package, group: 'Управление', permissions: ['CATALOG_READ'] },
  { path: '/purchases', label: 'Закупки', tabLabel: 'Все закупки', icon: ClipboardList, group: 'Управление', permissions: ['PURCHASE_READ', 'INVENTORY_RECEIVE', 'PURCHASE_PAYMENTS_READ', 'SUPPLIERS_READ'] },
  { path: '/returns', label: 'Возвраты и брак', tabLabel: 'Возвраты', icon: RotateCcw, group: 'Управление', permissions: ['RETURNS_READ', 'RETURNS_CREATE', 'DEFECTS_READ', 'DEFECTS_MANAGE'] },
  { path: '/finance', label: 'Финансы', tabLabel: 'Обзор', icon: WalletCards, group: 'Управление', permissions: ['FINANCE_READ', 'EXPENSES_READ', 'EXPENSES_CREATE', 'FINANCE_TRANSFER', 'FINANCE_INITIALIZE'] },
  { path: '/admin', label: 'Администрирование', icon: Settings2, group: 'Система', permissions: ['USERS_READ', 'ROLES_READ'] },
];

export const childRoutes: readonly RouteMeta[] = [
  { path: '/inventory/transfers', parent: '/inventory', label: 'История перемещений', permissions: ['INVENTORY_READ'] },
  { path: '/inventory/warehouses', parent: '/inventory', label: 'Управление складами', permissions: ['INVENTORY_READ', 'WAREHOUSES_MANAGE'] },
  { path: '/purchases/receipts', parent: '/purchases', label: 'Поступления', permissions: ['INVENTORY_RECEIVE'] },
  { path: '/purchases/payments', parent: '/purchases', label: 'Оплаты', permissions: ['PURCHASE_PAYMENTS_READ'] },
  { path: '/purchases/suppliers', parent: '/purchases', label: 'Поставщики', permissions: ['SUPPLIERS_READ'] },
  { path: '/returns/defects', parent: '/returns', label: 'Брак', permissions: ['DEFECTS_READ','DEFECTS_MANAGE'] },
  { path: '/finance/expenses', parent: '/finance', label: 'Расходы', permissions: ['EXPENSES_READ','EXPENSES_CREATE'] },
  { path: '/finance/transfers', parent: '/finance', label: 'Переводы', permissions: ['FINANCE_TRANSFER'] },
  { path: '/finance/opening-balances', parent: '/finance', label: 'Начальные остатки', permissions: ['FINANCE_INITIALIZE'] },
  { path: '/finance/operations', parent: '/finance', label: 'Все операции', permissions: ['FINANCE_READ'] },
  { path: '/finance/obligations', parent: '/finance', label: 'Закупки и обязательства', permissions: ['FINANCE_READ'] },
  { path: '/finance/analytics', parent: '/finance', label: 'Аналитика', permissions: ['FINANCE_READ'] },
  { path: '/finance/report', parent: '/finance', label: 'Финансовый отчёт', permissions: ['FINANCE_READ'] },
  { path: '/finance/cash-flow', parent: '/finance', label: 'Движение денег', permissions: ['FINANCE_READ'] },
  { path: '/admin/users', parent: '/admin', label: 'Сотрудники', permissions: ['USERS_READ'] },
  { path: '/admin/roles', parent: '/admin', label: 'Роли и разрешения', permissions: ['ROLES_READ'] },
  { path: '/admin/audit', parent: '/admin', label: 'Журнал безопасности', permissions: ['ROLES_READ'] },
];

const returnCreate:RouteMeta={path:'/returns/new',label:'Оформить возврат',parent:'/returns',permissions:['RETURNS_CREATE']};
const returnDetail:RouteMeta={path:'/returns/:returnId',label:'Карточка возврата',parent:'/returns',permissions:['RETURNS_READ']};
const returnRefund:RouteMeta={path:'/returns/:returnId/refund',label:'Денежный возврат',parent:'/returns',permissions:['RETURNS_READ','REFUNDS_CREATE']};
const exchangeDetail:RouteMeta={path:'/returns/exchanges/:exchangeId',label:'Карточка обмена',parent:'/returns',permissions:['EXCHANGES_READ']};
const defectDetail:RouteMeta={path:'/returns/defects/:defectId',label:'Карточка брака',parent:'/returns/defects',permissions:['DEFECTS_READ']};
const deliveryCreate: RouteMeta={path:'/deliveries/new',label:'Оформить доставку',parent:'/deliveries',permissions:['DELIVERIES_READ','DELIVERIES_MANAGE']};
const deliveryDetail: RouteMeta={path:'/deliveries/:deliveryId',label:'Карточка доставки',parent:'/deliveries',permissions:['DELIVERIES_READ']};
const saleCreate: RouteMeta = {path:'/sales/new', label:'Новая продажа',parent:'/sales',permissions:['SALES_CREATE']};
const saleDetail: RouteMeta = {path:'/sales/:saleId',label:'Карточка продажи',parent:'/sales',permissions:['SALES_READ']};
const catalogDetail: RouteMeta = { path: '/catalog/:productId', label: 'Карточка модели', parent: '/catalog', permissions: ['CATALOG_READ'] };
const purchaseDetail: RouteMeta = {
  path: '/purchases/:purchaseId', label: 'Карточка закупки', parent: '/purchases', permissions: ['PURCHASE_READ'],
};
const customerDetail: RouteMeta = { path: '/customers/:customerId', label: 'Карточка клиента', parent: '/customers', permissions: ['CUSTOMERS_READ'] };
export const routeDefinitions: readonly RouteMeta[] = [...navigationItems, ...childRoutes];
export const navigationGroups = (['Обзор', 'Ежедневная работа', 'Управление', 'Система'] as const)
  .map((title) => ({ title, items: navigationItems.filter((item) => item.group === title) }));

export const legacyRedirects: Readonly<Record<string, string>> = {
  '/suppliers': '/purchases/suppliers',
  '/expenses': '/finance/expenses',
  '/exchanges': '/returns/exchanges',
  '/defects': '/returns/defects',
};

export function routeFor(path: string): RouteMeta | undefined {
  if(path==='/returns/new')return returnCreate;
  if(/^\/returns\/[^/]+\/refund$/.test(path))return {...returnRefund,path};
  if(/^\/returns\/exchanges\/[^/]+$/.test(path))return {...exchangeDetail,path};
  if(/^\/returns\/defects\/[^/]+$/.test(path))return {...defectDetail,path};
  if(/^\/returns\/[^/]+$/.test(path)&&!childRoutes.some(x=>x.path===path))return {...returnDetail,path};
  if(/^\/admin\/users\/[^/]+$/.test(path)) return {path,label:'Карточка сотрудника',parent:'/admin/users',permissions:['USERS_READ']};
  if(/^\/admin\/roles\/[^/]+$/.test(path)) return {path,label:'Карточка роли',parent:'/admin/roles',permissions:['ROLES_READ']};
  if (path==='/deliveries/new') return deliveryCreate;
  if (/^\/deliveries\/[^/]+$/.test(path)) return {...deliveryDetail,path};
  if (path==='/sales/new') return saleCreate;
  if (/^\/sales\/[^/]+$/.test(path)) return {...saleDetail,path};
  if (/^\/catalog\/[^/]+$/.test(path)) return { ...catalogDetail, path };
  if (/^\/customers\/[^/]+$/.test(path)) return { ...customerDetail, path };
  if (/^\/purchases\/[^/]+$/.test(path) && !childRoutes.some(route => route.path === path)) {
    return { ...purchaseDetail, path };
  }
  return routeDefinitions.find((route) => route.path === path);
}

export function childrenOf(parent: string): RouteMeta[] {
  return childRoutes.filter((route) => route.parent === parent);
}

export function hasRoutePermission(permissions: readonly string[], hasPermission: (code: string) => boolean): boolean {
  return permissions.length === 0 || permissions.some(hasPermission);
}

export function isNavigationAllowed(item: NavigationItem, hasAnyPermission: (permissions: readonly string[]) => boolean): boolean {
  return item.permissions.length === 0 || hasAnyPermission(item.permissions);
}

export function firstAccessiblePath(parent: string, hasPermission: (code: string) => boolean): string | null {
  const route = routeFor(parent);
  if (!route) return null;
  const direct = parent === '/admin' ? [] :
    parent === '/purchases' ? ['PURCHASE_READ'] :
    parent === '/returns' ? ['RETURNS_READ'] :
    parent === '/finance' ? ['FINANCE_READ'] : route.permissions;
  if (parent !== '/admin' && hasRoutePermission(direct, hasPermission)) return parent;
  const firstChild=childrenOf(parent).find((child)=>hasRoutePermission(child.permissions,hasPermission));
  if(firstChild)return firstChild.path;
  if(parent==='/returns'&&hasPermission('RETURNS_CREATE'))return '/returns/new';
  return null;
}

export function breadcrumbsFor(pathname: string): RouteMeta[] {
  const current = routeFor(pathname);
  if (!current || pathname === '/') return [];
  const parent = current.parent ? routeFor(current.parent) : undefined;
  const home = routeFor('/');
  return [home, parent, current].filter((item): item is RouteMeta => item !== undefined);
}

export function routeLabel(pathname: string): string {
  return routeFor(pathname)?.label ?? 'Страница не найдена';
}

export const nestedRouteIcons = { suppliers: Handshake, expenses: ReceiptText, exchanges: ArrowLeftRight, defects: BadgeAlert };
