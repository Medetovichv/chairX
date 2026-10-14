import {useQuery,useQueryClient} from '@tanstack/react-query';
import {useAuth} from '../../auth/AuthProvider';
import {deliveryApi,deliveryKeys} from '../../deliveries/api';
import {DeliveryActions} from '../../deliveries/components/DeliveryActions';
import {salesKeys} from '../api';
import {inventoryKeys} from '../../inventory/api';
import {ApiClientError} from '../../../shared/api/api-error';

/**
 * Uses the same guarded/uncertain-outcome delivery workflow as delivery detail.
 * Never post an irreversible status change directly from a table cell.
 */
export function SaleDeliveryQuickActions({saleId}:{saleId:string}){
  const {hasPermission}=useAuth();
  const client=useQueryClient();
  const canManage=hasPermission('DELIVERIES_MANAGE')&&hasPermission('DELIVERIES_READ');
  const delivery=useQuery({
    queryKey:deliveryKeys.bySale(saleId),queryFn:()=>deliveryApi.bySale(saleId),
    enabled:canManage,retry:false,
  });
  if(!canManage)return null;
  if(delivery.isPending)return <span className="text-xs text-slate-500">Загружаем…</span>;
  if(delivery.isError){
    if(delivery.error instanceof ApiClientError&&delivery.error.status===404)return null;
    return <button type="button" className="text-xs text-blue-700 underline"
      onClick={()=>void delivery.refetch()}>Повторить загрузку</button>;
  }
  if(!delivery.data||!['READY','IN_TRANSIT','FAILED'].includes(delivery.data.status))return null;
  return <details className="entity-row-action mt-2 text-sm">
    <summary className="cursor-pointer font-medium text-blue-700">Изменить доставку</summary>
    <div className="mt-2 w-72 max-w-[85vw]">
      <DeliveryActions delivery={delivery.data}
        onChanged={async result=>{
          client.setQueryData(deliveryKeys.bySale(saleId),result);
          client.setQueryData(deliveryKeys.detail(result.id),result);
          await Promise.all([
            client.invalidateQueries({queryKey:deliveryKeys.lists}),
            client.invalidateQueries({queryKey:salesKeys.lists}),
            client.invalidateQueries({queryKey:salesKeys.kanban}),
            client.invalidateQueries({queryKey:inventoryKeys.overviewPrefix}),
            client.invalidateQueries({queryKey:inventoryKeys.balancePrefix}),
          ]);
        }}/>
    </div>
  </details>;
}
