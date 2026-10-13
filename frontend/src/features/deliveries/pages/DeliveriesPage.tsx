import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { PageHeader } from '../../../shared/components/PageHeader';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState';
import { EmptyState } from '../../../shared/components/EmptyState';
import { Button } from '../../../shared/ui/button';
import { PurchasePager } from '../../purchases/components/PurchasePager';
import { ForbiddenPage } from '../../../pages/ForbiddenPage';
import { ApiClientError } from '../../../shared/api/api-error';
import { deliveryApi, deliveryKeys } from '../api';
import { DeliveriesTable } from '../components/DeliveriesTable';
import { DeliveryFilters, applyQuick, type Quick } from '../components/DeliveryFilters';
import type { DeliveryFilters as Filters } from '../types';
const initial:Filters={status:'',from:'',to:'',plannedFrom:'',plannedTo:'',cityRegion:'',page:0,operational:true};
export function DeliveriesPage(){
  const {hasPermission}=useAuth();
  const read=hasPermission('DELIVERIES_READ'),manage=hasPermission('DELIVERIES_MANAGE');
  const [filters,setFilters]=useState<Filters>(initial);
  const [region,setRegion]=useState('');
  const [quick,setQuick]=useState<Quick|null>('all');
  const [history,setHistory]=useState(false);
  useEffect(()=>{
    const timeout=setTimeout(()=>setFilters(f=>({...f,cityRegion:region.trim(),page:0})),350);
    return ()=>clearTimeout(timeout);
  },[region]);
  const change=(p:Partial<Filters>)=>{
    setQuick(null);setFilters(f=>({...f,...p,operational:false,page:0}));
  };
  const choose=(choice:Quick)=>{
    setQuick(choice);setFilters(f=>({...f,...applyQuick(choice),operational:!history,page:0}));
  };
  const valid=!(filters.from&&filters.to&&filters.from>filters.to) &&
    !(filters.plannedFrom&&filters.plannedTo&&filters.plannedFrom>filters.plannedTo);
  const q=useQuery({queryKey:deliveryKeys.list(filters),queryFn:()=>deliveryApi.list(filters),
    enabled:read&&valid});
  if(!read||(q.error instanceof ApiClientError&&q.error.status===403))return <ForbiddenPage/>;
  return <div className="space-y-6">
    <PageHeader title="Доставки" description="Городские и региональные заказы в работе."
      action={manage?<Button asChild><Link to="/deliveries/new">+ Оформить доставку</Link></Button>:undefined}/>
    <div className="flex flex-wrap gap-2">
      <Button variant={!history?'default':'outline'} aria-pressed={!history}
        onClick={()=>{setHistory(false);setQuick('all');setFilters({...initial});}}>Актуальные доставки</Button>
      <Button variant={history?'default':'outline'} aria-pressed={history}
        onClick={()=>{setHistory(true);setQuick('all');setFilters({...initial,operational:false});}}>История доставок</Button>
    </div>
    <DeliveryFilters value={filters} region={region} onRegion={setRegion}
      quick={quick} onQuick={choose} onChange={change}/>
    {!valid?<p role="alert" className="rounded-xl bg-amber-50 p-4 text-sm text-amber-900">
      Начальная дата не должна быть позже конечной.</p>:
      q.isPending?<LoadingState label="Загружаем доставки…"/>:
      q.isError?<ErrorState error={q.error} onRetry={()=>void q.refetch()}/>:
      !q.data?.items.length?<EmptyState title="Доставки не найдены"
        description="Попробуйте изменить фильтры или оформить доставку для подтверждённой продажи."/>:
      <div><DeliveriesTable rows={q.data.items}/>
        <PurchasePager page={q.data.page} size={q.data.size} total={q.data.total}
          label="доставок" onPage={page=>setFilters(f=>({...f,page}))}/>
      </div>}
  </div>;
}
