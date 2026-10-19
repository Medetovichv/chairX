import { useState } from 'react';
import { Link } from 'react-router';
import { EmployeeDailyClosingPage } from '../features/daily-closing/EmployeeDailyClosingPage';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../features/auth/AuthProvider';
import { closingApi, closingKeys } from '../features/daily-closing/api';
import { ApiClientError } from '../shared/api/api-error';
import { validClosingDate } from '../features/daily-closing/validation';
import type { ClosingAccount, ClosingReport } from '../features/daily-closing/types';
import { postVerifiedClosing, type ApprovedClosing } from '../features/daily-closing/verified-close';
import { GuardedPost } from '../features/finance/components/GuardedPost';
import { financeApi } from '../features/finance/api';
import { categories,validateExpense } from '../features/finance/validation';
import type { ExpenseInput } from '../features/finance/types';
import { bishkekToday, nonnegativeSom, som, dateLabel } from '../features/finance/validation';
import { ClosingMaintenance } from '../features/daily-closing/ClosingMaintenance';
import { ManagerEmployeeClosingSummary } from '../features/daily-closing/ManagerEmployeeClosingSummary';
import { managerClosingApi } from '../features/daily-closing/personal-api';
import { PageHeader } from '../shared/components/PageHeader';
import { LoadingState } from '../shared/components/LoadingState';
import { ErrorState } from '../shared/components/ErrorState';
import { ForbiddenPage } from './ForbiddenPage';
import { Input } from '../shared/ui/input';
import { Button } from '../shared/ui/button';

