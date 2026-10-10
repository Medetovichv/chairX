import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ApiClientError } from '../shared/api/api-error';
import { LoadingState } from '../shared/components/LoadingState';
import { ErrorState } from '../shared/components/ErrorState';
import { EmptyState } from '../shared/components/EmptyState';
import { ConfirmDialog } from '../shared/ui/confirm-dialog';
import { Input } from '../shared/ui/input';
import { Button } from '../shared/ui/button';

describe('Common UI', () => {
  it('shows accessible loading and empty states', () => {
    const { rerender } = render(<LoadingState />);
    expect(screen.getByRole('status')).toHaveTextContent('Загрузка данных');
    rerender(<EmptyState />);
    expect(screen.getByText('Пока нет данных')).toBeInTheDocument();
  });

  it('shows sanitized API error and supports retry', async () => {
    const retry = vi.fn();
    const user = userEvent.setup();
    render(<ErrorState error={new ApiClientError('http', 'ACCESS_DENIED', 403)} onRetry={retry} />);
    expect(screen.getByRole('alert')).toHaveTextContent('недостаточно прав');
    expect(screen.getByRole('alert')).not.toHaveTextContent('java.lang');
    await user.click(screen.getByRole('button', { name: 'Повторить' }));
    expect(retry).toHaveBeenCalledOnce();
  });

  it('renders shared Button and Input with semantic HTML', () => {
    render(<><Input aria-label="Название" /><Button>Сохранить</Button></>);
    expect(screen.getByRole('textbox', { name: 'Название' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Сохранить' })).toBeInTheDocument();
  });

  it('confirms a dialog only after user action', async () => {
    const user = userEvent.setup();
    const confirm = vi.fn();
    render(<ConfirmDialog open onOpenChange={vi.fn()} onConfirm={confirm} title="Удалить?" description="Действие нельзя отменить." />);
    expect(screen.getByRole('alertdialog')).toHaveTextContent('Удалить?');
    expect(confirm).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: 'Подтвердить' }));
    expect(confirm).toHaveBeenCalledOnce();
  });
});

