import { useState } from 'react';
import { EmployeeDailyClosingPage } from '../features/daily-closing/EmployeeDailyClosingPage';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../features/auth/AuthProvider';
import { closingApi, closingKeys } from '../features/daily-closing/api';
import { ApiClientError } from '../shared/api/api-error';
import { closingInputIssue, validClosingDate } from '../features/daily-closing/validation';
import type { ClosingAccount, ClosingInput, ClosingReport } from '../features/daily-closing/types';
import { postVerifiedClosing, type ApprovedClosing } from '../features/daily-closing/verified-close';
import { GuardedPost } from '../features/finance/components/GuardedPost';
import { bishkekToday, nonnegativeSom, som, dateLabel } from '../features/finance/validation';
import { ClosingMaintenance } from '../features/daily-closing/ClosingMaintenance';
import { ManagerEmployeeClosingSummary } from '../features/daily-closing/ManagerEmployeeClosingSummary';
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
    <div className="grid gap-3 sm:grid-cols-2">
      <AccountView title="CASH — наличные" account={report.cash}/>
      <AccountView title="BANK — банк" account={report.bank}/>
    </div>
    <p className="text-xs text-slate-600">Расхождение — наблюдение сотрудника, а не движение денег.
      Закрытие и перерасчёт не создают финансовых проводок.</p>
  </section>;
}
function CompanyDailyClosingPage() {
  const { hasPermission, user } = useAuth();
  const read = hasPermission('DAILY_CLOSING_READ') || hasPermission('FINANCE_READ');
  const write = hasPermission('DAILY_CLOSING_WRITE') || hasPermission('FINANCE_CLOSE');
  const client = useQueryClient();
  const [date, setDate] = useState(bishkekToday);
  const [actualCash, setActualCash] = useState('');
  const [actualBank, setActualBank] = useState('');
  const [cashNote, setCashNote] = useState('');
  const [bankNote, setBankNote] = useState('');
  const [error, setError] = useState('');
  const [showHistory,setShowHistory]=useState(false);
  const [personalMode,setPersonalMode]=useState(false);
  const valid = validClosingDate(date);

  const dates = useQuery({ queryKey: closingKeys.dates, queryFn: closingApi.dates, enabled: read });
  const access = useQuery({ queryKey: closingKeys.access(date),
    queryFn: () => closingApi.access(date), enabled: read && valid });
  const exists = access.isSuccess && access.data.reportExists;
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
    setDate(next);
    setActualCash(''); setActualBank('');
    setCashNote(''); setBankNote(''); setError('');
  }
  async function refreshed() {
    await Promise.all([
      client.invalidateQueries({ queryKey: closingKeys.dates }),
      client.invalidateQueries({ queryKey: closingKeys.access(date) }),
      client.invalidateQueries({ queryKey: closingKeys.report(date) }),
      client.invalidateQueries({ queryKey: closingKeys.preview(date) }),
      client.invalidateQueries({ queryKey: closingKeys.sales(date) }),
    ]);
  }
  return <div className="space-y-5">
    {user?.roles.some(role=>role==='ADMIN'||role==='MANAGER') && hasPermission('DAILY_CLOSING_READ') &&
      <Button type="button" variant="outline" onClick={()=>setPersonalMode(true)}>Моё личное закрытие</Button>}
    <PageHeader title="Закрытие дня"
      description="Реальные остатки CASH/BANK, завершённые продажи и фиксирование итогов без вымышленных проводок."/>
    <div className="flex gap-4 text-sm font-semibold text-slate-700" aria-label="Сверяемые счета">
      <span>CASH</span><span>BANK</span>
    </div>
    <div className="space-y-3 rounded-xl border bg-white p-4">
      <label className="block space-y-1 text-sm font-semibold">Отчётная дата (Asia/Bishkek)
        <Input type="date" aria-label="Дата закрытия" value={date}
          max={bishkekToday()} onChange={e => chooseDate(e.target.value)}/>
      </label>
      {!valid && <p role="alert" className="text-red-700">Выберите действительную дату не позднее сегодня в Бишкеке.</p>}
      <Button type="button" variant="outline" aria-expanded={showHistory}
        onClick={()=>setShowHistory(v=>!v)}>{showHistory?'Скрыть историю':'История закрытых отчётов'}</Button>
      {showHistory&&<section aria-label="История закрытий за последние 10 дней" className="space-y-3 rounded-xl bg-slate-50 p-4">
       <h2 className="font-semibold">Закрытые отчёты за последние 10 дней</h2>
       {dates.isPending?<LoadingState label="Загружаем историю…"/>:
        dates.isError?<ErrorState error={dates.error} onRetry={()=>void dates.refetch()}/>:
        <div className="flex flex-wrap gap-2">{dates.data.filter(day=>{
          const end=Date.parse(bishkekToday()+'T00:00:00Z'),current=Date.parse(day+'T00:00:00Z');
          return Number.isFinite(current)&&current<=end&&current>=end-9*86400000;
        }).sort((x,y)=>y.localeCompare(x)).map(day=><Button type="button" key={day}
          variant={day===date?'default':'outline'}
          onClick={()=>{chooseDate(day);setShowHistory(false);}}>{day}</Button>)}
        </div>}
      </section>}
    </div>
    {valid && user?.roles.some(role=>role==='ADMIN'||role==='MANAGER') && hasPermission('DAILY_CLOSING_READ') && <ManagerEmployeeClosingSummary date={date}/>}
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
    {valid && <section aria-label="Завершённые продажи" className="space-y-5 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
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
            <div className="overflow-x-auto"><table className="w-full text-left text-sm">
              <thead><tr className="border-b">
                <th className="p-2">Продажа</th><th className="p-2">Клиент</th><th className="p-2">Телефон</th>
                <th className="p-2">Товар</th><th className="p-2">Шт.</th>
                <th className="p-2">Сумма</th><th className="p-2">Оплата</th><th className="p-2">Завершено</th>
              </tr></thead><tbody>{sales.data.items.map(item => <tr key={item.saleId} className="border-b">
                <td className="p-2">{item.saleNumber}</td>
                <td className="p-2">{item.customerName ?? '—'}</td><td className="p-2">{item.phone??'—'}</td>
                <td className="p-2">{item.products ?? '—'}</td>
                <td className="p-2">{item.quantity}</td>
                <td className="p-2">{som(item.total)}</td>
                <td className="p-2">{item.paymentStatus}</td><td className="p-2">{dateLabel(item.completedAt)}</td>
              </tr>)}</tbody>
            </table></div>}
        </>}
      <p className="text-xs text-slate-500">Итог продаж — оборот, а не остаток средств. Наличные и банк сверяются отдельно, с учётом других операций.</p>
    </section>}
    {valid && access.isSuccess && !exists && <section className="space-y-4 rounded-xl border bg-white p-4">
      <h2 className="text-lg font-bold">Сверка фактических сумм и закрытие дня</h2>
      {preview.isPending ? <LoadingState label="Получаем ожидаемые остатки…"/> :
        preview.isError ? <ErrorState error={preview.error} onRetry={() => void preview.refetch()}/> :
        <div className="grid gap-3 sm:grid-cols-2">
          <p>CASH: <strong>{som(preview.data.expectedCash)}</strong></p>
          <p>BANK: <strong>{som(preview.data.expectedBank)}</strong></p>
        </div>}
      {write && <GuardedPost<ApprovedClosing> kind="daily-close" scope={date}
        allowed={write} title="Закрыть финансовый день?" label="Зафиксировать отчёт"
        operationKey={() => crypto.randomUUID()}
        meta={p => ({date, cash: p.actualCash, bank: p.actualBank})}
        post={async p => {
          try { return await postVerifiedClosing(date, p); }
          catch (cause) {
            if (cause instanceof ApiClientError &&
              cause.code.startsWith('CLOSING_')) {
              setError('Проверка перед закрытием не пройдена: данные, ожидаемые остатки или права могли измениться. Проверьте обновлённый расчёт и подтвердите заново.');
              void access.refetch();
              void preview.refetch();
            }
            throw cause;
          }
        }} onDone={refreshed}>
        {({start, locked}) => <form aria-label="Закрытие финансового дня" className="space-y-3"
          onSubmit={e => {
            e.preventDefault();
            if (!access.data.canEdit || !preview.isSuccess) {
              setError('Отчёт заблокирован или предварительный расчёт недоступен.'); return;
            }
            const payload: ClosingInput = {
              actualCash: actualCash.trim(), actualBank: actualBank.trim(),
              cashNote: cashNote.trim() || null, bankNote: bankNote.trim() || null,
            };
            const issue = closingInputIssue(payload, preview.data);
            if (issue) { setError(issue); return; }
            setError('');
            start({...payload, approvedExpectedCash: preview.data.expectedCash,
              approvedExpectedBank: preview.data.expectedBank},
              'Дата ' + date + '. CASH факт ' + som(payload.actualCash) +
              ', BANK факт ' + som(payload.actualBank) +
              '. Система запишет отчёт и неизменяемый снимок завершённых продаж. Остатки и журнал не изменятся.');
          }}>
          <div className="grid gap-4 sm:grid-cols-2">
            {([
              ['CASH', actualCash, setActualCash, cashNote, setCashNote],
              ['BANK', actualBank, setActualBank, bankNote, setBankNote],
            ] as const).map(([name,amount,setAmount,note,setNote]) =>
              <div className="space-y-2" key={name}>
                <label className="block space-y-1 text-sm font-semibold">Фактически {name}, сом
                  <Input aria-label={'Факт ' + name} inputMode="numeric" maxLength={17}
                    disabled={locked || !access.data.canEdit || !preview.isSuccess}
                    value={amount} onChange={e => setAmount(e.target.value)}/>
                </label>
                <label className="block space-y-1 text-sm font-semibold">Причина расхождения {name}
                  <textarea aria-label={'Причина ' + name} className="w-full rounded-lg border p-2"
                    maxLength={2000} disabled={locked || !access.data.canEdit || !preview.isSuccess}
                    value={note} onChange={e => setNote(e.target.value)}/>
                </label>
              </div>
            )}
          </div>
          <p className="text-xs text-slate-600">Если фактический остаток отличается от ожидаемого,
            причина обязательна. Повторное закрытие одной даты запрещено backend.</p>
          {error && <p role="alert" className="text-red-700">{error}</p>}
          <Button type="submit" disabled={locked || !access.data.canEdit || !preview.isSuccess ||
            !nonnegativeSom(actualCash) || !nonnegativeSom(actualBank)}>Проверить и закрыть</Button>
        </form>}
      </GuardedPost>}
    </section>}

  </div>;
}

/** Global company CASH/BANK is never fetched for an employee without FINANCE_READ. */
export function DailyClosingPage(){
 const {hasPermission}=useAuth();
 if(!hasPermission('FINANCE_READ'))return <EmployeeDailyClosingPage/>;
 return <CompanyDailyClosingPage/>;
}
