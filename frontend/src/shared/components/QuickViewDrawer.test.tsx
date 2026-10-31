import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, createMemoryRouter, RouterProvider, useNavigate, useLocation } from 'react-router';
import { QuickViewDrawer, useQuickViewDirty } from './QuickViewDrawer';

function Editor() {
  const markDirty = useQuickViewDirty();
  return <button onClick={() => markDirty(true)}>Изменить</button>;
}
function HistoryHarness() {
  const navigate = useNavigate();
  const location = useLocation();
  return <>
    <output data-testid="route">{location.search}</output>
    <button onClick={() => navigate(-1)}>Browser Back</button>
    <QuickViewDrawer title="Заказ" detailUrl="/sales/id" onClose={() => navigate('/sales')}><Editor/></QuickViewDrawer>
  </>;
}
describe('QuickViewDrawer navigation', () => {
  it('blocks Back until edits are discarded within a data router', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    const router = createMemoryRouter([{ path: '/sales', element: <HistoryHarness/> }], {
      initialEntries: ['/sales', '/sales?quick=id'], initialIndex: 1,
    });
    const { unmount } = render(<RouterProvider router={router}/>);
    fireEvent.click(screen.getByRole('button', { name: 'Изменить' }));
    fireEvent.click(screen.getByRole('button', { name: 'Browser Back' }));
    expect(await screen.findByTestId('route')).toHaveTextContent('?quick=id');
    expect(confirm).toHaveBeenCalled();
    confirm.mockReturnValue(true);
    fireEvent.click(screen.getByRole('button', { name: 'Browser Back' }));
    await waitFor(() => expect(screen.getByTestId('route').textContent).toBe(''));
    confirm.mockRestore();
    unmount();
  });
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
  it('keeps the entire right panel as an independently scrollable keyboard-accessible region', () => {
    const { container, unmount } = render(<MemoryRouter>
      <QuickViewDrawer title="Заказ" detailUrl="/sales/id" onClose={() => {}}>
        <div style={{ height: 2000 }}>Длинная форма</div>
      </QuickViewDrawer>
    </MemoryRouter>);
    const region = container.querySelector('[data-quick-view-scroll]');
    expect(region).not.toBeNull();
    expect(region?.className).toContain('overflow-y-auto');
    expect(region?.className).toContain('overscroll-y-auto');
    expect(region?.getAttribute('tabindex')).toBe('0');
    expect(region?.className).toContain('scrollbar-gutter:stable');
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
  it('protects Escape when edited and allows close after approval', () => {
    const close = vi.fn();
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    const { unmount } = render(<MemoryRouter><QuickViewDrawer title="Заказ" detailUrl="/sales/id" onClose={close}><Editor/></QuickViewDrawer></MemoryRouter>);
    fireEvent.click(screen.getByRole('button', { name: 'Изменить' }));
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(close).not.toHaveBeenCalled();
    confirm.mockReturnValue(true);
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(close).toHaveBeenCalledTimes(1);
    confirm.mockRestore();
    unmount();
  });
  it('provides reachable adjacent navigation controls for narrow screens', () => {
    const { container, unmount } = render(<MemoryRouter><QuickViewDrawer title="Заказ" detailUrl="/sales/id" onClose={() => {}} previous={() => {}} next={() => {}}><Editor/></QuickViewDrawer></MemoryRouter>);
    for (const label of ['Предыдущая запись', 'Следующая запись']) {
      const button = screen.getByRole('button', { name: label });
      expect(button.className).toContain('min-h-11');
      expect(button.className).toContain('min-w-11');
    }
    expect(container.querySelector('[data-quick-view]')).toBeTruthy();
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
