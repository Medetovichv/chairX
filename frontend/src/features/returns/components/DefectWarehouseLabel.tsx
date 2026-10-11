import { Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { inventoryApi,inventoryKeys } from '../../inventory/api';
import { useAuth } from '../../auth/AuthProvider';

/** Shared human-readable warehouse label for defects; never exposes an id. */
export function DefectWarehouseLabel({warehouseId}:{warehouseId:string}) {
  const {hasPermission}=useAuth();
  const allowed=hasPermission('INVENTORY_READ');
  const warehouses=useQuery({queryKey:inventoryKeys.warehouses,
    queryFn:inventoryApi.warehouses,enabled:allowed});
  if(!allowed)return <span>Данные склада недоступны</span>;
  if(warehouses.isPending)return <span>Загружаем склад…</span>;
  const current=warehouses.data?.find(w=>w.id===warehouseId);
  if(!current)return <span>Название склада недоступно</span>;
  return <Link className="font-semibold text-blue-700 underline-offset-2 hover:underline"
    to={'/inventory?warehouseId='+encodeURIComponent(current.id)}>{current.name}</Link>;
}
