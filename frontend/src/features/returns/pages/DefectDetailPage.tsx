import { Link,useParams } from 'react-router';
import { useQuery,useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { ReturnChairLabel } from '../components/ReturnChairLabel';
import { DefectWarehouseLabel } from '../components/DefectWarehouseLabel';
import { supplierApi } from '../../purchases/api';
import { PageHeader } from '../../../shared/components/PageHeader';
import { ErrorState } from '../../../shared/components/ErrorState';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ForbiddenPage } from '../../../pages/ForbiddenPage';
import { NotFoundPage } from '../../../pages/NotFoundPage';
import { ApiClientError } from '../../../shared/api/api-error';
import { defectsApi,returnKeys } from '../api';
import { DefectActions } from '../components/DefectActions';
import { defectLabels,dateLabel,uuid } from '../validation';
export function DefectDetailPage(){
 const {defectId=''}=useParams(),{hasPermission}=useAuth(),client=useQueryClient();
 const read=hasPermission('DEFECTS_READ');
 const q=useQuery({queryKey:returnKeys.defect(defectId),queryFn:()=>defectsApi.get(defectId),
  enabled:read&&uuid(defectId)});
 const supplier=useQuery({queryKey:['suppliers','detail',q.data?.supplierId??''],
  queryFn:()=>supplierApi.get(q.data!.supplierId!),
  enabled:hasPermission('SUPPLIERS_READ')&&!!q.data?.supplierId});
 if(!read)return <ForbiddenPage/>;
 if(!uuid(defectId))return <NotFoundPage/>;
 if(q.isPending)return <LoadingState label="Загружаем дефект…"/>;
 if(q.isError){
  if(q.error instanceof ApiClientError&&q.error.status===403)return <ForbiddenPage/>;
  if(q.error instanceof ApiClientError&&q.error.status===404)return <NotFoundPage/>;
  return <ErrorState error={q.error} onRetry={()=>void q.refetch()}/>;
 }
 const d=q.data;
 return <div className="space-y-5">
  <Link className="font-semibold text-indigo-700 underline" to="/returns/defects">← Брак</Link>
  <PageHeader title={'Дефект: '+defectLabels[d.status]}/>
  <section aria-label="Карточка дефекта" className="grid gap-3 rounded-xl border bg-white p-4 text-sm sm:grid-cols-2">
   <div><strong>Кресло:</strong> <ReturnChairLabel variantId={d.productVariantId}/></div>
   <p><strong>Склад:</strong> <DefectWarehouseLabel warehouseId={d.warehouseId}/></p>
   <p><strong>Количество:</strong> {d.quantity}</p>
   <p><strong>Статус:</strong> {defectLabels[d.status]}</p>
   <p className="sm:col-span-2"><strong>Описание:</strong> {d.description}</p>
   <p><strong>Создан:</strong> {dateLabel(d.createdAt)} · {d.createdBy}</p>
   <p><strong>Закрыт:</strong> {dateLabel(d.resolvedAt)} · {d.resolvedBy??'—'}</p>
   {d.resolutionNote&&<p className="sm:col-span-2"><strong>Результат:</strong> {d.resolutionNote}</p>}
   <p><strong>Поставщик:</strong> {!d.supplierId?'Не указан':
     hasPermission('SUPPLIERS_READ')?(supplier.data?.name??'Название недоступно'):
     'Недоступен для вашей роли'}</p>
   <p><strong>Позиция поступления:</strong> {d.purchaseReceiptItemId?'Связана с поступлением':'Не указана'}</p>
  </section>
  <DefectActions key={d.id} defect={d} onChanged={next=>client.setQueryData(returnKeys.defect(d.id),next)}/>
 </div>;
}
