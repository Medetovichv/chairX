import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiClient } from '../shared/api/client';
import { salesApi } from '../features/sales/api';
import { deliveryApi } from '../features/deliveries/api';
import type { CancelSaleInput } from '../features/sales/cancellation';

describe('legacy cancellation transport requires structured audit reason (#128)',()=>{
  afterEach(()=>vi.restoreAllMocks());

  it('sends a required reason and optional comment on the legacy sale route',async()=>{
    const post=vi.spyOn(apiClient,'post').mockResolvedValue(undefined);
    const payload:CancelSaleInput={reason:'CUSTOMER_CHANGED_MIND',comment:'Клиент отменил до выдачи'};
    await salesApi.cancel('sale-id',payload);
    expect(post).toHaveBeenCalledExactlyOnceWith('/api/sales/sale-id/cancel',payload);
  });

  it('sends the same required reason contract on the legacy delivery route',async()=>{
    const post=vi.spyOn(apiClient,'post').mockResolvedValue(undefined);
    const payload:CancelSaleInput={reason:'DUPLICATE_ORDER',comment:null};
    await deliveryApi.cancel('delivery-id',payload);
    expect(post).toHaveBeenCalledExactlyOnceWith('/api/deliveries/delivery-id/cancel',payload);
  });
});
