import { StatusBadge } from '../../../shared/components/StatusBadge';
import type { DeliveryStatus } from '../types';
import { deliveryStatusNames } from '../validation';
export function DeliveryStatusBadge({status}:{status:DeliveryStatus}){
  const tone=status==='DELIVERED'?'success':status==='FAILED'||status==='CANCELLED'?'danger':
    status==='IN_TRANSIT'?'warning':'neutral';
  return <StatusBadge tone={tone}>{deliveryStatusNames[status]}</StatusBadge>;
}
