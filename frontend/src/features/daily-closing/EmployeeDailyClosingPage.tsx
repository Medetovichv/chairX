import {useState,type FormEvent} from 'react';
import {useQuery,useQueryClient} from '@tanstack/react-query';
import {useAuth} from '../auth/AuthProvider';
import {personalCloseApi,personalCloseKeys,type PersonalCloseRequest} from './personal-api';
import {ApiClientError} from '../../shared/api/api-error';
import {bishkekToday,nonnegativeSom,som} from '../finance/validation';
import {PageHeader} from '../../shared/components/PageHeader';
import {LoadingState} from '../../shared/components/LoadingState';
import {ErrorState} from '../../shared/components/ErrorState';
import {Button} from '../../shared/ui/button';
import {Input} from '../../shared/ui/input';
import {ConfirmDialog} from '../../shared/ui/confirm-dialog';
import {ForbiddenPage} from '../../pages/ForbiddenPage';

const money=(value:string)=>som(value);
export function EmployeeDailyClosingPage(){
 const {hasPermission}=useAuth(),client=useQueryClient();
 const read=hasPermission('DAILY_CLOSING_READ'),write=hasPermission('DAILY_CLOSING_WRITE');
 const [day,setDay]=useState(bishkekToday),[cash,setCash]=useState(''),[bank,setBank]=useState('');
 const [cashNote,setCashNote]=useState(''),[bankNote,setBankNote]=useState('');
 const [error,setError]=useState(''),[pending,setPending]=useState(false);
 const [unknown,setUnknown]=useState(false),[confirm,setConfirm]=useState<PersonalCloseRequest|null>(null);
 const valid=/^\d{4}-\d{2}-\d{2}$/.test(day)&&day<=bishkekToday();
 const report=useQuery({queryKey:personalCloseKeys.report(day),
  queryFn:()=>personalCloseApi.report(day),enabled:read&&valid});
 const dates=useQuery({queryKey:personalCloseKeys.dates,queryFn:personalCloseApi.dates,enabled:read});
 if(!read)return <ForbiddenPage/>;
 function chooseDate(date:string){
  setDay(date);setCash('');setBank('');setCashNote('');setBankNote('');
  setError('');setUnknown(false);setConfirm(null);
 }
 function prepare(e:FormEvent){
  e.preventDefault();
  if(!report.data||report.data.closed||!report.data.canClose||pending||unknown)return;
  if(!nonnegativeSom(cash)||!nonnegativeSom(bank)){
   setError('Введите целые неотрицательные суммы в сомах.');return;
  }
  const nCash=cashNote.trim()||null,nBank=bankNote.trim()||null;
  if((BigInt(cash)!==BigInt(report.data.expectedCash)&&!nCash)||
     (BigInt(bank)!==BigInt(report.data.expectedBank)&&!nBank)){
   setError('Укажите причину каждого расхождения с собранными деньгами.');return;
  }
  setError('');
  setConfirm({expectedCash:report.data.expectedCash,expectedBank:report.data.expectedBank,
   actualCash:cash,actualBank:bank,cashNote:nCash,bankNote:nBank});
 }
 async function close(){
  if(!confirm||pending||unknown)return;
  setPending(true);setError('');
  try{
   // A changed amount or newly closed day must stop BEFORE sending the POST.
   const fresh=await personalCloseApi.report(day);
   if(fresh.closed||!fresh.canClose||
      BigInt(fresh.expectedCash)!==BigInt(confirm.expectedCash)||
      BigInt(fresh.expectedBank)!==BigInt(confirm.expectedBank)){
    setError('Данные изменились. Обновите расчёт и подтвердите заново.');
    await client.invalidateQueries({queryKey:personalCloseKeys.report(day)});
    setConfirm(null);return;
   }
   await personalCloseApi.close(day,confirm);
   setConfirm(null);
   await Promise.all([client.invalidateQueries({queryKey:personalCloseKeys.report(day)}),
    client.invalidateQueries({queryKey:personalCloseKeys.dates})]);
  }catch(e){
   setConfirm(null);
   if(!(e instanceof ApiClientError)||e.kind==='network'||e.kind==='parse'||
      (e.kind==='http'&&e.status>=500)){
    setUnknown(true);setError('Результат записи неизвестен. Проверьте сохранённый отчёт, не отправляйте повторно.');
   }else setError('Закрытие отклонено сервером. Проверьте права, дату и ожидаемые суммы.');
  }finally{setPending(false);}
 }
 return <div className="space-y-5">
  <PageHeader title="Моё закрытие дня"
   description="Только мои завершённые продажи и деньги, которые зарегистрировал я. Общие CASH/BANK компании здесь не показываются."/>
  <section className="space-y-3 rounded-xl border bg-white p-4">
   <label className="block text-sm font-semibold">Дата (Asia/Bishkek)
    <Input aria-label="Личная отчётная дата" type="date" value={day}
     max={bishkekToday()} onChange={e=>chooseDate(e.target.value)}/>
   </label>
   {dates.data&&dates.data.length>0&&<div className="flex flex-wrap gap-2">
    {dates.data.map(d=><Button key={d} variant="outline" onClick={()=>chooseDate(d)}>{d}</Button>)}
   </div>}
   {unknown&&<Button variant="outline" onClick={()=>{
    void client.invalidateQueries({queryKey:personalCloseKeys.report(day)}).then(()=>{
     setUnknown(false);setError('');
    });
   }}>Проверить отчёт</Button>}
   {error&&<p role="alert" className="text-red-700">{error}</p>}
  </section>
  {!valid?<p role="alert">Выберите дату не позже сегодняшнего дня в Бишкеке.</p>:
   report.isPending?<LoadingState label="Загружаем мою смену…"/>:
   report.isError?<ErrorState error={report.error} onRetry={()=>void report.refetch()}/>:
   <div className="space-y-4">
    <section className="space-y-3 rounded-xl border bg-white p-4">
     <h2 className="text-lg font-bold">{report.data.closed?'Мой день закрыт':'Мой день открыт'}</h2>
     <p>Зарегистрировано мной: CASH <strong>{money(report.data.expectedCash)}</strong>,
      BANK <strong>{money(report.data.expectedBank)}</strong></p>
     <p className="text-sm text-slate-600">Это суммы поступлений, оформленных мной за день, а не общий остаток кассы или банка. Возвраты и отмены отражаются в общем финансовом учёте отдельно.</p>
     {report.data.closed&&<div className="space-y-2">
      <p>Фактически CASH: <strong>{money(report.data.actualCash??'0')}</strong>.
       Фактически BANK: <strong>{money(report.data.actualBank??'0')}</strong>.</p>
      {report.data.cashNote&&<p>Примечание CASH: {report.data.cashNote}</p>}
      {report.data.bankNote&&<p>Примечание BANK: {report.data.bankNote}</p>}
      {report.data.collectionsChangedAfterClose&&<p role="alert" className="text-amber-800">
       После моего закрытия появились дополнительные проводки. Требуется сверка у руководителя; сохранённый отчёт не перезаписывается.</p>}
     </div>}
     {!report.data.closed&&write&&report.data.canClose&&
      <form aria-label="Личное закрытие смены" onSubmit={prepare} className="space-y-3">
       <div className="grid gap-3 sm:grid-cols-2">
        <label>Фактически собрано наличными, сом
         <Input aria-label="Личный факт CASH" inputMode="numeric" value={cash} maxLength={17}
          onChange={e=>setCash(e.target.value)}/></label>
        <label>Фактически собрано через банк, сом
         <Input aria-label="Личный факт BANK" inputMode="numeric" value={bank} maxLength={17}
          onChange={e=>setBank(e.target.value)}/></label>
        <label>Причина расхождения CASH
         <textarea aria-label="Личная причина CASH" className="w-full rounded-lg border p-2"
          maxLength={2000} value={cashNote} onChange={e=>setCashNote(e.target.value)}/></label>
        <label>Причина расхождения BANK
         <textarea aria-label="Личная причина BANK" className="w-full rounded-lg border p-2"
          maxLength={2000} value={bankNote} onChange={e=>setBankNote(e.target.value)}/></label>
       </div>
       <Button type="submit" disabled={pending||unknown||!nonnegativeSom(cash)||!nonnegativeSom(bank)}>
        Закрыть мою смену</Button>
      </form>}
     {!report.data.closed&&!report.data.canClose&&<p className="text-amber-800">
       Эта дата недоступна для обычного личного закрытия.</p>}
    </section>
    <section className="rounded-xl border bg-white p-4">
     <h2 className="text-lg font-bold">Мои завершённые продажи</h2>
     <p>Заказы: {report.data.orders} · Кресла: {report.data.chairs} ·
      Сумма продаж: {money(report.data.salesValue)}</p>
     {report.data.sales.length>0&&<ul className="divide-y">
      {report.data.sales.map(s=><li className="py-2 text-sm" key={s.saleId}>
       {s.saleNumber} · {s.quantity} шт. · {money(s.total)}
      </li>)}
     </ul>}
     {report.data.sales.length===0&&<p>Завершённых продаж за выбранную дату нет.</p>}
    </section>
   </div>}
  <ConfirmDialog open={confirm!==null} onOpenChange={v=>{if(!v)setConfirm(null);}}
   title="Подтвердить моё закрытие?"
   description={confirm?'Дата '+day+'. Мои CASH '+money(confirm.actualCash)+
    ', BANK '+money(confirm.actualBank)+'. Общие деньги компании не изменятся.':''}
   busy={pending} confirmLabel="Сохранить моё закрытие" onConfirm={()=>void close()}/>
 </div>;
}
