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
}

export interface NavigationItem extends RouteMeta {
  icon: LucideIcon;
  group: NavigationGroup;
}

// Single source of truth for links, route authorization, subnav and breadcrumbs.
// Parent permissions are the union of the independent backend read permissions.
export const navigationItems: readonly NavigationItem[] = [
  { path: '/', label: 'Главная', icon: House, group: 'Обзор', permissions: [] },
  { path: '/sales', label: 'Продажи', icon: ShoppingCart, group: 'Ежедневная работа', permissions: ['SALES_READ'] },
  { path: '/deliveries', label: 'Доставки', icon: Truck, group: 'Ежедневная работа', permissions: ['DELIVERIES_READ'] },
  { path: '/inventory', label: 'Склады', icon: Warehouse, group: 'Ежедневная работа', permissions: ['INVENTORY_READ'] },
  { path: '/customers', label: 'Клиенты', icon: Users, group: 'Ежедневная работа', permissions: ['CUSTOMERS_READ'] },
  { path: '/daily-closing', label: 'Закрытие дня', icon: CalendarCheck2, group: 'Ежедневная работа', permissions: ['DAILY_CLOSING_READ', 'FINANCE_READ'] },
  { path: '/catalog', label: 'Каталог товаров', icon: Package, group: 'Управление', permissions: ['CATALOG_READ'] },
  { path: '/purchases', label: 'Закупки', icon: ClipboardList, group: 'Управление', permissions: ['PURCHASE_READ', 'INVENTORY_RECEIVE', 'PURCHASE_PAYMENTS_READ', 'SUPPLIERS_READ'] },
  { path: '/returns', label: 'Возвраты и брак', icon: RotateCcw, group: 'Управление', permissions: ['RETURNS_READ', 'EXCHANGES_READ', 'DEFECTS_READ'] },
  { path: '/finance', label: 'Финансы', icon: WalletCards, group: 'Управление', permissions: ['FINANCE_READ', 'EXPENSES_READ'] },
  { path: '/admin', label: 'Администрирование', icon: Settings2, group: 'Система', permissions: ['USERS_READ', 'ROLES_READ'] },
];

// The parent URL is a real page only where its own permission permits it.
// Purchase receiving intentionally requires INVENTORY_RECEIVE, not PURCHASE_READ.
export const childRoutes: readonly RouteMeta[] = [
  { path: '/purchases/receipts', parent: '/purchases', label: 'Поступления', permissions: ['INVENTORY_RECEIVE'] },
  { path: '/purchases/payments', parent: '/purchases', label: 'Оплаты', permissions: ['PURCHASE_PAYMENTS_READ'] },
  { path: '/purchases/suppliers', parent: '/purchases', label: 'Поставщики', permissions: ['SUPPLIERS_READ'] },
  { path: '/returns/exchanges', parent: '/returns', label: 'Обмены', permissions: ['EXCHANGES_READ'] },
  { path: '/returns/defects', parent: '/returns', label: 'Брак', permissions: ['DEFECTS_READ'] },
  { path: '/finance/expenses', parent: '/finance', label: 'Расходы', permissions: ['EXPENSES_READ'] },
  { path: '/finance/cash-flow', parent: '/finance', label: 'Движение денег', permissions: ['FINANCE_READ'] },
  { path: '/admin/users', parent: '/admin', label: 'Сотрудники', permissions: ['USERS_READ'] },
  { path: '/admin/roles', parent: '/admin', label: 'Роли и разрешения', permissions: ['ROLES_READ'] },
];

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
  // /admin has no standalone overview; /returns, /purchases and /finance do.
  const direct = parent === '/admin' ? [] :
    parent === '/purchases' ? ['PURCHASE_READ'] :
    parent === '/returns' ? ['RETURNS_READ'] :
    parent === '/finance' ? ['FINANCE_READ'] : route.permissions;
  if (hasRoutePermission(direct, hasPermission)) return parent;
  return childrenOf(parent).find((child) => hasRoutePermission(child.permissions, hasPermission))?.path ?? null;
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

// Icons are attached only to actual sidebar items; nested routes have their own labels.
export const nestedRouteIcons = { suppliers: Handshake, expenses: ReceiptText, exchanges: ArrowLeftRight, defects: BadgeAlert };
