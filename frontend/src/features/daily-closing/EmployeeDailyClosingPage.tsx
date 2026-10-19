import {useState,type FormEvent} from 'react';
import {Link} from 'react-router';
import {useQuery,useQueryClient} from '@tanstack/react-query';
import {useAuth} from '../auth/AuthProvider';
import {personalCloseApi,personalCloseKeys,type PersonalCloseRequest,type PersonalClosing} from './personal-api';
import {financeApi} from '../finance/api';
import {categories,validateExpense} from '../finance/validation';
import type {ExpenseInput} from '../finance/types';
import {GuardedPost} from '../finance/components/GuardedPost';
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
const categoryLabel=(value:string)=>categories.find(category=>category.value===value)?.label??value;
function shiftDay(value:string,offset:number):string {
 const current=new Date(value+'T12:00:00Z');
 current.setUTCDate(current.getUTCDate()+offset);
 return current.toISOString().slice(0,10);
}
function centsFree(value:unknown):bigint {
 const raw=String(value??0);
 if(!/^\d+(?:\.0+)?$/.test(raw))throw new Error('Invalid whole som');
 return BigInt(raw.split('.')[0]!);
}
function expectedTotal(data:PersonalClosing):string {
 return data.expectedTotal??String(centsFree(data.expectedCash)+centsFree(data.expectedBank));
}
export function EmployeeDailyClosingPage(){
 const {hasPermission,user}=useAuth(),client=useQueryClient();
 const read=hasPermission('DAILY_CLOSING_READ'),write=hasPermission('DAILY_CLOSING_WRITE')&&user?.roles.includes('EMPLOYEE')===true&& !user.roles.some(role=>role==='MANAGER'||role==='ADMIN');
 const [day,setDay]=useState(bishkekToday),[total,setTotal]=useState('');
 const [view,setView]=useState<'current'|'history'>('current');
 const [saleSearch,setSaleSearch]=useState('');
 const [historyDays,setHistoryDays]=useState<7|10|20|30>(30);
 const [reason,setReason]=useState('');
 const [expenseCategory,setExpenseCategory]=useState<ExpenseInput['category']>('OTHER');
 const [expenseAmount,setExpenseAmount]=useState('');
 const [expenseMethod,setExpenseMethod]=useState<ExpenseInput['paymentMethod']>('CASH');
 const [expenseComment,setExpenseComment]=useState('');
 const [error,setError]=useState(''),[pending,setPending]=useState(false);
 const [unknown,setUnknown]=useState(false),[confirm,setConfirm]=useState<PersonalCloseRequest|null>(null);
 const valid=/^\d{4}-\d{2}-\d{2}$/.test(day)&&day<=bishkekToday();
 const report=useQuery({queryKey:personalCloseKeys.report(day),
  queryFn:()=>personalCloseApi.report(day),enabled:read&&valid});
 const dates=useQuery({queryKey:personalCloseKeys.dates,queryFn:personalCloseApi.dates,enabled:read});
 if(!read)return <ForbiddenPage/>;
 function chooseDate(date:string){
  setDay(date);setTotal('');setReason('');
  setError('');setUnknown(false);setConfirm(null);
 }
 function prepare(e:FormEvent){
  e.preventDefault();
  if(!report.data||report.data.closed||!report.data.canClose||pending||unknown)return;
  if(!nonnegativeSom(total)){
   setError('Укажите фактически подтверждённую сумму в целых сомах.');return;
  }
  const expected=expectedTotal(report.data);
  if(centsFree(total)!==centsFree(expected)&&!reason.trim()){
   setError('При расхождении укажите причину.');return;
  }
  setError('');
  setConfirm({expectedCash:report.data.expectedCash,expectedBank:report.data.expectedBank,
   actualCash:null,actualBank:null,cashNote:null,bankNote:null,
   actualTotal:total.trim(),totalNote:reason.trim()||null});
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
      (e.kind==='http'&&e.status!==undefined&&e.status>=500)){
    setUnknown(true);setError('Результат записи неизвестен. Проверьте сохранённый отчёт, не отправляйте повторно.');
   }else setError('Закрытие отклонено сервером. Проверьте права, дату и ожидаемые суммы.');
  }finally{setPending(false);}
 }
 return <div className="space-y-5 bg-[#f5f8ff]/40 pb-6">
  <PageHeader title="Закрытие дня"
   description="Ежедневный операционный отчёт предприятия"/>
  <nav aria-label="Режим отчётов" className="flex flex-wrap items-center gap-2 rounded-2xl bg-white p-3 shadow-sm"> 
   <Button type="button" variant="outline" aria-label="Предыдущий день" onClick={()=>chooseDate(shiftDay(day,-1))}>‹</Button>
   <Button type="button" variant="outline" aria-label="Следующий день"
    disabled={day>=bishkekToday()} onClick={()=>chooseDate(shiftDay(day,1))}>›</Button>
  <div aria-label="Режим отчётов" className="flex min-w-0 flex-1 flex-wrap items-center gap-2">
   <label className="text-sm font-semibold">Отчётная дата
    <Input className="mt-1 w-44" aria-label="Отчётная дата" type="date"
     max={bishkekToday()} value={day} onChange={e=>chooseDate(e.target.value)}/>
   </label>
   <Button variant={view==='current'?'default':'outline'} onClick={()=>{setView('current');chooseDate(bishkekToday());}}>Актуальный отчёт</Button>
   <Button variant={view==='history'?'default':'outline'} onClick={()=>{
    setView('history');
    const previous=new Date(bishkekToday()+'T12:00:00Z');
    previous.setUTCDate(previous.getUTCDate()-1);
    chooseDate(previous.toISOString().slice(0,10));
   }}>Предыдущие отчёты</Button>
  </div></nav>
  {view==='history'&&<section aria-label="Предыдущие отчёты" className="space-y-3 rounded-xl border bg-white p-4">
   <div className="flex gap-2">
    {([7,10,20,30] as const).map(count=><Button key={count} variant={historyDays===count?'default':'outline'}
      onClick={()=>setHistoryDays(count)}>{count} дней</Button>)}
   </div>
   <div className="space-y-2">
    {dates.isPending?<LoadingState label="Загружаем отчёты…"/>:
      (()=>{
       const today=bishkekToday();
       const cutoff=new Date(today+'T12:00:00Z');
       cutoff.setUTCDate(cutoff.getUTCDate()-(historyDays-1));
       const oldest=cutoff.toISOString().slice(0,10);
       const closedDates=(dates.data??[]).filter(value=>value<today&&value>=oldest)
         .sort((a,b)=>b.localeCompare(a));
       return closedDates.length===0
        ? <p className="text-sm text-slate-600">За выбранный период закрытых отчётов нет.</p>
        : closedDates.map(value=><button type="button" key={value} onClick={()=>chooseDate(value)}
          className={'flex w-full justify-between rounded-lg border p-3 text-left '+(day===value?'border-blue-600':'')}>
           <span>{value}</span><span>Закрыт</span>
          </button>);
      })()}
   </div>
  </section>}
  <section className="space-y-3">
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
   <div className="flex flex-col gap-4">
    <section aria-label="Показатели дня" className="grid grid-cols-2 gap-3 lg:grid-cols-4">
      <article className="min-w-0 rounded-2xl border border-slate-100 bg-white p-3 shadow-sm sm:p-4">
       <p className="text-xs font-medium text-slate-500">🛒 Продажи</p>
       <p className="mt-2 break-words text-lg font-bold text-slate-900 sm:text-2xl">{money(report.data.salesValue)}</p>
       <p className="text-xs text-slate-500">{report.data.orders} заказов · {report.data.chairs} кресел</p>
      </article>
      <article className="min-w-0 rounded-2xl border border-slate-100 bg-white p-3 shadow-sm sm:p-4">
       <p className="text-xs font-medium text-slate-500">💵 Расчёт наличных</p>
       <p className="mt-2 break-words text-lg font-bold text-slate-900 sm:text-2xl">{money(report.data.expectedCash)}</p>
       <p className="text-xs text-slate-500">Из операционного отчёта</p>
      </article>
      <article className="min-w-0 rounded-2xl border border-slate-100 bg-white p-3 shadow-sm sm:p-4">
       <p className="text-xs font-medium text-slate-500">💳 Расчёт переводов</p>
       <p className="mt-2 break-words text-lg font-bold text-slate-900 sm:text-2xl">{money(report.data.expectedBank)}</p>
       <p className="text-xs text-slate-500">Из операционного отчёта</p>
      </article>
      <article className="min-w-0 rounded-2xl border border-slate-100 bg-white p-3 shadow-sm sm:p-4">
       <p className="text-xs font-medium text-slate-500">🧾 Расходы сотрудников</p>
       <p className="mt-2 break-words text-lg font-bold text-slate-900 sm:text-2xl">{som(String(centsFree(report.data.expenseCash)+centsFree(report.data.expenseBank)))}</p>
       <p className="text-xs text-slate-500">Все сотрудники за день</p>
      </article>
    </section>
    <section className="rounded-2xl border border-slate-100 bg-white p-4 shadow-sm">

     <h2 className="text-lg font-bold">Продажи, включаемые в закрытие дня</h2>
     <div className="mt-3 flex flex-wrap items-center justify-between gap-3">
       <p className="text-sm text-slate-600">Заказы: {report.data.orders} · Кресла: {report.data.chairs} · Сумма: {money(report.data.salesValue)}</p>
       <input aria-label="Поиск продаж" placeholder="Поиск по заказу, телефону или креслу"
        className="min-h-11 min-w-0 w-full rounded-xl border border-slate-200 px-3 text-sm sm:w-72"
        value={saleSearch} onChange={e=>setSaleSearch(e.target.value)}/>
      </div>
     {report.data.sales.length>0&&<div className="hidden overflow-x-auto md:block">
      <table aria-label="Оплаченные выданные товары дня" className="w-full text-left text-sm">
       <thead><tr className="border-b text-slate-600">
        <th className="p-2">Заказ</th><th className="p-2">Кресла</th><th className="p-2">Контакт и адрес</th>
        <th className="p-2">Состояние</th><th className="p-2 text-right">Сумма</th>
       </tr></thead>
       <tbody>{report.data.sales.filter(s=>(s.saleNumber+' '+(s.phone||'')+' '+(s.products||'')).toLowerCase().includes(saleSearch.trim().toLowerCase())).map(s=><tr className="border-b" key={s.saleId}>
        <td className="p-2"><Link className="font-semibold text-blue-700 underline" to={'/sales/'+encodeURIComponent(s.saleId)}>{s.saleNumber}</Link></td>
        <td className="p-2">{s.products||'Товары в заказе'} · {s.quantity} шт.</td>
        <td className="p-2">{s.phone||'Без номера'}
         {s.address&&<p className="text-xs text-slate-500">{s.address}</p>}</td>
        <td className="p-2"><span className="font-semibold text-emerald-700">Оплачено</span>
         <p className="text-xs text-slate-600">{s.deliveryStatus==='IN_TRANSIT'?'В пути':
          s.deliveryStatus==='DELIVERED'?'Доставлено':'Выдано'}</p></td>
        <td className="p-2 text-right font-semibold">{money(s.total)}</td>
       </tr>)}</tbody>
      </table>
     </div>}
     <div aria-label="Карточки продаж" className="mt-3 space-y-2 md:hidden">
       {report.data.sales.filter(s=>(s.saleNumber+' '+(s.phone||'')+' '+(s.products||'')).toLowerCase().includes(saleSearch.trim().toLowerCase())).map(s=>
       <article key={s.saleId} className="min-w-0 rounded-xl border border-slate-100 p-3">
         <div className="flex flex-wrap items-start justify-between gap-2">
           <Link className="break-all text-sm font-semibold text-blue-700" to={'/sales/'+encodeURIComponent(s.saleId)}>{s.saleNumber}</Link>
           <strong className="text-sm">{money(s.total)}</strong>
         </div>
         <p className="mt-1 text-sm text-slate-600">{s.phone||'Контакт не указан'}</p>
         <p className="mt-1 break-words text-sm">{s.products||'Товары в заказе'} · {s.quantity} шт.</p>
         <div className="mt-2 flex flex-wrap items-center gap-2 text-xs">
          <span className="rounded-lg bg-emerald-50 px-2 py-1 text-emerald-800">Оплачено</span>
          <span className="rounded-lg bg-blue-50 px-2 py-1 text-blue-800">
           {s.deliveryStatus==='IN_TRANSIT'?'В пути':s.deliveryStatus==='DELIVERED'?'Доставлено':'Выдано'}</span>
         </div>
       </article>)}
      </div>
     {report.data.sales.length===0&&<p>Продаж, соответствующих условиям закрытия, за выбранную дату нет.</p>}
    </section>
    <section aria-label="Расходы сотрудников за день" className="space-y-3 rounded-2xl border border-slate-100 bg-white p-4 shadow-sm">
     <h2 className="text-lg font-bold">Расходы сотрудников за день</h2>
     <p className="text-sm text-slate-600">Все расходы сотрудников учитываются один раз; добавить новый расход можно только от собственного имени.</p>
     <div className="grid gap-3 sm:grid-cols-2">
      <p className="rounded-xl bg-slate-50 p-3">Наличные: <strong>{money(report.data.expenseCash)}</strong></p>
      <p className="rounded-xl bg-slate-50 p-3">Переводы: <strong>{money(report.data.expenseBank)}</strong></p>
     </div>
     {!report.data.closed&&write&&hasPermission('EXPENSES_CREATE')&&
       <GuardedPost<ExpenseInput> kind="employee-expense" scope={day} allowed canReplay
         destructive title="Зарегистрировать мой расход?" label="Провести расход"
         operationKey={x=>x.idempotencyKey}
         meta={x=>({amount:x.amount,category:x.category,day:x.expenseDate})}
         post={financeApi.createExpense}
         onDone={async()=>{
           setExpenseAmount('');setExpenseComment('');
           await Promise.all([
             client.invalidateQueries({queryKey:personalCloseKeys.report(day)}),
             client.invalidateQueries({queryKey:['employee-closings','manager',day]}),
           ]);
         }}>
        {({start,locked})=><form aria-label="Добавить расход при закрытии" className="space-y-3 rounded-xl border border-slate-200 bg-slate-50 p-4"
          onSubmit={e=>{e.preventDefault();
            const payload:ExpenseInput={idempotencyKey:crypto.randomUUID(),
              category:expenseCategory,amount:expenseAmount.trim(),
              paymentMethod:expenseMethod,expenseDate:day,comment:expenseComment.trim()||null};
            const issue=validateExpense(payload);
            if(issue){setError(issue);return;}
            setError('');
            start(payload,'Расход '+expenseAmount+' сом, '+day+'. Ответственный определяется вашей авторизованной учётной записью.');
          }}>
         <h3 className="font-semibold">Добавить расход</h3>
         <div className="grid gap-3 sm:grid-cols-2">
          <label className="text-sm font-semibold">Категория
           <select aria-label="Категория моего расхода" className="mt-1 w-full rounded-xl border p-2" value={expenseCategory}
             disabled={locked} onChange={e=>setExpenseCategory(e.target.value as ExpenseInput['category'])}>
             {categories.map(c=><option key={c.value} value={c.value}>{c.label}</option>)}
           </select></label>
          <label className="text-sm font-semibold">Сумма, сом
           <Input aria-label="Сумма моего расхода" inputMode="numeric" value={expenseAmount}
            disabled={locked} onChange={e=>setExpenseAmount(e.target.value)}/></label>
          <label className="text-sm font-semibold">Способ оплаты расхода
           <select aria-label="Способ оплаты моего расхода" className="mt-1 w-full rounded-xl border p-2"
             value={expenseMethod} disabled={locked}
             onChange={e=>setExpenseMethod(e.target.value as ExpenseInput['paymentMethod'])}>
             <option value="CASH">Наличные</option><option value="BANK">Перевод</option>
           </select></label>
          <label className="text-sm font-semibold sm:col-span-2">Назначение (например, бензин, скотч, упаковка)
           <textarea aria-label="Назначение моего расхода" rows={2} maxLength={1000} className="mt-1 w-full rounded-xl border p-3"
            disabled={locked} value={expenseComment} onChange={e=>setExpenseComment(e.target.value)}/></label>
         </div>
         <Button type="submit" disabled={locked||!nonnegativeSom(expenseAmount)||centsFree(expenseAmount)===0n}>Добавить расход</Button>
        </form>}
       </GuardedPost>}
     {report.data.expenses.length===0?<p className="text-sm text-slate-500">За этот день расходы сотрудников не зарегистрированы.</p>:
      <><div className="hidden overflow-x-auto md:block"><table className="w-full text-left text-sm">
       <thead><tr className="border-b"><th className="p-2">Категория</th><th className="p-2">Комментарий</th><th className="p-2">Оплата</th><th className="p-2 text-right">Сумма</th></tr></thead>
       <tbody>{report.data.expenses.map(e=><tr key={e.expenseId} className="border-b">
        <td className="p-2">{categoryLabel(e.category)}<p className="text-xs text-slate-500">{e.createdBy||""}</p></td><td className="p-2">{e.comment||'—'}</td>
        <td className="p-2">{e.paymentMethod==='CASH'?'Наличные':'Перевод'}</td>
        <td className="p-2 text-right font-semibold">{money(e.amount)}</td>
       </tr>)}</tbody>
      </table></div>
      <div aria-label="Карточки расходов" className="space-y-2 md:hidden">
       {report.data.expenses.map(e=><article key={e.expenseId} className="min-w-0 rounded-xl border border-slate-100 p-3 text-sm">
        <div className="flex flex-wrap items-center justify-between gap-2">
         <strong>{categoryLabel(e.category)}</strong><strong>{money(e.amount)}</strong>
        </div>
        <p className="mt-1 break-words text-slate-600">{e.comment||'Без комментария'}</p>
        <p className="mt-1 text-xs text-slate-500">{e.paymentMethod==='CASH'?'Наличные':'Перевод'} · {e.createdBy||'Сотрудник'}</p>
       </article>)}
      </div></>}
    </section>
    <section className="space-y-3 rounded-2xl border border-emerald-100 bg-emerald-50/50 p-4 shadow-sm">
     <h2 className="text-lg font-bold">{report.data.closed?'Операционный день закрыт':'Итог к подтверждению'}</h2>
     <div className="rounded-xl bg-slate-50 p-4"><p className="text-sm text-slate-600">Расчётная сумма к подтверждению · подходящие продажи минус расходы сотрудников</p>
       <p className="mt-2 text-3xl font-bold">{money(expectedTotal(report.data))}</p></div>
     <p className="text-sm text-slate-600">Отчёт включает подходящие продажи всей команды и операционные расходы. Постоянные затраты учитываются отдельно в финансовой аналитике.</p>
     {(BigInt(report.data.expectedCash)<0n||BigInt(report.data.expectedBank)<0n)&&
      <p role="alert" className="rounded-xl bg-amber-50 p-3 text-sm text-amber-900">Операционные расходы превышают сумму подходящих продаж. Перед закрытием нужна проверка с менеджером.</p>}
     {report.data.closed&&<div className="space-y-2">
      <p>Подтверждённая сумма: <strong>{money(report.data.actualTotal??String(centsFree(report.data.actualCash)+centsFree(report.data.actualBank)))}</strong></p>
      {(report.data.totalNote||report.data.cashNote||report.data.bankNote)&&
        <p>Причина расхождения: {report.data.totalNote||[report.data.cashNote,report.data.bankNote].filter(Boolean).join('; ')}</p>}
      {report.data.collectionsChangedAfterClose&&<p role="alert" className="text-amber-800">
       После закрытия появились изменения. Сохранённый отчёт не перезаписывается; обратитесь к менеджеру.</p>}
     </div>}
     {!report.data.closed&&write&&report.data.canClose&&
      <form aria-label="Операционное закрытие дня" onSubmit={prepare} className="space-y-3">
       <label className="block space-y-1 text-sm font-semibold">Фактически подтверждаю, сом
        <Input aria-label="Фактическая общая сумма" inputMode="numeric" value={total} maxLength={17}
         onChange={e=>setTotal(e.target.value)}/></label>
       <label className="block space-y-1 text-sm font-semibold">Причина расхождения {nonnegativeSom(total)&&centsFree(total)!==centsFree(expectedTotal(report.data))?'*':''}
        <textarea aria-label="Общая причина расхождения" className="w-full rounded-lg border p-3"
         maxLength={2000} value={reason} onChange={e=>setReason(e.target.value)}/></label>
       <Button type="submit" disabled={pending||unknown||!nonnegativeSom(total)}>
        Подтвердить и закрыть день</Button>
      </form>}
     {!report.data.closed&&!report.data.canClose&&<p className="text-amber-800">
       Эта дата недоступна для обычного операционного закрытия.</p>}
    </section>
   </div>}
  <ConfirmDialog open={confirm!==null} onOpenChange={v=>{if(!v)setConfirm(null);}}
   title="Подтвердить общее закрытие дня?"
   description={confirm?'Дата '+day+'. Подтверждаемая сумма '+money(confirm.actualTotal??'0')+
    '. Финансовые счета компании не изменяются.':''}
   busy={pending} confirmLabel="Закрыть операционный день" onConfirm={()=>void close()}/>
 </div>;
}
