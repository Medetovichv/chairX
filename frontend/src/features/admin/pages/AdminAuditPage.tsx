import {useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {PageHeader} from '../../../shared/components/PageHeader';
import {LoadingState} from '../../../shared/components/LoadingState';
import {ErrorState} from '../../../shared/components/ErrorState';
import {Input} from '../../../shared/ui/input';
import {Button} from '../../../shared/ui/button';
import {adminApi,adminKeys} from '../api';
export function AdminAuditPage(){
 const [filter,setFilter]=useState(''),[target,setTarget]=useState(''),[page,setPage]=useState(0);
 const log=useQuery({queryKey:adminKeys.audit(target,page),queryFn:()=>adminApi.audit(target,page)});
 const total=log.data?.total??0, size=log.data?.size??20,totalPages=Math.ceil(total/size);
 return <div className="space-y-5">
  <PageHeader title="Журнал безопасности" description="Все события с сервера. Записи не редактируются."/>
  <section className="space-y-3 rounded-2xl border border-slate-200 bg-white p-5">
   <form className="flex flex-wrap gap-3" onSubmit={e=>{
    e.preventDefault();
    if(filter&&!/^[0-9a-f]{8}-[0-9a-f-]{27}$/i.test(filter)){return;}
    setTarget(filter.trim());setPage(0);
   }}>
    <Input aria-label="UUID объекта" className="max-w-sm" placeholder="Фильтр по targetId (UUID)" value={filter} onChange={e=>setFilter(e.target.value)}/>
    <Button type="submit">Применить фильтр</Button>
    <Button variant="outline" type="button" onClick={()=>{setFilter('');setTarget('');setPage(0);}}>Очистить</Button>
   </form>
   {log.isPending?<LoadingState label="Загружаем журнал…"/>:
    log.isError?<ErrorState error={log.error} onRetry={()=>void log.refetch()}/>:
    (log.data?.items.length??0)===0?<p>Событий не найдено</p>:
    <div className="overflow-x-auto"><table className="w-full min-w-160 text-left text-sm">
     <thead><tr>{['Дата','Инициатор','Событие','Тип','Объект','Детали'].map(x=><th key={x} className="p-3">{x}</th>)}</tr></thead>
     <tbody className="divide-y divide-slate-100">{log.data?.items.map(item=><tr key={item.id}>
      <td className="whitespace-nowrap p-3">{new Date(item.createdAt).toLocaleString('ru-RU')}</td>
      <td className="p-3">{item.actorUserId}</td><td className="p-3">{item.action}</td>
      <td className="p-3">{item.targetType}</td><td className="p-3">{item.targetId}</td>
      <td className="max-w-md break-all p-3">{item.details??'—'}</td>
     </tr>)}</tbody>
    </table></div>}
   <nav aria-label="Страницы журнала" className="flex flex-wrap items-center justify-between gap-3">
    <span className="text-sm">Записей: {total} · Страница {totalPages===0?0:page+1} из {totalPages}</span>
    <div className="flex gap-2">
     <Button variant="outline" disabled={page===0} onClick={()=>setPage(page-1)}>Назад</Button>
     <Button variant="outline" disabled={page+1>=totalPages} onClick={()=>setPage(page+1)}>Вперёд</Button>
    </div>
   </nav>
  </section>
 </div>;
}
