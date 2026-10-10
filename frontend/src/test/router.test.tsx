import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { AppRouter } from '../app/router/AppRouter';
import { childRoutes, navigationItems, routeFor } from '../shared/lib/navigation';

// Control ONLY the authenticated profile; the real router/layout/RBAC checks run.
const mockProfile = vi.hoisted(() => ({
  permissions: ['SALES_READ', 'DELIVERIES_READ', 'INVENTORY_READ', 'CUSTOMERS_READ',
    'DAILY_CLOSING_READ', 'CATALOG_READ', 'PURCHASE_READ', 'INVENTORY_RECEIVE',
    'PURCHASE_PAYMENTS_READ', 'SUPPLIERS_READ', 'RETURNS_READ', 'EXCHANGES_READ',
    'DEFECTS_READ', 'FINANCE_READ', 'EXPENSES_READ', 'USERS_READ', 'ROLES_READ', 'SALES_CREATE'],
  roles: ['ADMIN'],
}));
vi.mock('../features/auth/AuthProvider', () => ({
  useAuth: () => ({
    isAuthenticated: true, status: 'authenticated',
    user: { id: 'test-id', username: 'test', displayName: 'Test User',
      roles: mockProfile.roles, permissions: mockProfile.permissions },
    hasPermission: (permission: string) => mockProfile.permissions.includes(permission),
    hasAnyPermission: (permissions: readonly string[]) =>
      permissions.some((permission) => mockProfile.permissions.includes(permission)),
    login: vi.fn(), logout: vi.fn(),
  }),
}));

function renderAt(path = '/') {
  return render(<MemoryRouter initialEntries={[path]}><AppRouter /></MemoryRouter>);
}

beforeEach(() => {
  mockProfile.permissions = ['SALES_READ', 'DELIVERIES_READ', 'INVENTORY_READ', 'CUSTOMERS_READ',
    'DAILY_CLOSING_READ', 'CATALOG_READ', 'PURCHASE_READ', 'INVENTORY_RECEIVE',
    'PURCHASE_PAYMENTS_READ', 'SUPPLIERS_READ', 'RETURNS_READ', 'EXCHANGES_READ',
    'DEFECTS_READ', 'FINANCE_READ', 'EXPENSES_READ', 'USERS_READ', 'ROLES_READ', 'SALES_CREATE'];
  mockProfile.roles = ['ADMIN'];
});

