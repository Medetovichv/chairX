import type { SaleSummary } from '../sales/types';
/** null = permission denied for this metric; 0 = authorized, no records. */
export interface DashboardOverview {
  businessDate: string;
  orders30Days: number | null;
  activeOrders: number | null;
  pendingDeliveries: number | null;
  deliveredToday: number | null;
  delayedDeliveries: number | null;
  availableUnits: number | null;
  lowStockVariants: number | null;
  ordersByDay: Array<{ day: string; orders: number }>;
  recentSales: SaleSummary[];
}
