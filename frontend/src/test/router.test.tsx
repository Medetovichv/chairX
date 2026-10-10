import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { AppRouter } from '../app/router/AppRouter';
import { navigationItems } from '../shared/lib/navigation';

// F01 navigation regression: authenticated account with all permissions.
vi.mock('../features/auth/AuthProvider', () => ({
  useAuth: () => ({
    isAuthenticated: true,
    status: 'authenticated',
    user: { id: 'test-admin', username: 'test', displayName: 'Test Admin',
      roles: ['ADMIN'], permissions: [] },
    hasAnyPermission: () => true,
    hasPermission: () => true,
    logout: vi.fn(),
    login: vi.fn(),
  }),
}));

function renderAt(path = '/') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AppRouter />
    </MemoryRouter>,
  );
}

describe('ChairX navigation', () => {
  it('renders the welcome screen without invented KPI numbers', () => {
    renderAt();
    expect(screen.getByRole('heading', { name: 'Добро пожаловать в ChairX' })).toBeInTheDocument();
    expect(screen.getByText('Сейчас доступна навигация. Бизнес-разделы будут подключаться поэтапно.')).toBeInTheDocument();
    expect(screen.queryByText('Выручка сегодня')).not.toBeInTheDocument();
  });

  it('renders sidebar with every required section', () => {
    renderAt();
    const sidebar = screen.getByRole('navigation', { name: 'Разделы ChairX' });
    for (const item of navigationItems) {
      expect(within(sidebar).getByRole('link', { name: item.label })).toHaveAttribute(
        'href',
        item.path,
      );
    }
  });

  it.each(navigationItems.filter((item) => item.path !== '/'))(
    'opens section $label',
    ({ path, label }) => {
      renderAt(path);
      expect(screen.getByRole('heading', { name: label, level: 2 })).toBeInTheDocument();
      expect(screen.getByText(/Этот раздел ещё разрабатывается/)).toBeInTheDocument();
    },
  );

  it('navigates without a document reload', async () => {
    const user = userEvent.setup();
    renderAt();
    const sidebar = screen.getByRole('navigation', { name: 'Разделы ChairX' });
    await user.click(within(sidebar).getByRole('link', { name: 'Продажи' }));
    expect(screen.getByRole('heading', { name: 'Продажи', level: 2 })).toBeInTheDocument();
    await user.click(screen.getByRole('link', { name: 'На главную' }));
    expect(screen.getByRole('heading', { name: 'Добро пожаловать в ChairX' })).toBeInTheDocument();
  });

  it('opens and closes the mobile menu after navigation', async () => {
    const user = userEvent.setup();
    renderAt();
    await user.click(screen.getByRole('button', { name: 'Открыть меню' }));
    const dialog = screen.getByRole('dialog', { name: 'Мобильная навигация' });
    expect(dialog).toBeInTheDocument();
    await user.click(within(dialog).getByRole('link', { name: 'Склады' }));
    expect(screen.queryByRole('dialog', { name: 'Мобильная навигация' })).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Склады', level: 2 })).toBeInTheDocument();
  });

  it('closes mobile menu with Escape', async () => {
    const user = userEvent.setup();
    renderAt();
    await user.click(screen.getByRole('button', { name: 'Открыть меню' }));
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('renders a 404 page for an unknown route', () => {
    renderAt('/not-real');
    expect(screen.getByRole('heading', { name: 'Страница не найдена', level: 2 })).toBeInTheDocument();
  });
});

