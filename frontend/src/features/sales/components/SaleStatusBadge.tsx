import { StatusBadge } from '../../../shared/components/StatusBadge';
import type { SaleStatus } from '../types';
import { saleStatusNames } from '../validation';
export function SaleStatusBadge({status}:{status:SaleStatus}) {
  const tone=status==='FULFILLED'?'success':status==='CANCELLED'?'danger':
    status==='CONFIRMED'?'warning':'neutral';
  return <StatusBadge tone={tone}>{saleStatusNames[status]}</StatusBadge>;
}
