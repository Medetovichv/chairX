import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { QuickViewDrawer, useQuickViewDirty } from './QuickViewDrawer';

function Editor() {
  const markDirty = useQuickViewDirty();
  return <button onClick={() => markDirty(true)}>Изменить</button>;
}
describe('QuickViewDrawer navigation', () => {
  it('uses fullscreen mobile and split desktop layout styles', () => {
    const { container, unmount } = render(<MemoryRouter>
      <QuickViewDrawer title="Заказ" detailUrl="/sales/id" onClose={() => {}}><Editor/></QuickViewDrawer>
    </MemoryRouter>);
    const drawer = container.querySelector('[data-quick-view]');
    expect(drawer?.className).toContain('fixed inset-0');
    expect(drawer?.className).toContain('lg:sticky');
    expect(drawer?.getAttribute('aria-label')).toContain('Заказ');
    expect(screen.getByRole('button', { name: 'Предыдущая запись' }).hasAttribute('disabled')).toBe(true);
    unmount();
  });
  it('closes on Escape when clean', () => {
    const close = vi.fn();
    const { unmount } = render(<MemoryRouter><QuickViewDrawer title="Заказ" detailUrl="/sales/id" onClose={close}><Editor/></QuickViewDrawer></MemoryRouter>);
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(close).toHaveBeenCalledTimes(1);
    unmount();
  });
  it('protects edits on close and adjacent navigation', () => {
    const close = vi.fn();
    const next = vi.fn();
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    const { unmount } = render(<MemoryRouter><QuickViewDrawer title="Заказ" detailUrl="/sales/id" onClose={close} next={next}><Editor/></QuickViewDrawer></MemoryRouter>);
    fireEvent.click(screen.getByRole('button', { name: 'Изменить' }));
    fireEvent.click(screen.getByRole('button', { name: 'Назад к списку' }));
    fireEvent.click(screen.getByRole('button', { name: 'Следующая запись' }));
    expect(close).not.toHaveBeenCalled();
    expect(next).not.toHaveBeenCalled();
    expect(confirm).toHaveBeenCalledTimes(2);
    confirm.mockRestore();
    unmount();
  });
  it('blocks full-page navigation while edits are not discarded', () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    const { unmount } = render(<MemoryRouter initialEntries={['/sales']}>
      <QuickViewDrawer title="Заказ" detailUrl="/sales/detail" onClose={() => {}}><Editor/></QuickViewDrawer>
    </MemoryRouter>);
    fireEvent.click(screen.getByRole('button', { name: 'Изменить' }));
    fireEvent.click(screen.getByRole('link', { name: /Полная страница/ }));
    expect(confirm).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('button', { name: 'Назад к списку' })).toBeTruthy();
    confirm.mockRestore();
    unmount();
  });
  it('warns on browser unload with unsaved edits', () => {
    const { unmount } = render(<MemoryRouter><QuickViewDrawer title="Заказ" detailUrl="/sales/id" onClose={() => {}}><Editor/></QuickViewDrawer></MemoryRouter>);
    fireEvent.click(screen.getByRole('button', { name: 'Изменить' }));
    const event = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(event);
    expect(event.defaultPrevented).toBe(true);
    unmount();
  });
  it('allows moving to adjacent records after approval', () => {
    const next = vi.fn();
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const { unmount } = render(<MemoryRouter><QuickViewDrawer title="Заказ" detailUrl="/sales/id" onClose={() => {}} next={next}><Editor/></QuickViewDrawer></MemoryRouter>);
    fireEvent.click(screen.getByRole('button', { name: 'Изменить' }));
    fireEvent.click(screen.getByRole('button', { name: 'Следующая запись' }));
    expect(next).toHaveBeenCalledTimes(1);
    confirm.mockRestore();
    unmount();
  });
});