describe('F02.1 navigation structure', () => {
  it('has exactly eleven main sections with no supplier/expense/exchange/defect sidebar item', () => {
    expect(navigationItems.map((item) => item.path)).toEqual([
      '/', '/sales', '/deliveries', '/inventory', '/customers', '/daily-closing',
      '/catalog', '/purchases', '/returns', '/finance', '/admin',
    ]);
    renderAt();
    const nav = screen.getByRole('navigation', { name: 'Разделы ChairX' });
    for (const item of navigationItems) {
      expect(within(nav).getByRole('link', { name: item.label })).toHaveAttribute('href', item.path);
    }
    for (const oldName of ['Поставщики', 'Расходы', 'Обмены', 'Брак']) {
      expect(within(nav).queryByRole('link', { name: oldName })).not.toBeInTheDocument();
    }
    expect(within(nav).getByText('Ежедневная работа')).toBeInTheDocument();
    expect(within(nav).getByText('Управление')).toBeInTheDocument();
    expect(within(nav).getByText('Система')).toBeInTheDocument();
  });

  it('keeps the F01 welcome page and omits fabricated KPI values', () => {
    renderAt();
    expect(screen.getByRole('heading', { name: 'Добро пожаловать в ChairX' })).toBeInTheDocument();
    expect(screen.getByText('Сейчас доступна навигация. Бизнес-разделы будут подключаться поэтапно.')).toBeInTheDocument();
    expect(screen.queryByText('Выручка сегодня')).not.toBeInTheDocument();
  });

  it('opens the operational sales page with future filter modes and no invented sales', async () => {
    const user = userEvent.setup();
    renderAt();
    const nav = screen.getByRole('navigation', { name: 'Разделы ChairX' });
    await user.click(within(nav).getByRole('link', { name: 'Продажи' }));
    expect(screen.getByRole('heading', { name: 'Продажи', level: 2 })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Статус оплаты' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Все' })).toHaveAttribute('aria-pressed', 'true');
    await user.click(screen.getByRole('button', { name: 'Черновики' }));
    expect(screen.getByRole('button', { name: 'Черновики' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByText('Данные пока не загружены')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '+ Новая продажа' })).toBeDisabled();
    await user.click(within(screen.getByRole('navigation', { name: 'Разделы ChairX' })).getByRole('link', { name: 'Главная' }));
    expect(screen.getByRole('heading', { name: 'Добро пожаловать в ChairX' })).toBeInTheDocument();
  });

  it('renders independent nested routes and breadcrumbs, and highlights their parent', async () => {
    const user = userEvent.setup();
    renderAt('/purchases/receipts');
    expect(screen.getByRole('heading', { name: 'Поступления', level: 2 })).toBeInTheDocument();
    const crumbs = screen.getByRole('navigation', { name: 'Навигационная цепочка' });
    expect(within(crumbs).getByText('Главная')).toBeInTheDocument();
    expect(within(crumbs).getByText('Закупки')).toBeInTheDocument();
    expect(within(crumbs).getByText('Поступления')).toHaveAttribute('aria-current', 'page');
    const side = screen.getByRole('navigation', { name: 'Разделы ChairX' });
    expect(within(side).getByRole('link', { name: 'Закупки' })).toHaveAttribute('aria-current', 'page');
    await user.click(within(side).getByRole('button', { name: 'Свернуть Закупки' }));
    expect(within(side).queryByRole('link', { name: 'Поступления' })).not.toBeInTheDocument();
    await user.click(within(side).getByRole('button', { name: 'Развернуть Закупки' }));
    expect(within(side).getByRole('link', { name: 'Поступления' })).toHaveAttribute('href', '/purchases/receipts');
  });

  it.each(childRoutes.map((route) => [route.path, route.label]))('opens child %s', (path, label) => {
    renderAt(path);
    expect(screen.getByRole('heading', { name: label, level: 2 })).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Навигационная цепочка' })).toBeInTheDocument();
  });

  it('closes mobile menu on navigation and Escape', async () => {
    const user = userEvent.setup();
    renderAt();
    await user.click(screen.getByRole('button', { name: 'Открыть меню' }));
    const dialog = screen.getByRole('dialog', { name: 'Мобильная навигация' });
    expect(dialog).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Развернуть Закупки' }));
    expect(within(dialog).getByRole('link', { name: 'Поступления' })).toBeInTheDocument();
    await user.click(within(dialog).getByRole('link', { name: 'Поступления' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.getByRole('heading', { name: 'Поступления', level: 2 })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Открыть меню' }));
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('renders a 404 for unknown URLs and never infers permission from roles', () => {
    expect(routeFor('/not-real')).toBeUndefined();
    renderAt('/not-real');
    expect(screen.getByRole('heading', { name: 'Страница не найдена', level: 2 })).toBeInTheDocument();
  });
});

describe('F02.1 RBAC and legacy paths', () => {
  it('lets expense-only employee reach finance/expenses but not finance overview or cash flow', async () => {
    mockProfile.roles = ['EMPLOYEE'];
    mockProfile.permissions = ['EXPENSES_READ'];
    renderAt('/finance');
    expect(screen.getByRole('heading', { name: 'Расходы', level: 2 })).toBeInTheDocument();
    const nav = screen.getByRole('navigation', { name: 'Разделы ChairX' });
    expect(within(nav).getByRole('link', { name: 'Финансы' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Движение денег' })).not.toBeInTheDocument();
    expect(within(nav).getByRole('button', { name: 'Свернуть Финансы' })).toHaveAttribute('aria-expanded', 'true');
    expect(within(nav).getByRole('link', { name: 'Расходы' })).toBeInTheDocument();
  });

  it('allows isolated INVENTORY_RECEIVE without PURCHASE_READ and without payment tabs', () => {
    mockProfile.permissions = ['INVENTORY_RECEIVE'];
    mockProfile.roles = ['WAREHOUSE'];
    renderAt('/purchases');
    expect(screen.getByRole('heading', { name: 'Поступления', level: 2 })).toBeInTheDocument();
    const side = screen.getByRole('navigation', { name: 'Разделы ChairX' });
    expect(within(side).getByRole('link', { name: 'Закупки' })).toBeInTheDocument();
    expect(within(side).queryByRole('link', { name: 'Оплаты' })).not.toBeInTheDocument();
    expect(screen.queryByRole('columnheader', { name: 'Сумма' })).not.toBeInTheDocument();
  });

  it('permits daily closing without FINANCE_READ and hides finance', () => {
    mockProfile.roles = ['EMPLOYEE'];
    mockProfile.permissions = ['DAILY_CLOSING_READ'];
    renderAt('/daily-closing');
    expect(screen.getByRole('heading', { name: 'Закрытие дня', level: 2 })).toBeInTheDocument();
    const nav = screen.getByRole('navigation', { name: 'Разделы ChairX' });
    expect(within(nav).queryByRole('link', { name: 'Финансы' })).not.toBeInTheDocument();
    expect(screen.getByText('CASH')).toBeInTheDocument();
  });

  it('uses independent returns/exchanges/defects permissions', () => {
    mockProfile.roles = ['CUSTOM_REPAIRS'];
    mockProfile.permissions = ['DEFECTS_READ'];
    renderAt('/returns');
    expect(screen.getByRole('heading', { name: 'Брак', level: 2 })).toBeInTheDocument();
    const tabs = screen.getByRole('navigation', { name: 'Вкладки раздела' });
    expect(within(tabs).queryByRole('link', { name: 'Возвраты' })).not.toBeInTheDocument();
    expect(within(tabs).queryByRole('link', { name: 'Обмены' })).not.toBeInTheDocument();
    expect(within(tabs).getByRole('link', { name: 'Брак' })).toBeInTheDocument();
  });

  it('shows only permitted admin child and never checks the ADMIN role name', () => {
    mockProfile.roles = ['CUSTOM'];
    mockProfile.permissions = ['ROLES_READ'];
    renderAt('/admin');
    expect(screen.getByRole('heading', { name: 'Роли и разрешения', level: 2 })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Сотрудники' })).not.toBeInTheDocument();
  });

  it.each([
    ['/suppliers', 'Поставщики'],
    ['/expenses', 'Расходы'],
    ['/exchanges', 'Обмены'],
    ['/defects', 'Брак'],
  ])('redirects legacy %s to its authorized nested route', (path, heading) => {
    renderAt(path);
    expect(screen.getByRole('heading', { name: heading, level: 2 })).toBeInTheDocument();
  });

  it('never turns a legacy redirect into an authorization bypass', () => {
    mockProfile.roles = ['EMPLOYEE'];
    mockProfile.permissions = ['SALES_READ'];
    renderAt('/expenses');
    expect(screen.getByRole('heading', { name: 'Доступ запрещён', level: 2 })).toBeInTheDocument();
  });

  it('unions permissions from multiple roles but does not trust their names', () => {
    mockProfile.roles = ['CUSTOM_READ', 'CUSTOM_ACCOUNTING'];
    mockProfile.permissions = ['EXPENSES_READ', 'PURCHASE_PAYMENTS_READ'];
    renderAt('/purchases');
    expect(screen.getByRole('heading', { name: 'Оплаты', level: 2 })).toBeInTheDocument();
  });
});
