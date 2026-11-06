import { apiClient } from '../../shared/api/client';
import type {CancelSaleInput} from '../sales/cancellation';
import type { CreateDeliveryInput, DeliveryFilters, DeliveryPage, DeliveryResponse } from './types';
export const DELIVERY_PAGE_SIZE = 20;
const enc = (id: string) => encodeURIComponent(id);
export const deliveryKeys = {
  lists: ['deliveries', 'list'] as const,
  list: (f: DeliveryFilters) => [
    'deliveries','list',f.status,f.from,f.to,f.plannedFrom,f.plannedTo,f.cityRegion,f.page,f.operational,f.pendingOnly??false,f.deliveredOn??'',DELIVERY_PAGE_SIZE,
  ] as const,
  detail: (id: string) => ['deliveries','detail',id] as const,
  bySale: (saleId: string) => ['deliveries','by-sale',saleId] as const,
};
export const deliveryApi = {
  list: (filters: DeliveryFilters): Promise<DeliveryPage> => {
    const qs = new URLSearchParams({ page: String(filters.page), size: String(DELIVERY_PAGE_SIZE) });
    for (const key of ['status','from','to','plannedFrom','plannedTo','cityRegion'] as const) {
      if (filters[key].trim()) qs.set(key, filters[key].trim());
    }
    if(filters.pendingOnly)qs.set('pendingOnly','true');
    if(filters.deliveredOn)qs.set('deliveredOn',filters.deliveredOn);
    if(filters.operational)qs.set('operational','true');
    return apiClient.get('/api/deliveries?' + qs.toString());
  },
  get: (id: string): Promise<DeliveryResponse> => apiClient.get('/api/deliveries/' + enc(id)),
  bySale: (saleId: string, signal?: AbortSignal): Promise<DeliveryResponse> =>
    apiClient.get('/api/deliveries/by-sale/' + enc(saleId),signal ? {signal} : undefined),
  create: (body: CreateDeliveryInput): Promise<DeliveryResponse> => apiClient.post('/api/deliveries',body),
  plannedDate: (id: string, plannedDeliveryDate: string | null): Promise<DeliveryResponse> =>
    apiClient.put('/api/deliveries/' + enc(id) + '/planned-date',{plannedDeliveryDate}),
  dispatch: (id: string): Promise<DeliveryResponse> => apiClient.post('/api/deliveries/' + enc(id) + '/dispatch'),
  deliver: (id: string): Promise<DeliveryResponse> => apiClient.post('/api/deliveries/' + enc(id) + '/deliver'),
  fail: (id: string, reason: string): Promise<DeliveryResponse> =>
    apiClient.post('/api/deliveries/' + enc(id) + '/fail',{reason}),
  cancel: (id: string, input: CancelSaleInput): Promise<DeliveryResponse> => apiClient.post('/api/deliveries/' + enc(id) + '/cancel', input),
  cancelWithReason: (id:string,input:CancelSaleInput):Promise<DeliveryResponse> =>
    apiClient.post('/api/deliveries/'+enc(id)+'/cancel-with-reason',input),
  returnToWarehouse: (id: string, warehouseId: string): Promise<DeliveryResponse> =>
    apiClient.post('/api/deliveries/' + enc(id) + '/return-to-warehouse',{warehouseId}),
};
