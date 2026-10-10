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
}

export const navigationItems: readonly NavigationItem[] = [
  { path: '/', label: 'Главная', icon: House, group: 'Обзор' },
  { path: '/catalog', label: 'Каталог', icon: Package, group: 'Операции' },
  { path: '/inventory', label: 'Склады', icon: Warehouse, group: 'Операции' },
  { path: '/purchases', label: 'Закупки', icon: ClipboardList, group: 'Операции' },
  { path: '/sales', label: 'Продажи', icon: ShoppingCart, group: 'Операции' },
  { path: '/customers', label: 'Клиенты', icon: Users, group: 'Операции' },
  { path: '/suppliers', label: 'Поставщики', icon: Handshake, group: 'Операции' },
  { path: '/deliveries', label: 'Доставка', icon: Truck, group: 'Операции' },
  { path: '/returns', label: 'Возвраты', icon: RotateCcw, group: 'Операции' },
  { path: '/exchanges', label: 'Обмены', icon: ArrowLeftRight, group: 'Операции' },
  { path: '/defects', label: 'Брак', icon: BadgeAlert, group: 'Операции' },
  { path: '/expenses', label: 'Расходы', icon: ReceiptText, group: 'Учёт и управление' },
  { path: '/finance', label: 'Финансы', icon: WalletCards, group: 'Учёт и управление' },
  { path: '/daily-closing', label: 'Закрытие дня', icon: CalendarCheck2, group: 'Учёт и управление' },
  { path: '/admin', label: 'Администрирование', icon: Settings2, group: 'Учёт и управление' },
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

