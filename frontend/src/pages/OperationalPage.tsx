import { useAuth } from '../features/auth/AuthProvider';
import { childrenOf, hasRoutePermission, routeFor, type RouteMeta } from '../shared/lib/navigation';
import { EmptyState } from '../shared/components/EmptyState';
import { PageHeader } from '../shared/components/PageHeader';
import { PageTabs } from '../shared/components/PageTabs';
import { PageToolbar } from '../shared/components/PageToolbar';
import { Button } from '../shared/ui/button';

// These are visual column contracts, not mock records or API response contracts.
// Backend integrations, real filters and mutations belong to F03+.
interface ScreenConfig {
  description: string;
  columns: readonly string[];
  filters?: readonly string[];
  search?: string;
  actionLabel?: string;
  actionPermission?: string;
}
const screens: Readonly<Record<string, ScreenConfig>> = {
  '/sales': {
    description: 'Рабочий список заказов: от черновика до выполнения.',
    columns: ['Номер заказа', 'Дата', 'Клиент', 'Товары', 'Количество', 'Сумма', 'Статус продажи', 'Статус оплаты', 'Статус доставки'],
    filters: ['Все', 'Черновики', 'Подтверждённые', 'Выполненные', 'Отменённые'],
    search: 'Поиск по номеру заказа',
    actionLabel: '+ Новая продажа', actionPermission: 'SALES_CREATE',
  },
  '/deliveries': {
    description: 'Планирование и отслеживание городских и региональных доставок.',
    columns: ['Заказ', 'Получатель', 'Телефон', 'Город / регион', 'Адрес', 'Плановая дата', 'Статус'],
    filters: ['Все', 'Сегодня', 'Предстоящие', 'В пути', 'Завершённые'],
    search: 'Поиск по заказу',
  },
  '/inventory': {
    description: 'Остатки на домашнем и офисном складе. Конкретные склады будут загружаться из API.',
    columns: ['Модель', 'Вариация', 'Дом', 'Офис', 'Всего', 'Зарезервировано', 'Доступно'],
    filters: ['Все склады', 'Дом', 'Офис'],
    search: 'Поиск модели или вариации',
  },
  '/customers': {
    description: 'Клиенты и история заказов. Имя может отсутствовать — оно не будет подменяться вымышленным.',
    columns: ['Имя', 'Телефон', 'WhatsApp', 'Город', 'Количество заказов', 'Последний заказ'],
    search: 'Поиск клиента по имени или телефону',
  },
  '/catalog': {
    description: 'Модели офисных кресел и вариации. Каталог не смешивается со складскими остатками.',
    columns: ['Название', 'Категория', 'Вариации', 'Цвета', 'Цена', 'Статус'],
    filters: ['Все', 'Активные', 'Неактивные'],
    search: 'Поиск товаров',
    actionLabel: '+ Новый товар', actionPermission: 'CATALOG_MANAGE',
  },
  '/purchases': {
    description: 'Закупки у поставщиков и контроль поступлений товара.',
    columns: ['Номер закупки', 'Поставщик', 'Дата', 'Статус', 'Позиции', 'Количество'],
    search: 'Поиск закупки',
  },
  '/purchases/receipts': {
    description: 'Поступления товара без доступа к закупочным ценам, карго и платежам.',
    columns: ['Закупка', 'Модель', 'Вариация', 'Заказано', 'Принято', 'Осталось', 'Статус'],
    filters: ['Все', 'Ожидают приёмки', 'Приняты'],
  },
  '/purchases/payments': {
    description: 'Оплаты закупок. Доступен только сотрудникам с PURCHASE_PAYMENTS_READ.',
    columns: ['Закупка', 'Поставщик', 'Оплата', 'Сумма', 'Дата', 'Статус'],
  },
  '/purchases/suppliers': {
    description: 'Справочник поставщиков закупок.',
    columns: ['Поставщик', 'Контакт', 'Телефон', 'Статус'],
    search: 'Поиск поставщика',
  },
  '/returns': {
    description: 'Возвраты по завершённым продажам.',
    columns: ['Номер', 'Продажа', 'Клиент', 'Товары', 'Дата', 'Статус'],
  },
  '/returns/exchanges': {
    description: 'Обмены между моделями и вариациями кресел.',
    columns: ['Обмен', 'Продажа', 'Возвращено', 'Выдано', 'Дата', 'Статус'],
  },
  '/returns/defects': {
    description: 'Брак, ожидание запчастей и решение по товару.',
    columns: ['Обращение', 'Модель', 'Вариация', 'Причина', 'Дата', 'Статус'],
    filters: ['Все', 'Открытые', 'Ожидание запчастей', 'Решённые'],
  },
  '/finance': {
    description: 'Обзор финансов организации. Реальные суммы пока не загружаются.',
    columns: ['Счёт', 'Период', 'Показатель', 'Значение'],
  },
  '/finance/expenses': {
    description: 'Операционные расходы без доступа к полному финансовому обзору.',
    columns: ['Дата', 'Категория', 'Описание', 'Сумма', 'Счёт'],
    search: 'Поиск расходов',
  },
  '/finance/cash-flow': {
    description: 'Движение денег по CASH и BANK.',
    columns: ['Дата', 'Счёт', 'Тип движения', 'Основание', 'Сумма'],
  },
  '/admin/users': {
    description: 'Сотрудники ChairX. Управление учётными записями будет добавлено позже.',
    columns: ['Логин', 'Сотрудник', 'Роли', 'Статус'],
    search: 'Поиск сотрудника',
  },
  '/admin/roles': {
    description: 'Пользовательские роли и разрешения из P23. Редактирование пока недоступно.',
    columns: ['Код роли', 'Название', 'Права', 'Сотрудников', 'Версия'],
  },
};

