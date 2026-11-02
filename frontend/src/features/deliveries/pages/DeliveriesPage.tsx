import { CreateDeliveryPage } from './CreateDeliveryPage';
import { useUrlListState } from '../../../shared/hooks/useUrlListState';
import { useCallback } from 'react';
import { useSearchParams } from 'react-router';
import { QuickViewDrawer } from '../../../shared/components/QuickViewDrawer';
import { DeliveryQuickView } from '../../../shared/components/QuickViewContent';

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
const initial:Filters={status:'',from:'',to:'',plannedFrom:'',plannedTo:'',cityRegion:'',page:0,operational:true,pendingOnly:false,deliveredOn:''};
export function DeliveriesPage(){
  const {hasPermission}=useAuth();
  const [quickParams, setQuickParams] = useSearchParams();
  const selected = quickParams.get('quick');
  const goQuick = (id: string) => setQuickParams(prev => { const next = new URLSearchParams(prev); next.set('quick', id); return next; });
  const closeQuick = useCallback(() => setQuickParams(prev => {
    const next = new URLSearchParams(prev); next.delete('quick'); return next;
  }), [setQuickParams]);
  const interceptDetail = (event: React.MouseEvent<HTMLDivElement>) => {
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    const target = event.target;
    if (!(target instanceof Element)) return;
    const link = target.closest('a[href]');
    if (!link) return;
    const path = new URL(link.getAttribute('href') || '', window.location.origin).pathname;
    if (!path.startsWith('/deliveries/')) return;
    const id = path.slice('/deliveries/'.length);
    if (!id || id.includes('/')) return;
    if (id === 'new' && !manage) return;
    event.preventDefault();
    setQuickParams(prev => { const next = new URLSearchParams(prev); next.set('quick', decodeURIComponent(id)); return next; });
  };

  const read=hasPermission('DELIVERIES_READ'),manage=hasPermission('DELIVERIES_MANAGE');
  const [filters,setFilters]=useUrlListState(initial, value => ({ ...value,
    status: ['READY','IN_TRANSIT','DELIVERED','FAILED','CANCELLED'].includes(value.status) ? value.status : '',
  }));
  const history=!filters.operational;
  const region=filters.cityRegion;
  const today=applyQuick('today'), upcoming=applyQuick('upcoming');
  const quick: Quick|null = filters.pendingOnly || filters.deliveredOn ? null
    : !filters.status && filters.plannedFrom===today.plannedFrom && filters.plannedTo===today.plannedTo ? 'today'
    : !filters.status && filters.plannedFrom===upcoming.plannedFrom && !filters.plannedTo ? 'upcoming'
    : filters.plannedFrom || filters.plannedTo ? null : filters.status || 'all';
  const change=(p:Partial<Filters>)=>setFilters(f=>({...f,...p,page:0}));
  const setRegion=(cityRegion:string)=>change({cityRegion});
  const choose=(choice:Quick)=>change({...applyQuick(choice),pendingOnly:false,deliveredOn:''});
  const valid=!(filters.from&&filters.to&&filters.from>filters.to) &&
    !(filters.plannedFrom&&filters.plannedTo&&filters.plannedFrom>filters.plannedTo);
  const q=useQuery({queryKey:deliveryKeys.list(filters),queryFn:()=>deliveryApi.list(filters),
    enabled:read&&valid});
  if(!read||(q.error instanceof ApiClientError&&q.error.status===403))return <ForbiddenPage/>;
  const visibleIds = (q.data?.items ?? []).map(record => record.id);
  const selectedIndex = visibleIds.indexOf(selected ?? '');
  return <div className="flex min-w-0 items-start gap-4"><div onClickCapture={interceptDetail} className="space-y-6 min-w-0 flex-1">
    <PageHeader title="Доставки" description="Городские и региональные заказы в работе."
      action={manage?<Button asChild><Link to="/deliveries/new">+ Оформить доставку</Link></Button>:undefined}/>
    <div className="flex flex-wrap gap-2">
      <Button variant={!history?'default':'outline'} aria-pressed={!history}
        onClick={()=>{setFilters({...initial});}}>Актуальные доставки</Button>
      <Button variant={history?'default':'outline'} aria-pressed={history}
        onClick={()=>{setFilters({...initial,operational:false});}}>История доставок</Button>
    </div>
    {(filters.pendingOnly || filters.deliveredOn) && <p role="status">
      {filters.deliveredOn ? 'Завершены: '+filters.deliveredOn : 'Готовы к отправке или в пути'}
      <Button variant="ghost" onClick={()=>setFilters(initial)}>Сбросить фильтры</Button></p>}
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
  </div>{selected && <QuickViewDrawer title={selected === 'new' ? 'Новая доставка' : 'Доставка'} detailUrl={'/deliveries/' + encodeURIComponent(selected)} onClose={closeQuick} previous={selectedIndex > 0 ? () => goQuick(visibleIds[selectedIndex - 1]!) : undefined} next={selectedIndex >= 0 && selectedIndex < visibleIds.length - 1 ? () => goQuick(visibleIds[selectedIndex + 1]!) : undefined}>{selected === 'new' ? <CreateDeliveryPage embedded onCreated={goQuick}/> : <DeliveryQuickView key={selected} id={selected}/>}</QuickViewDrawer>}</div>;
}
