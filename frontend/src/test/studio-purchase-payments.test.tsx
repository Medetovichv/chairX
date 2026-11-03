import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PurchasePaymentOverview } from '../features/purchases/components/PurchasePaymentOverview';
import type { PaymentSummary } from '../features/purchases/types';

const base:PaymentSummary={
  purchaseId:'12341234-1234-4123-8123-123412341234',
  supplierTotal:'35000.00',cargoTotal:'800.00',
  paidSupplier:'5000.00',paidCargo:'0.00',
  remainingSupplier:'30000.00',remainingCargo:'800.00',payments:[],
};

describe('Studio purchase payments overview',()=>{
  it('renders actual server amounts distinctly without combining supplier and cargo balances',()=>{
    render(<PurchasePaymentOverview summary={base}/>);
    expect(screen.getByText('Расчёты по закупке')).toBeInTheDocument();
    expect(screen.getByText(/Обязательства перед поставщиком:/)).toBeInTheDocument();
    expect(screen.getByText(/Обязательства по карго:/)).toBeInTheDocument();
    expect(screen.getByText(/Обязательства поставщику и по карго показаны отдельно/)).toBeInTheDocument();
  });
  it('does not invent a zero when the cargo balance is unknown',()=>{
    render(<PurchasePaymentOverview summary={{...base,cargoTotal:null,remainingCargo:null}}/>);
    expect(screen.getByText(/Обязательства по карго:/)).toBeInTheDocument();
    expect(screen.getByText(/Осталось по карго/)).toBeInTheDocument();
  });
});
