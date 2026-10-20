import {Link,useParams} from 'react-router';
import {useQuery} from '@tanstack/react-query';
import {useAuth} from '../../auth/AuthProvider';
import {apiClient} from '../../../shared/api/client';
import {PageHeader} from '../../../shared/components/PageHeader';
import {LoadingState} from '../../../shared/components/LoadingState';
import {ErrorState} from '../../../shared/components/ErrorState';
import {ForbiddenPage} from '../../../pages/ForbiddenPage';
type SourceDocument={kind:string;id:string;title:string;fields:Record<string,string>};
const supported=new Set(['EXPENSE','TRANSFER','ADMIN_ADJUSTMENT','OPENING_BALANCE']);
export function FinanceSourceDocumentPage(){
 const {kind='',documentId=''}=useParams();
 const {hasPermission}=useAuth();
 const allowed=hasPermission('FINANCE_READ')&&(kind!=='EXPENSE'||hasPermission('EXPENSES_READ'));
 const valid=supported.has(kind)&&/^[a-f0-9]{8}-[a-f0-9-]{27,}$/i.test(documentId);
 const q=useQuery({queryKey:['f15','source-document',kind,documentId],
  queryFn:()=>apiClient.get<SourceDocument>('/api/finance/source-documents/'+encodeURIComponent(kind)+'/'+encodeURIComponent(documentId)),
  enabled:allowed&&valid});
 if(!allowed)return <ForbiddenPage/>;
 if(!valid)return <p role="alert">Неизвестный первичный документ.</p>;
 return <div className="min-w-0 space-y-5 pb-10">
  <PageHeader title="Первичный документ" description="Данные исходного документа, подтверждённые сервером ChairX"/>
  <Link to="/finance/operations" className="text-sm text-blue-700 underline">← К финансовым операциям</Link>
  {q.isPending?<LoadingState label="Загружаем документ…"/>:q.isError?<ErrorState error={q.error} onRetry={()=>void q.refetch()}/>:
   <section aria-label="Детали первичного документа" className="max-w-2xl rounded-2xl border bg-white p-5">
    <h2 className="text-lg font-bold">{q.data.title}</h2>
    <dl className="mt-4 divide-y">{Object.entries(q.data.fields).map(([key,value])=>
     <div key={key} className="grid min-w-0 gap-1 py-3 text-sm sm:grid-cols-2">
      <dt className="text-slate-500">{key}</dt><dd className="break-words font-semibold">{value}</dd>
     </div>)}</dl>
   </section>}
 </div>;
}
