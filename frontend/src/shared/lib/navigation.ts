import {
  ArrowLeftRight,
  BadgeAlert,
  BookOpenCheck,
  CalendarCheck2,
  ClipboardList,
  Handshake,
  House,
  Package,
  ReceiptText,
  RotateCcw,
  Settings2,
  ShoppingCart,
  Truck,
  Users,
  Warehouse,
  WalletCards,
  type LucideIcon,
} from 'lucide-react';

export interface NavigationItem {
  path: string;
  label: string;
  icon: LucideIcon;
  group: 'Обзор' | 'Операции' | 'Учёт и управление';
  permissions: readonly string[];
}

export const navigationItems: readonly NavigationItem[] = [
  { path: '/', label: 'Главная', permissions: [], icon: House, group: 'Обзор' },
  { path: '/catalog', label: 'Каталог', permissions: ["CATALOG_READ"], icon: Package, group: 'Операции' },
  { path: '/inventory', label: 'Склады', permissions: ["INVENTORY_READ"], icon: Warehouse, group: 'Операции' },
  { path: '/purchases', label: 'Закупки', permissions: ["PURCHASE_READ"], icon: ClipboardList, group: 'Операции' },
  { path: '/sales', label: 'Продажи', permissions: ["SALES_READ"], icon: ShoppingCart, group: 'Операции' },
  { path: '/customers', label: 'Клиенты', permissions: ["CUSTOMERS_READ"], icon: Users, group: 'Операции' },
  { path: '/suppliers', label: 'Поставщики', permissions: ["SUPPLIERS_READ"], icon: Handshake, group: 'Операции' },
  { path: '/deliveries', label: 'Доставка', permissions: ["DELIVERIES_READ"], icon: Truck, group: 'Операции' },
  { path: '/returns', label: 'Возвраты', permissions: ["RETURNS_READ"], icon: RotateCcw, group: 'Операции' },
  { path: '/exchanges', label: 'Обмены', permissions: ["EXCHANGES_READ"], icon: ArrowLeftRight, group: 'Операции' },
  { path: '/defects', label: 'Брак', permissions: ["DEFECTS_READ"], icon: BadgeAlert, group: 'Операции' },
  { path: '/expenses', label: 'Расходы', permissions: ["EXPENSES_READ"], icon: ReceiptText, group: 'Учёт и управление' },
  { path: '/finance', label: 'Финансы', permissions: ["FINANCE_READ"], icon: WalletCards, group: 'Учёт и управление' },
  { path: '/daily-closing', label: 'Закрытие дня', permissions: ["DAILY_CLOSING_READ","FINANCE_READ"], icon: CalendarCheck2, group: 'Учёт и управление' },
  { path: '/admin', label: 'Администрирование', permissions: ["USERS_READ","ROLES_READ"], icon: Settings2, group: 'Учёт и управление' },
];

export const navigationGroups = [
  { title: 'Обзор', items: navigationItems.filter((item) => item.group === 'Обзор') },
  { title: 'Операции', items: navigationItems.filter((item) => item.group === 'Операции') },
  {
    title: 'Учёт и управление',
    items: navigationItems.filter((item) => item.group === 'Учёт и управление'),
  },
] as const;

export const foundationIcon = BookOpenCheck;

export function routeLabel(pathname: string): string {
  return navigationItems.find((item) => item.path === pathname)?.label ?? 'Страница не найдена';
}


export function isNavigationAllowed(item: NavigationItem, hasAnyPermission: (permissions: readonly string[]) => boolean): boolean {
  return item.permissions.length === 0 || hasAnyPermission(item.permissions);
}
