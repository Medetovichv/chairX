import { apiClient } from '../../shared/api/client';
import type { CreateDraftInput, DraftInput, SaleFilters, SalePageResponse, SaleResponse, SaleStatus, SaleHistoryPage, SaleCommentsPage, SaleComment, CreateSaleCommentInput } from './types';

export const SALES_PAGE_SIZE = 20;
const enc = (id: string) => encodeURIComponent(id);
export const salesKeys = {
  lists: ['sales', 'list'] as const,
  list: (f: SaleFilters) =>
    ['sales', 'list', f.status, f.from, f.to, f.number, f.page, SALES_PAGE_SIZE] as const,
  kanban: ['sales', 'kanban'] as const,
  kanbanColumn: (status: SaleStatus) => ['sales', 'kanban', status, SALES_PAGE_SIZE] as const,
  detail: (id: string) => ['sales', 'detail', id] as const,
  historyPrefix: (id: string) => ['sales', 'history', id] as const,
  history: (id: string, page: number) => ['sales', 'history', id, page] as const,
  commentsPrefix: (id: string) => ['sales', 'comments', id] as const,
  comments: (id: string, page: number) => ['sales', 'comments', id, page] as const,
  customerSearch: (q: string) => ['customers', 'search', q] as const,
};
export const salesApi = {
  list: (f: SaleFilters): Promise<SalePageResponse> => {
    const search = new URLSearchParams({ page: String(f.page), size: String(SALES_PAGE_SIZE) });
    if (f.status) search.set('status', f.status);
    if (f.from) search.set('from', f.from);
    if (f.to) search.set('to', f.to);
    if (f.number) search.set('number', f.number.trim());
    return apiClient.get<SalePageResponse>('/api/sales?' + search.toString());
  },
  get: (id: string) => apiClient.get<SaleResponse>('/api/sales/' + enc(id)),
  history: (id: string, page = 0): Promise<SaleHistoryPage> =>
    apiClient.get<SaleHistoryPage>('/api/sales/' + enc(id) +
      '/history?page=' + encodeURIComponent(String(page)) + '&size=20'),
  comments: (id: string, page = 0): Promise<SaleCommentsPage> =>
    apiClient.get<SaleCommentsPage>('/api/sales/' + enc(id) +
      '/comments?page=' + encodeURIComponent(String(page)) + '&size=20'),
  addComment: (id: string, input: CreateSaleCommentInput) =>
    apiClient.post<SaleComment>('/api/sales/' + enc(id) + '/comments', input),
  createDraft: (input: CreateDraftInput) => apiClient.post<SaleResponse>('/api/sales/drafts', input),
  createWithDelivery: (input: CreateDraftInput) => {
    if (!input.deliveryDetails) throw new Error('Укажите сведения о доставке');
    const {deliveryDetails,...sale}=input;
    return apiClient.post<SaleResponse>('/api/sales/with-delivery',{sale,delivery:deliveryDetails});
  },
  updateDraft: (id: string, input: DraftInput) =>
    apiClient.put<SaleResponse>('/api/sales/' + enc(id) + '/draft', input),
  confirm: (id: string) => apiClient.post<SaleResponse>('/api/sales/' + enc(id) + '/confirm'),
  fulfill: (id: string) => apiClient.post<SaleResponse>('/api/sales/' + enc(id) + '/fulfill'),
  cancel: (id: string) => apiClient.post<SaleResponse>('/api/sales/' + enc(id) + '/cancel'),
};