function AccountView({ title, account }: { title: string; account: ClosingAccount }) {
  return <article className="space-y-2 rounded-xl border bg-white p-4 text-sm">
    <h3 className="font-bold">{title}</h3>
    <p>Ожидалось: <strong>{som(account.expected)}</strong></p>
    <p>Фактически: <strong>{som(account.actual)}</strong></p>
    <p>Разница (факт − ожидание): <strong>{som(account.difference)}</strong></p>
    {account.note && <p className="whitespace-pre-wrap">Причина: {account.note}</p>}
    {account.recalculated && <p className="text-amber-800">Ожидание пересчитано после поздней корректировки.
      Исходное при закрытии: {som(account.originalExpected)}.</p>}
    {account.requiresReview && <p role="alert" className="font-semibold text-red-700">
      Требуется сверка: изменившееся ожидание не подтверждено новым фактическим остатком.
    </p>}
  </article>;
}
function ExistingReport({ report }: { report: ClosingReport }) {
  return <section aria-label="Закрытый отчёт" className="space-y-3">
    <h2 className="text-lg font-bold">День закрыт · версия {report.version}</h2>
    <p className="text-sm">Создал {report.createdBy} · {dateLabel(report.createdAt)}</p>
    <details className="rounded-xl border border-slate-200 bg-white p-3">
      <summary className="cursor-pointer text-sm font-semibold">Архивные финансовые показатели</summary>
      <div className="mt-3 grid gap-3 sm:grid-cols-2">
        <AccountView title="Наличные" account={report.cash}/>
        <AccountView title="Перевод" account={report.bank}/>
      </div>
    </details>
  </section>;
}
function CompanyDailyClosingPage() {
  const { hasPermission, user } = useAuth();
  const read = hasPermission('DAILY_CLOSING_READ') || hasPermission('FINANCE_READ');
  const manager=user?.roles.includes('MANAGER')===true && !user?.roles.includes('ADMIN');
  const write = user?.roles.some(role=>role==='ADMIN'||role==='MANAGER')===true && hasPermission('DAILY_CLOSING_WRITE');
  const client = useQueryClient();
  const [date, setDate] = useState(bishkekToday);
  const [expenseAmount,setExpenseAmount]=useState('');
  const [expenseCategory,setExpenseCategory]=useState<ExpenseInput['category']>('OTHER');
  const [expenseMethod,setExpenseMethod]=useState<ExpenseInput['paymentMethod']>('CASH');
  const [expenseComment,setExpenseComment]=useState('');
  const [error, setError] = useState('');
  const [showHistory,setShowHistory]=useState(false);
  const [personalMode,setPersonalMode]=useState(false);
  const valid = validClosingDate(date);

  const dates = useQuery({ queryKey: closingKeys.dates, queryFn: closingApi.dates, enabled: read });
  const access = useQuery({ queryKey: closingKeys.access(date),
    queryFn: () => closingApi.access(date), enabled: read && valid });
  const exists = access.isSuccess && access.data.reportExists;
  const staff=useQuery({queryKey:['employee-closings','manager',date],
    queryFn:()=>managerClosingApi.report(date),
    enabled:read&&valid&&hasPermission('DAILY_CLOSING_READ')});
  const canFinancialClose=!manager||staff.data?.operationalClosed===true;
  const report = useQuery({ queryKey: closingKeys.report(date),
    queryFn: () => closingApi.report(date), enabled: read && valid && exists });
  const preview = useQuery({ queryKey: closingKeys.preview(date),
    queryFn: () => closingApi.preview(date),
    enabled: read && valid && access.isSuccess && !exists });
  const sales = useQuery({ queryKey: closingKeys.sales(date),
    queryFn: () => closingApi.sales(date), enabled: read && valid });

  if (!read) return <ForbiddenPage/>;
  if (personalMode) return <div className="space-y-4">
    <Button type="button" variant="outline" onClick={()=>setPersonalMode(false)}>← Общая сверка компании</Button>
    <EmployeeDailyClosingPage/>
  </div>;
  function chooseDate(next: string) {
    setDate(next); setError('');
  }
  async function refreshed() {
    await Promise.all([
      client.invalidateQueries({ queryKey: closingKeys.dates }),
      client.invalidateQueries({ queryKey: closingKeys.access(date) }),
      client.invalidateQueries({ queryKey: closingKeys.report(date) }),
      client.invalidateQueries({ queryKey: closingKeys.preview(date) }),
      client.invalidateQueries({ queryKey: closingKeys.sales(date) }),
      client.invalidateQueries({ queryKey: ['employee-closings','manager',date] }),
    ]);
  }
  return <div className="space-y-5">

    <PageHeader title="Закрытие дня" description="Ежедневный операционный и финансовый отчёт"/>

    <div className="flex flex-wrap items-center gap-3 rounded-2xl bg-white p-3 shadow-sm">
      <Button type="button" variant="outline" aria-label="Предыдущий день" onClick={()=>{
        const previous=new Date(date+'T12:00:00Z');previous.setUTCDate(previous.getUTCDate()-1);
        chooseDate(previous.toISOString().slice(0,10));
      }}>‹</Button>
      <Button type="button" variant="outline" aria-label="Следующий день" disabled={date>=bishkekToday()} onClick={()=>{
        const next=new Date(date+'T12:00:00Z');next.setUTCDate(next.getUTCDate()+1);
        chooseDate(next.toISOString().slice(0,10));
      }}>›</Button>
      <label className="block w-44 space-y-1 text-sm font-semibold">Отчётная дата
        <Input type="date" aria-label="Дата закрытия" value={date}
          max={bishkekToday()} onChange={e => chooseDate(e.target.value)}/>
      </label>
      {!valid && <p role="alert" className="text-red-700">Выберите действительную дату не позднее сегодня в Бишкеке.</p>}
      <Button type="button" onClick={()=>{chooseDate(bishkekToday());setShowHistory(false);}}>Актуальный отчёт</Button>
      <Button type="button" variant="outline" aria-expanded={showHistory}
        onClick={()=>setShowHistory(v=>!v)}>{showHistory?'Скрыть историю':'Предыдущие отчёты'}</Button>
      {showHistory&&<section aria-label="История финансовых отчётов" className="space-y-3 rounded-xl bg-slate-50 p-4">
       <h2 className="font-semibold">Все закрытые финансовые отчёты</h2>
       {dates.isPending?<LoadingState label="Загружаем историю…"/>:
        dates.isError?<ErrorState error={dates.error} onRetry={()=>void dates.refetch()}/>:
        <div className="flex flex-wrap gap-2">{dates.data.slice().sort((x,y)=>y.localeCompare(x)).slice(0,7).map(day=><Button type="button" key={day}
          variant={day===date?'default':'outline'}
          onClick={()=>{chooseDate(day);setShowHistory(false);}}>{day}</Button>)}
        </div>}
      </section>}
    </div>
    {valid&&<section aria-label="Финансовые показатели дня" className="grid grid-cols-2 gap-3 lg:grid-cols-4">
      <article className="min-w-0 rounded-2xl border border-slate-100 bg-white p-3 shadow-sm sm:p-4">
       <p className="text-xs font-medium text-slate-500">🛒 Завершённые продажи</p>
       <p className="mt-2 break-words text-lg font-bold sm:text-2xl">
        {sales.isSuccess&&sales.data.totals.value!==null?som(sales.data.totals.value):'—'}</p>
       <p className="text-xs text-slate-500">
        {sales.isSuccess?`${sales.data.totals.orders??'—'} заказов · ${sales.data.totals.chairs??'—'} кресел`:'Данные загружаются'}</p>
      </article>
      <article className="min-w-0 rounded-2xl border border-slate-100 bg-white p-3 shadow-sm sm:p-4">
       <p className="text-xs font-medium text-slate-500">💵 Расчёт наличных</p>
       <p className="mt-2 break-words text-lg font-bold sm:text-2xl">
        {exists?(report.isSuccess?som(report.data.cash.expected):'—'):(preview.isSuccess?som(preview.data.expectedCash):'—')}</p>
       <p className="text-xs text-slate-500">По данным финансового отчёта</p>
      </article>
      <article className="min-w-0 rounded-2xl border border-slate-100 bg-white p-3 shadow-sm sm:p-4">
       <p className="text-xs font-medium text-slate-500">💳 Расчёт переводов</p>
       <p className="mt-2 break-words text-lg font-bold sm:text-2xl">
        {exists?(report.isSuccess?som(report.data.bank.expected):'—'):(preview.isSuccess?som(preview.data.expectedBank):'—')}</p>
       <p className="text-xs text-slate-500">По данным финансового отчёта</p>
      </article>
      <article className="min-w-0 rounded-2xl border border-slate-100 bg-white p-3 shadow-sm sm:p-4">
       <p className="text-xs font-medium text-slate-500">🧾 Расходы сотрудников</p>
       <p className="mt-2 break-words text-lg font-bold sm:text-2xl">
        {staff.isSuccess?som(staff.data.expenseTotal):'—'}</p>
       <p className="text-xs text-slate-500">{staff.isError?'Не удалось загрузить': 'Операционные расходы за день'}</p>
      </article>
    </section>}
    {valid && user?.roles.some(role=>role==='ADMIN'||role==='MANAGER') && hasPermission('DAILY_CLOSING_READ') && <ManagerEmployeeClosingSummary date={date}/>}
    {valid && access.isSuccess && !exists && access.data.canEdit &&
     hasPermission('EXPENSES_CREATE') && <details className="rounded-xl border bg-white p-4">
      <summary className="cursor-pointer font-semibold">+ Добавить расход менеджера</summary>
      <p className="mt-2 text-sm text-slate-600">Расход создаётся от вашей учётной записи и включается в финансовый расчёт.</p>
      <GuardedPost<ExpenseInput> kind="manager-closing-expense" scope={date}
       allowed canReplay destructive title="Подтвердить расход предприятия?"
       label="Провести расход" operationKey={x=>x.idempotencyKey}
       meta={x=>({date:x.expenseDate,amount:x.amount,category:x.category})}
       post={financeApi.createExpense}
       onDone={async()=>{setExpenseAmount('');setExpenseComment('');await refreshed();}}>
       {({start,locked})=><form className="mt-3 grid gap-3 sm:grid-cols-2"
        onSubmit={e=>{e.preventDefault();
         const payload:ExpenseInput={idempotencyKey:crypto.randomUUID(),
           category:expenseCategory,amount:expenseAmount.trim(),
           paymentMethod:expenseMethod,expenseDate:date,comment:expenseComment.trim()||null};
         const issue=validateExpense(payload);if(issue){setError(issue);return;}
         setError('');start(payload,'Расход '+expenseAmount+' сом, '+date);
        }}>
        <label className="text-sm font-semibold">Категория
         <select className="mt-1 w-full rounded-lg border p-2" value={expenseCategory}
          disabled={locked} onChange={e=>setExpenseCategory(e.target.value as ExpenseInput['category'])}>
          {categories.map(item=><option key={item.value} value={item.value}>{item.label}</option>)}
         </select></label>
        <label className="text-sm font-semibold">Сумма, сом
         <Input inputMode="numeric" value={expenseAmount} disabled={locked}
          onChange={e=>setExpenseAmount(e.target.value)}/></label>
        <label className="text-sm font-semibold">Способ оплаты
         <select className="mt-1 w-full rounded-lg border p-2" value={expenseMethod}
          disabled={locked} onChange={e=>setExpenseMethod(e.target.value as ExpenseInput['paymentMethod'])}>
          <option value="CASH">Наличные</option><option value="BANK">Перевод</option>
         </select></label>
        <label className="text-sm font-semibold">Комментарий
         <Input value={expenseComment} disabled={locked}
          maxLength={1000} onChange={e=>setExpenseComment(e.target.value)}/></label>
        {error&&<p role="alert" className="text-red-700 sm:col-span-2">{error}</p>}
        <Button disabled={locked||!nonnegativeSom(expenseAmount)||expenseAmount==='0'} type="submit">
         Добавить расход</Button>
       </form>}
      </GuardedPost>
     </details>}
    {valid && (access.isPending ? <LoadingState label="Проверяем доступность отчёта…"/> :
      access.isError ? <ErrorState error={access.error} onRetry={() => void access.refetch()}/> :
      <section aria-label="Статус закрытия" className="rounded-xl border bg-white p-4 text-sm">
        <p>{access.data.reportExists ? 'Отчёт уже закрыт' : 'Отчёт ещё не создан'}
          {' · '}{access.data.accessStatus === 'OPEN' ? 'Открытый период' :
            access.data.accessStatus === 'TEMPORARILY_UNLOCKED' ? 'Временная разблокировка' :
            'Заблокированный период'}</p>
        {access.data.unlockedUntil && <p>Разблокировка до {dateLabel(access.data.unlockedUntil)}</p>}
        {!access.data.canEdit && <p className="text-amber-800">Редактирование этой даты недоступно по правам или сроку.</p>}
      </section>)}
    {valid && exists && (report.isPending ? <LoadingState label="Загружаем закрытый отчёт…"/> :
      report.isError ? <ErrorState error={report.error} onRetry={() => void report.refetch()}/> :
      <ExistingReport report={report.data}/>)}
    {valid && access.isSuccess && (!exists || report.isSuccess) &&
      <ClosingMaintenance key={date + ':' + (report.data?.version ?? 'new')} date={date}
        access={access.data} report={report.data} onChanged={refreshed}/>}
    {valid && <section aria-label="Завершённые продажи" className="space-y-5 rounded-2xl border border-slate-100 bg-white p-4 shadow-sm sm:p-5">
      <div className="flex flex-wrap items-center justify-between gap-2"><h2 className="text-xl font-bold">Отчёт о продажах за {date}</h2><span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold">{exists?"Закрытый день":"Сегодняшние данные до закрытия"}</span></div>
      <p className="text-sm text-slate-600">Список завершённых заказов с креслами, количеством, клиентами и оплатой.</p>
      {sales.isPending ? <LoadingState label="Загружаем продажи дня…"/> :
        sales.isError ? <ErrorState error={sales.error} onRetry={() => void sales.refetch()}/> :
        <>
          {!sales.data.closed && <p className="text-amber-800">Предварительные данные: день ещё не закрыт.</p>}
          {sales.data.closed && !sales.data.snapshotAvailable &&
            <p role="alert" className="text-amber-800">Для этого исторического отчёта нет сохранённого снимка продаж.
              Не подменяем его текущими данными.</p>}
          {sales.data.closed && sales.data.snapshotAvailable && (sales.data.lateCompletionCount ?? 0) > 0 &&
            <p className="text-amber-800">После закрытия завершено дополнительных продаж: {sales.data.lateCompletionCount}.
              Они не изменяют сохранённый снимок.</p>}
          <div className="grid gap-3 sm:grid-cols-3">
            <div className="rounded-xl bg-slate-50 p-4"><p className="text-xs text-slate-600">Завершено заказов</p><p className="mt-1 text-2xl font-bold">{sales.data.totals.orders??'—'}</p></div>
            <div className="rounded-xl bg-slate-50 p-4"><p className="text-xs text-slate-600">Продано кресел</p><p className="mt-1 text-2xl font-bold">{sales.data.totals.chairs??'—'}</p></div>
            <div className="rounded-xl bg-slate-50 p-4"><p className="text-xs text-slate-600">Итого продажи</p><p className="mt-1 text-2xl font-bold">{sales.data.totals.value===null?'—':som(sales.data.totals.value)}</p></div>
          </div>
          {sales.data.items.length === 0 ? <p className="text-sm text-slate-500">Нет подтверждённых завершённых продаж.</p> :
            <div className="hidden overflow-x-auto md:block"><table className="w-full text-left text-sm">
              <thead><tr className="border-b">
                <th className="p-2">Продажа</th><th className="p-2">Клиент</th><th className="p-2">Телефон</th>
                <th className="p-2">Товар</th><th className="p-2">Шт.</th>
                <th className="p-2">Сумма</th><th className="p-2">Оплата</th><th className="p-2">Доставка</th><th className="p-2">Завершено</th>
              </tr></thead><tbody>{sales.data.items.map(item => <tr key={item.saleId} className="border-b">
                <td className="p-2">{item.saleNumber}</td>
                <td className="p-2">{item.customerName ?? '—'}</td><td className="p-2">{item.phone??'—'}</td>
                <td className="p-2">{item.products ?? '—'}</td>
                <td className="p-2">{item.quantity}</td>
                <td className="p-2">{som(item.total)}</td>
                <td className="p-2">{item.paymentStatus==='PAID'?'Оплачено':item.paymentStatus==='PARTIAL'||item.paymentStatus==='PARTIALLY_PAID'?'Частично оплачено':item.paymentStatus==='UNPAID'?'Не оплачено':item.paymentStatus}</td>
                <td className="p-2">{item.deliveryStatus==='DELIVERED'?'Доставлено':item.deliveryStatus==='IN_TRANSIT'?'В пути':item.fulfillmentType==='SELF_PICKUP'?'Самовывоз':item.deliveryStatus??'—'}</td>
                <td className="p-2">{dateLabel(item.completedAt)}</td>
              </tr>)}</tbody>
            </table></div>}
          {sales.data.items.length>0&&<div className="space-y-2 md:hidden" aria-label="Карточки завершённых продаж">
           {sales.data.items.map(item=><article key={item.saleId} className="min-w-0 rounded-xl border border-slate-100 p-3 text-sm">
            <div className="flex flex-wrap items-start justify-between gap-2">
             <Link to={'/sales/'+encodeURIComponent(item.saleId)} className="break-all font-semibold text-blue-700">{item.saleNumber}</Link>
             <strong>{som(item.total)}</strong>
            </div>
            <p className="mt-1 break-words">{item.products??'Товары в заказе'} · {item.quantity} шт.</p>
            <p className="mt-1 text-slate-600">{item.customerName||item.phone||'Контакт не указан'}</p>
            <div className="mt-2 flex flex-wrap gap-2 text-xs">
             <span className="rounded-md bg-emerald-50 px-2 py-1 text-emerald-800">{item.paymentStatus==='PAID'?'Оплачено':item.paymentStatus}</span>
             <span className="rounded-md bg-blue-50 px-2 py-1 text-blue-800">{item.deliveryStatus==='DELIVERED'?'Доставлено':item.deliveryStatus==='IN_TRANSIT'?'В пути':item.fulfillmentType==='SELF_PICKUP'?'Самовывоз':item.deliveryStatus||'—'}</span>
            </div>
           </article>)}
          </div>}
        </>}
      <p className="text-xs text-slate-500">Итог продаж — оборот, а не остаток средств. Наличные и банк сверяются отдельно, с учётом других операций.</p>
    </section>}
    {valid && access.isSuccess && !exists && <section aria-label="Завершение финансового дня"
      className="space-y-4 rounded-xl border bg-white p-4">
      <h2 className="text-lg font-bold">Завершение финансового дня</h2>
      {preview.isPending ? <LoadingState label="Загружаем расчёт…"/> :
       preview.isError ? <ErrorState error={preview.error} onRetry={()=>void preview.refetch()}/> :
       <p className="text-sm text-slate-600">Система использует учётные остатки без ручной сверки.
        Если нужно исправить остаток, администратор может сделать документированную корректировку в финансах.</p>}
      {manager && !canFinancialClose && <p role="status" className="text-amber-800">
        Финансовое завершение доступно после операционного закрытия сотрудником.</p>}
      {write && canFinancialClose && <GuardedPost<ApprovedClosing> kind="daily-close" scope={date}
        allowed={write} title="Завершить финансовый день?" label="Зафиксировать отчёт"
        operationKey={()=>crypto.randomUUID()}
        meta={p=>({date,cash:p.actualCash,bank:p.actualBank})}
        post={async p=>{
          try {return await postVerifiedClosing(date,p);}
          catch(cause){
            if(cause instanceof ApiClientError && cause.code.startsWith('CLOSING_')){
              setError('Данные изменились. Обновите расчёт перед повторным подтверждением.');
              void access.refetch();void preview.refetch();
            }
            throw cause;
          }
        }} onDone={refreshed}>
        {({start,locked})=><div className="flex flex-wrap gap-2">
          <Button type="button" disabled={locked||!access.data.canEdit||!preview.isSuccess}
            onClick={()=>{
              if(!preview.data||!access.data.canEdit)return;
              const payload:ApprovedClosing={
                actualCash:String(preview.data.expectedCash),actualBank:String(preview.data.expectedBank),
                cashNote:null,bankNote:null,
                approvedExpectedCash:preview.data.expectedCash,
                approvedExpectedBank:preview.data.expectedBank,
              };
              setError('');
              start(payload,'Подтвердить финансовое закрытие '+date+
                ' по рассчитанным остаткам. Изменение остатков выполняется отдельно через документированную корректировку.');
            }}>Завершить финансовый день</Button>
          {user?.roles.includes('ADMIN')&&<Button type="button" variant="outline" asChild>
            <Link to="/finance/opening-balances">Корректировать остатки</Link>
          </Button>}
        </div>}
      </GuardedPost>}
      {error&&<p role="alert" className="text-red-700">{error}</p>}
    </section>}

  </div>;
}

/** Global company CASH/BANK is never fetched for an employee without FINANCE_READ. */
export function DailyClosingPage(){
 const {hasPermission}=useAuth();
 if(!hasPermission('FINANCE_READ'))return <EmployeeDailyClosingPage/>;
 return <CompanyDailyClosingPage/>;
}
