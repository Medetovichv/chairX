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
 return <div className="space-y-5">
  <PageHeader title="Моё закрытие дня"
   description="Все завершённые продажи дня, мои поступления и только мои расходы. Чужие расходы недоступны."/>
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
     <div className="rounded-xl bg-slate-50 p-4"><p className="text-sm text-slate-600">Расчётная сумма к подтверждению · поступления минус мои расходы</p>
       <p className="mt-2 text-3xl font-bold">{money(expectedTotal(report.data))}</p></div>
     <p className="text-sm text-slate-600">Это расчёт после вычета расходов, зарегистрированных вами за этот день. Остатки компании и расходы других сотрудников здесь не показываются.</p>
     {(BigInt(report.data.expectedCash)<0n||BigInt(report.data.expectedBank)<0n)&&
      <p role="alert" className="rounded-xl bg-amber-50 p-3 text-sm text-amber-900">Ваши расходы превышают личные поступления. Перед закрытием требуется сверка с менеджером.</p>}
     {report.data.closed&&<div className="space-y-2">
      <p>Подтверждённая сумма: <strong>{money(report.data.actualTotal??String(centsFree(report.data.actualCash)+centsFree(report.data.actualBank)))}</strong></p>
      {(report.data.totalNote||report.data.cashNote||report.data.bankNote)&&
        <p>Причина расхождения: {report.data.totalNote||[report.data.cashNote,report.data.bankNote].filter(Boolean).join('; ')}</p>}
      {report.data.collectionsChangedAfterClose&&<p role="alert" className="text-amber-800">
       После закрытия появились изменения. Сохранённый отчёт не перезаписывается; обратитесь к менеджеру.</p>}
     </div>}
     {!report.data.closed&&write&&report.data.canClose&&
      <form aria-label="Личное закрытие смены" onSubmit={prepare} className="space-y-3">
       <label className="block space-y-1 text-sm font-semibold">Фактически подтверждаю, сом
        <Input aria-label="Фактическая общая сумма" inputMode="numeric" value={total} maxLength={17}
         onChange={e=>setTotal(e.target.value)}/></label>
       <label className="block space-y-1 text-sm font-semibold">Причина расхождения {nonnegativeSom(total)&&centsFree(total)!==centsFree(expectedTotal(report.data))?'*':''}
        <textarea aria-label="Общая причина расхождения" className="w-full rounded-lg border p-3"
         maxLength={2000} value={reason} onChange={e=>setReason(e.target.value)}/></label>
       <Button type="submit" disabled={pending||unknown||!nonnegativeSom(total)}>
        Подтвердить и закрыть смену</Button>
      </form>}
     {!report.data.closed&&!report.data.canClose&&<p className="text-amber-800">
       Эта дата недоступна для обычного личного закрытия.</p>}
    </section>
    <section className="rounded-xl border bg-white p-4">
     <h2 className="text-lg font-bold">Оплаченные и выданные заказы за день</h2>
     <p>Заказы: {report.data.orders} · Кресла: {report.data.chairs} ·
      Сумма продаж: {money(report.data.salesValue)}</p>
     {report.data.sales.length>0&&<div className="overflow-x-auto">
      <table aria-label="Оплаченные выданные товары дня" className="w-full text-left text-sm">
       <thead><tr className="border-b text-slate-600">
        <th className="p-2">Заказ</th><th className="p-2">Кресла</th><th className="p-2">Контакт и адрес</th>
        <th className="p-2">Состояние</th><th className="p-2 text-right">Сумма</th>
       </tr></thead>
       <tbody>{report.data.sales.map(s=><tr className="border-b" key={s.saleId}>
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
     {report.data.sales.length===0&&<p>Оплаченных выданных продаж за выбранную дату нет.</p>}
    </section>
    <section aria-label="Мои расходы за день" className="space-y-3 rounded-2xl border border-slate-200 bg-white p-4">
     <h2 className="text-lg font-bold">Мои расходы за день</h2>
     <p className="text-sm text-slate-600">Только расходы, созданные под вашей учётной записью (createdBy). Чужие расходы не загружаются.</p>
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
         <h3 className="font-semibold">Добавить мой расход</h3>
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
     {report.data.expenses.length===0?<p className="text-sm text-slate-500">Вы не регистрировали расходы за этот день.</p>:
      <div className="overflow-x-auto"><table className="w-full text-left text-sm">
       <thead><tr className="border-b"><th className="p-2">Категория</th><th className="p-2">Комментарий</th><th className="p-2">Оплата</th><th className="p-2 text-right">Сумма</th></tr></thead>
       <tbody>{report.data.expenses.map(e=><tr key={e.expenseId} className="border-b">
        <td className="p-2">{e.category}</td><td className="p-2">{e.comment||'—'}</td>
        <td className="p-2">{e.paymentMethod==='CASH'?'Наличные':'Перевод'}</td>
        <td className="p-2 text-right font-semibold">{money(e.amount)}</td>
       </tr>)}</tbody>
      </table></div>}
    </section>
   </div>}
  <ConfirmDialog open={confirm!==null} onOpenChange={v=>{if(!v)setConfirm(null);}}
   title="Подтвердить моё закрытие?"
   description={confirm?'Дата '+day+'. Подтверждаемая сумма '+money(confirm.actualTotal??'0')+
    '. Финансовые счета компании не изменяются.':''}
   busy={pending} confirmLabel="Сохранить моё закрытие" onConfirm={()=>void close()}/>
 </div>;
}
