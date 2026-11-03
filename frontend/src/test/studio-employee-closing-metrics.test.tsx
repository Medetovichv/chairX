import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { EmployeeClosingMetrics } from '../features/daily-closing/EmployeeClosingMetrics';

describe('Studio operational daily closing KPI cards',()=>{
  it('preserves exact already-formatted amounts and separate cash/bank figures',()=>{
    render(<EmployeeClosingMetrics sales="12 500 сом" orders={2} chairs={3}
      cash="6 000 сом" bank="4 000 сом" expenses="2 500 сом"/>);
    const section=screen.getByRole('region',{name:'Показатели дня'});
    for(const amount of ['12 500 сом','6 000 сом','4 000 сом','2 500 сом']) {
      expect(within(section).getByText(amount)).toBeInTheDocument();
    }
    expect(within(section).getByText('2 заказов · 3 кресел')).toBeInTheDocument();
    expect(within(section).getAllByRole('article')).toHaveLength(4);
    expect(section.textContent).not.toContain('🛒');
  });
  it('never derives cash balance or changes totals when source strings are unparseable',()=>{
    render(<EmployeeClosingMetrics sales="—" orders={0} chairs={0}
      cash="—" bank="1 сом" expenses="—"/>);
    expect(screen.getAllByText('—')).toHaveLength(3);
    expect(screen.getByText('1 сом')).toBeInTheDocument();
  });
});
