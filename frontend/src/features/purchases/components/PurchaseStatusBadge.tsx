import { StatusBadge } from '../../../shared/components/StatusBadge';
import type { PurchaseStatus } from '../types';
import { statusNames } from '../validation';
export function PurchaseStatusBadge({ status }: { status: PurchaseStatus }) {
  const tone = status==='RECEIVED'?'success':status==='CANCELLED'?'danger':
    status==='PARTIALLY_RECEIVED'?'warning':'neutral';
  return <StatusBadge tone={tone}>{statusNames[status] ?? status}</StatusBadge>;
}
