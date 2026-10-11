import type { Money } from '../sales/types';

export interface ClosingAccount {
  expected: Money;
  actual: Money;
  difference: Money;
  note: string | null;
  originalExpected: Money;
  recalculated: boolean;
  requiresReview: boolean;
}
export interface ClosingReport {
  id: string;
  businessDate: string;
  createdAt: string;
  createdBy: string;
  cash: ClosingAccount;
  bank: ClosingAccount;
  version: number;
}
export interface ClosingPreview {
  businessDate: string;
  expectedCash: Money;
  expectedBank: Money;
}
export interface ClosingAccess {
  businessDate: string;
  reportExists: boolean;
  reportStatus: 'NOT_CREATED' | 'CLOSED';
  accessStatus: 'OPEN' | 'LOCKED' | 'TEMPORARILY_UNLOCKED';
  canEdit: boolean;
  canUnlock: boolean;
  unlockedUntil: string | null;
  remainingSeconds: number;
}
export interface ClosingInput {
  actualCash: string;
  cashNote: string | null;
  actualBank: string;
  bankNote: string | null;
}
export interface CompletedSale {
  saleId: string;
  saleNumber: string;
  customerName: string | null;
  phone: string | null;
  products: string | null;
  quantity: number;
  address: string | null;
  fulfillmentType: string;
  completedAt: string;
  total: Money;
  paymentStatus: string;
}
export interface CompletedSales {
  businessDate: string;
  closed: boolean;
  snapshotAvailable: boolean;
  preliminary: boolean;
  items: CompletedSale[];
  totals: {orders: number | null; chairs: number | null; value: Money | null};
  lateCompletionCount: number | null;
}

export interface ClosingUpdate extends ClosingInput {
  expectedVersion: number;
  reason: string;
}
export interface UnlockGrant {
  id: string;
  businessDate: string;
  unlockedBy: string;
  unlockedAt: string;
  expiresAt: string;
  reason: string;
  revokedAt: string | null;
  revokedBy: string | null;
}
export interface ClosingAudit {
  action: string;
  actor: string;
  at: string;
  before: string | null;
  after: string | null;
}
