// F05 contracts mirror backend kg.chairx.customer.api and kg.chairx.sale.api.
export interface Customer {
  id: string;
  fullName: string | null;
  phone: string | null;
  secondaryPhone: string | null;
  whatsappPhone: string | null;
  instagramUsername: string | null;
  address: string | null;
  cityRegion: string | null;
  comment: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export type CustomerInput = Pick<Customer,
  'fullName' | 'phone' | 'secondaryPhone' | 'whatsappPhone' |
  'instagramUsername' | 'address' | 'cityRegion' | 'comment'>;

export interface CustomerPage {
  items: Customer[];
  page: number;
  size: number;
  totalElements: number;
}

export interface CustomerOverviewLine {
  id: string;
  fullName: string | null;
  phone: string | null;
  whatsappPhone: string | null;
  cityRegion: string | null;
  orderCount: number;
  lastSaleNumber: string | null;
  lastOrderAt: string | null;
}

export interface CustomerOverviewPage {
  items: CustomerOverviewLine[];
  page: number;
  size: number;
  total: number;
}

export type CustomerListing =
  | { kind: 'overview'; data: CustomerOverviewPage }
  | { kind: 'basic'; data: CustomerPage }
  | { kind: 'limited-search'; items: Customer[] };

export interface SaleSummary {
  id: string;
  saleNumber: string;
  createdAt: string;
  customerId: string;
  customerName: string | null;
  total: string | number;
  status: string;
  fulfillmentType: string | null;
  quantity: number;
  products: string | null;
  paymentStatus: string | null;
  deliveryStatus: string | null;
  plannedDeliveryDate: string | null;
}

export interface SalePage {
  items: SaleSummary[];
  page: number;
  size: number;
  total: number;
}