function accessibleTabs(path: string, hasPermission: (permission: string) => boolean): RouteMeta[] {
  const current = routeFor(path);
  if (!current) return [];
  const parent = current.parent ?? current.path;
  const main = routeFor(parent);
  if (!main || childrenOf(parent).length === 0) return [];
  const primary = parent === '/admin' ? [] : parent === '/purchases' ? ['PURCHASE_READ'] :
    parent === '/returns' ? ['RETURNS_READ'] : parent === '/finance' ? ['FINANCE_READ'] : main.permissions;
  const tabs = [
    ...(primary.some(hasPermission) ? [{ ...main, label: main.tabLabel ?? main.label, permissions: primary }] : []),
    ...childrenOf(parent),
  ];
  return tabs.filter((tab) => hasRoutePermission(tab.permissions, hasPermission));
}

export function OperationalPage({ path }: { path: string }) {
  const { hasPermission } = useAuth();
  const current = routeFor(path);
  const screen = screens[path];
  if (!current || !screen) return null;
  const tabs = accessibleTabs(path, hasPermission);
  return (
    <div className="space-y-6">
      <PageHeader title={current.label} description={screen.description}
        action={screen.actionLabel && screen.actionPermission && hasPermission(screen.actionPermission)
          ? <Button disabled title="Создание появится в следующем пакете">{screen.actionLabel}</Button>
          : undefined}
      />
      <PageTabs tabs={tabs} />
      {(screen.filters || screen.search) && (
        <PageToolbar key={path} filters={screen.filters} searchPlaceholder={screen.search} />
      )}
      <section aria-label="Структура списка" className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-xs">
        <div className="overflow-x-auto">
          <table className="w-full min-w-160 text-left text-sm">
            <caption className="sr-only">{current.label}: структура будущей таблицы</caption>
            <thead className="bg-slate-50">
              <tr>{screen.columns.map((column) => (
                <th key={column} scope="col" className="whitespace-nowrap px-4 py-3 font-semibold text-slate-600">{column}</th>
              ))}</tr>
            </thead>
          </table>
        </div>
        <div className="p-4 sm:p-6">
          <EmptyState title="Данные пока не загружены"
            description="В F02.1 подготовлен интерфейс списка. Подключение реального API запланировано в следующих пакетах." />
        </div>
      </section>
    </div>
  );
}
