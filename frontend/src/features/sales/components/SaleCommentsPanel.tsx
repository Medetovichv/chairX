import { useRef, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { salesApi, salesKeys } from '../api';
import { ApiClientError, userFacingApiError } from '../../../shared/api/api-error';
import { Button } from '../../../shared/ui/button';
import { LoadingState } from '../../../shared/components/LoadingState';
import { ErrorState } from '../../../shared/components/ErrorState';
import type { CreateSaleCommentInput } from '../types';

function stamp(value:string):string {
  const date=new Date(value);
  return Number.isNaN(date.getTime())?'Дата недоступна':
    new Intl.DateTimeFormat('ru-RU',{timeZone:'Asia/Bishkek',
      day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit'}).format(date);
}
function restore(key:string):CreateSaleCommentInput|null {
  try {
    const stored=sessionStorage.getItem(key);
    if(!stored)return null;
    const data=JSON.parse(stored) as Partial<CreateSaleCommentInput>;
    if(typeof data.idempotencyKey!=='string'||typeof data.text!=='string')
      return {idempotencyKey:'',text:''};
    return {idempotencyKey:data.idempotencyKey,text:data.text};
  } catch {return {idempotencyKey:'',text:''};}
}
export function SaleCommentsPanel({saleId}:{saleId:string}){
  const {hasPermission,user}=useAuth();
  const allowed=hasPermission('SALES_COMMENT_CREATE');
  const client=useQueryClient();
  const [page,setPage]=useState(0);
  const [draft,setDraft]=useState('');
  const storageKey='chairx:sale-comment:'+String(user?.id??'unknown')+':'+saleId;
  const [pending,setPending]=useState<CreateSaleCommentInput|null>(()=>restore(storageKey));
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState('');
  const running=useRef(false);
  const comments=useQuery({
    queryKey:salesKeys.comments(saleId,page),
    queryFn:()=>salesApi.comments(saleId,page),
    enabled:hasPermission('SALES_READ'),
  });
  async function send(operation:CreateSaleCommentInput){
    if(running.current)return;
    running.current=true;
    setBusy(true);setError('');
    try {
      // Persist the operation identity before a non-read request.
      sessionStorage.setItem(storageKey,JSON.stringify(operation));
      setPending(operation);
      await salesApi.addComment(saleId,operation);
      sessionStorage.removeItem(storageKey);
      setPending(null);
      setDraft('');
      setPage(0);
      await client.invalidateQueries({queryKey:salesKeys.commentsPrefix(saleId)});
    } catch(cause) {
      setError(userFacingApiError(cause));
      if(cause instanceof ApiClientError && cause.kind==='http' &&
          cause.status!==undefined && cause.status<500 && cause.status!==409) {
        try{sessionStorage.removeItem(storageKey);}catch { /* blocked storage remains locked */ }
        setPending(null);
      } else {
        setPending(operation);
        setError('Результат отправки неизвестен. Повторите только ту же операцию — её ключ сохранён. '+userFacingApiError(cause));
      }
    } finally {setBusy(false);running.current=false;}
  }
  function add(){
    const text=draft.trim();
    if(busy||pending||!allowed||!text||text.length>2000)return;
    void send({idempotencyKey:crypto.randomUUID(),text});
  }
  return <section aria-label="Комментарии сотрудников"
    className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5">
    <h3 className="text-lg font-bold text-slate-900">Комментарии сотрудников</h3>
    {comments.isPending?<LoadingState label="Загружаем комментарии…"/>:
      comments.isError?<ErrorState title="Комментарии недоступны"
        error={comments.error} onRetry={()=>void comments.refetch()}/>:
      <>
        {comments.data?.items.length===0&&<p className="text-sm text-slate-500">
          Комментариев пока нет.</p>}
        <ol className="space-y-3">
          {comments.data?.items.map(comment=><li key={comment.id}
            className="rounded-xl border border-slate-100 bg-slate-50 p-3 text-sm">
            <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
              <span className="font-semibold text-slate-900">{comment.author}</span>
              <time dateTime={comment.createdAt} className="text-xs text-slate-500">
                {stamp(comment.createdAt)}</time>
            </div>
            <p className="whitespace-pre-wrap break-words text-slate-700">{comment.text}</p>
          </li>)}
        </ol>
        {!!comments.data&&comments.data.total>comments.data.size&&
          <div className="flex justify-between gap-3">
            <Button variant="outline" type="button" disabled={page===0}
              onClick={()=>setPage(p=>Math.max(0,p-1))}>Новые комментарии</Button>
            <span className="self-center text-xs text-slate-500">Страница {page+1}</span>
            <Button variant="outline" type="button"
              disabled={(page+1)*comments.data.size>=comments.data.total}
              onClick={()=>setPage(p=>p+1)}>Старые комментарии</Button>
          </div>}
      </>}
    {allowed&&<div className="space-y-3 border-t border-slate-100 pt-4">
      <label className="block space-y-2 text-sm font-semibold">Написать комментарий
        <textarea aria-label="Новый комментарий" rows={3} maxLength={2000}
          value={pending?.text??draft} disabled={busy||!!pending}
          onChange={e=>setDraft(e.target.value)}
          className="w-full rounded-xl border border-slate-200 p-3 text-sm"
          placeholder="Например, клиент просил позвонить перед доставкой"/>
      </label>
      {error&&<p role="alert" className="text-sm text-red-800">{error}</p>}
      {pending?<div className="space-y-2 rounded-xl bg-amber-50 p-3 text-sm text-amber-900">
        <p>Предыдущая отправка могла сохраниться. Чтобы избежать дубликата,
          повторяется только исходный комментарий с тем же ключом.</p>
        <Button type="button" variant="outline"
          disabled={busy||!pending.idempotencyKey}
          onClick={()=>void send(pending)}>Проверить отправку тем же ключом</Button>
      </div>:
        <Button type="button" disabled={busy||!draft.trim()}
          onClick={add}>{busy?'Отправляем…':'Добавить комментарий'}</Button>}
    </div>}
  </section>;
}
