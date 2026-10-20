import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { QuickViewDrawer, useQuickViewDirty } from './QuickViewDrawer';

function Editor() {
  const markDirty = useQuickViewDirty();
  return <button onClick={() => markDirty(true)}>Изменить</button>;
}
describe('QuickViewDrawer navigation', () => {
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
