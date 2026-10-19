import {describe,it,expect,vi,afterEach} from 'vitest';
import {render,screen,waitFor,fireEvent} from '@testing-library/react';
import {QueryClientProvider} from '@tanstack/react-query';
import {createQueryClient} from '../shared/api/query';
import {ExpenseAccrualPanel} from '../features/finance/pages/ExpenseAccrualPanel';

const api=vi.hoisted(()=>({
 accruals:vi.fn(),expenses:vi.fn(),createAccrual:vi.fn(),linkAccrualPayment:vi.fn()
}));
vi.mock('../features/finance/api',()=>({financeApi:api}));
function show(canCreate:boolean) {
 return render(<QueryClientProvider client={createQueryClient()}>
  <ExpenseAccrualPanel from="2026-10-01" to="2026-11-01" canCreate={canCreate}/>
 </QueryClientProvider>);
}
afterEach(()=>vi.clearAllMocks());

describe('F14 accrual screen',()=>{
 it('shows recognized and paid sums without opening an expense creation form for readers',async()=>{
  api.accruals.mockResolvedValueOnce([{accrual:{
   id:'acc1',idempotencyKey:'key',category:'RENT',description:'October rent',amount:'3000',
   periodStart:'2026-10-01',periodEnd:'2026-11-01',createdBy:'manager'},
   recognized:'3000',paid:'1000',outstanding:'2000',prepaid:'0'}]);
  show(false);
  expect(await screen.findByText('October rent')).toBeInTheDocument();
  expect(screen.getByText('3 000 сом')).toBeInTheDocument();
  expect(screen.getByText('1 000 сом')).toBeInTheDocument();
  expect(screen.queryByRole('form',{name:'Добавить начисление'})).not.toBeInTheDocument();
  expect(api.expenses).not.toHaveBeenCalled();
 });
 it('creates an accrual independently from payments',async()=>{
  api.accruals.mockResolvedValue([]);
  api.expenses.mockResolvedValue([]);
  api.createAccrual.mockResolvedValue({id:'new'});
  show(true);
  const form=await screen.findByRole('form',{name:'Добавить начисление'});
  fireEvent.change(screen.getByLabelText('Сумма, сом'),{target:{value:'3000'}});
  fireEvent.change(screen.getByLabelText('Назначение'),{target:{value:'Rent for office'}});
  fireEvent.submit(form);
  await waitFor(()=>expect(api.createAccrual).toHaveBeenCalledOnce());
  expect(api.linkAccrualPayment).not.toHaveBeenCalled();
 });
});
