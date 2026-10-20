import { beforeEach, describe, expect, it } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { usePersistedListState } from './usePersistedListState';

function List({ user }: { user: string }) {
  const [filters, setFilters] = usePersistedListState('test-filters', user, { search: '', page: 0 });
  return <><output data-testid="filters">{filters.search}:{filters.page}</output>
    <button onClick={() => setFilters({ search: 'chair', page: 2 })}>Filter</button></>;
}
describe('F16 list state persistence', () => {
  beforeEach(() => sessionStorage.clear());
  it('restores filters and pagination across a remount', () => {
    const first = render(<List user="one"/>);
    fireEvent.click(screen.getByRole('button', { name: 'Filter' }));
    expect(screen.getByTestId('filters').textContent).toBe('chair:2');
    first.unmount();
    render(<List user="one"/>);
    expect(screen.getByTestId('filters').textContent).toBe('chair:2');
  });
  it('does not share list preferences between employees', () => {
    const first = render(<List user="one"/>);
    fireEvent.click(screen.getByRole('button', { name: 'Filter' }));
    first.unmount();
    render(<List user="two"/>);
    expect(screen.getByTestId('filters').textContent).toBe(':0');
  });
});
