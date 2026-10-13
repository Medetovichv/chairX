import type { FulfillmentType } from '../sales/types';
export type DeliveryStatus = 'READY' | 'IN_TRANSIT' | 'DELIVERED' | 'FAILED' | 'CANCELLED';
export type Money = string | number;
export interface DeliverySummary {
  id: string; saleId: string; status: DeliveryStatus;
  recipientName: string | null; cityRegion: string | null; createdAt: string;
  plannedDeliveryDate: string | null; saleNumber: string | null;
  recipientPhone: string | null; address: string | null;
  fulfillmentType: FulfillmentType | null; deliveredAt: string | null;
}
export interface DeliveryResponse {
  id: string; saleId: string; status: DeliveryStatus;
  recipientName: string | null; recipientPhone: string; address: string;
  cityRegion: string | null; deliveryCost: Money;
  carrierName: string | null; trackingNumber: string | null; comment: string | null;
  createdBy: string; createdAt: string; dispatchedBy: string | null;
  dispatchedAt: string | null; deliveredBy: string | null;
  deliveredAt: string | null; failedBy: string | null; failedAt: string | null;
  failureReason: string | null; cancelledBy: string | null;
  cancelledAt: string | null; returnedToWarehouseBy: string | null;
  returnedToWarehouseAt: string | null; returnWarehouseId: string | null;
  plannedDeliveryDate: string | null;
}
export interface DeliveryPage { items: DeliverySummary[]; page: number; size: number; total: number }
export interface DeliveryFilters {
  status: DeliveryStatus | ''; from: string; to: string;
  plannedFrom: string; plannedTo: string; cityRegion: string; page: number;
  operational?: boolean;
}
export interface CreateDeliveryInput {
  saleId: string; recipientName: string | null; recipientPhone: string;
  address: string; cityRegion: string | null; deliveryCost: string;
  carrierName: string | null; trackingNumber: string | null;
  comment: string | null; plannedDeliveryDate: string | null;
}
