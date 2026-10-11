import { Link } from 'react-router';
import { ArrowLeft, ArrowRightLeft, Warehouse } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { PageHeader } from '../../../shared/components/PageHeader';
import { ErrorState } from '../../../shared/components/ErrorState';
import { LoadingState } from '../../../shared/components/LoadingState';
import { Button } from '../../../shared/ui/button';
import { inventoryApi, inventoryKeys } from '../api';
import { TransferHistory } from '../components/TransferHistory';

export function InventoryTransfersPage() {
  const warehouses = useQuery({queryKey: inventoryKeys.warehouses, queryFn: inventoryApi.warehouses});
  return <div className="space-y-5">
    <PageHeader title="История перемещений" description="Все подтверждённые перемещения между складами." />
    <div className="flex flex-wrap gap-2">
      <Button asChild variant="outline"><Link to="/inventory"><ArrowLeft className="size-4" />Остатки</Link></Button>
      <span className="inline-flex items-center gap-2 text-xs text-slate-500"><ArrowRightLeft className="size-4" />История доступна для просмотра, изменения записей запрещены.</span>
    </div>
    {warehouses.isPending ? <LoadingState label="Загружаем склады…" /> :
      warehouses.isError ? <ErrorState error={warehouses.error} onRetry={()=>void warehouses.refetch()}/> :
      <TransferHistory warehouses={warehouses.data ?? []} selectedWarehouseId={null} selectedLine={null}/>}
    <p className="flex items-center gap-2 text-xs text-slate-500"><Warehouse className="size-4"/>Для нового перемещения откройте позицию в «Остатках».</p>
  </div>;
}
