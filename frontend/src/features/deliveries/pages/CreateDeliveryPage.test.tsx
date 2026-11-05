import { afterEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createMemoryRouter, RouterProvider, useSearchParams } from 'react-router';
import { QuickViewDrawer } from '../../../shared/components/QuickViewDrawer';
import { CreateDeliveryPage } from './CreateDeliveryPage';
import type { CreateDeliveryInput } from '../types';

vi.mock('../../auth/AuthProvider', () => ({
  useAuth: () => ({ hasPermission: () => true, user: { id: 'staff' } }),
}));
vi.mock('../pending-create', () => ({
  readPendingCreate: () => ({ kind: 'missing' }),
  savePendingCreate: () => true,
  clearPendingCreate: vi.fn(),
  isUnknownDeliveryCreation: () => false,
}));
vi.mock('../api', () => ({
  deliveryApi: { create: vi.fn(async () => ({ id: 'delivery-created', saleId: 'sale-created' })) },
  deliveryKeys: {
    lists: ['deliveries', 'list'],
    bySale: (id: string) => ['deliveries', 'sale', id],
  },
}));
vi.mock('../components/DeliveryForm', () => ({
  DeliveryForm: ({ onSubmit }: { onSubmit: (payload: CreateDeliveryInput) => void }) => <div>
    <label>Имя получателя<input aria-label="Имя получателя"/></label>
    <button onClick={() => onSubmit({} as CreateDeliveryInput)}>Создать доставку</button>
  </div>,
}));

function DeliveryQuickCreateHarness() {
  const [params, setParams] = useSearchParams();
  const selected = params.get('quick');
  return <>
    <output data-testid="current-delivery">{selected}</output>
    {selected && <QuickViewDrawer title="Новая доставка" detailUrl={'/deliveries/' + selected}
      onClose={() => setParams({})}>
      {selected === 'new' ? <CreateDeliveryPage embedded onCreated={id => setParams({ quick: id })}/> :
        <p>Карточка созданной доставки</p>}
    </QuickViewDrawer>}
  </>;
}

describe('delivery quick create navigation', () => {
  afterEach(() => vi.restoreAllMocks());
  it('navigates to the created delivery without asking to discard successfully saved edits', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    const router = createMemoryRouter([{ path: '/deliveries', element: <DeliveryQuickCreateHarness/> }], {
      initialEntries: ['/deliveries?quick=new'],
    });
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const view = render(<QueryClientProvider client={client}><RouterProvider router={router}/></QueryClientProvider>);
    fireEvent.change(screen.getByRole('textbox', { name: 'Имя получателя' }), {
      target: { value: 'Клиент' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Создать доставку' }));
    await waitFor(() => expect(screen.getByTestId('current-delivery')).toHaveTextContent('delivery-created'));
    expect(screen.getByText('Карточка созданной доставки')).toBeInTheDocument();
    expect(confirm).not.toHaveBeenCalled();
    view.unmount();
  });
});
