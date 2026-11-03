import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { SalesPipelineOverview } from '../features/sales/components/SalesPipelineOverview';

function renderPipeline(props: React.ComponentProps<typeof SalesPipelineOverview>) {
  return render(<MemoryRouter><SalesPipelineOverview {...props} /></MemoryRouter>);
}

describe('Studio Analytics sales pipeline', () => {
  it('renders server-provided totals and links to matching operational status filters', () => {
    renderPipeline({ drafts: 3, confirmed: 12, completed: 0 });
    const region = screen.getByRole('region', { name: 'Текущие показатели продаж' });
    const links = within(region).getAllByRole('link');
    expect(links).toHaveLength(3);
    expect(links.map(link => link.getAttribute('href'))).toEqual([
      '/sales?status=CONFIRMED&operational=true',
      '/sales?status=DRAFT&operational=true',
      '/sales?status=FULFILLED&operational=true',
    ]);
    expect(within(links[0]!).getByText('12')).toBeInTheDocument();
    expect(within(links[1]!).getByText('3')).toBeInTheDocument();
    expect(within(links[2]!).getByText('0')).toBeInTheDocument();
  });

  it('never treats a pending or failed API query as a confirmed zero', () => {
    renderPipeline({ drafts: 0, confirmedFailed: true, completed: undefined });
    const links = screen.getAllByRole('link');
    expect(within(links[0]!).getByRole('alert')).toHaveTextContent('Не удалось загрузить показатель');
    expect(within(links[1]!).getByText('0')).toBeInTheDocument();
    expect(within(links[2]!).getByText('…')).toBeInTheDocument();
    expect(screen.getAllByRole('alert')).toHaveLength(1);
  });

  it('labels multiple independent API failures without hiding the other status links', () => {
    renderPipeline({ draftsFailed: true, completedFailed: true, confirmed: 2 });
    expect(screen.getAllByRole('alert')).toHaveLength(2);
    expect(screen.getAllByRole('link')).toHaveLength(3);
    expect(screen.getByText('2')).toBeInTheDocument();
  });
});
