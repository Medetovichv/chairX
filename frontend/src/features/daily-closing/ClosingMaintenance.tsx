import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../auth/AuthProvider';
import { closingApi, closingKeys } from './api';
import type { ClosingAccess, ClosingReport, ClosingUpdate } from './types';
import { GuardedPost } from '../finance/components/GuardedPost';
import { categories, amountCents, bishkekToday, dateLabel, nonnegativeSom, optional, som } from '../finance/validation';
import { validateExpense } from '../finance/validation';
import type { ExpenseInput, ExpenseCategory, FinanceAccount } from '../finance/types';
import { Input } from '../../shared/ui/input';
import { Button } from '../../shared/ui/button';
import { ErrorState } from '../../shared/components/ErrorState';

function editableWholeSom(money: string | number): string {
  const cents = amountCents(money);
  if (cents === null || cents < 0n || cents % 100n !== 0n) return '';
  const value = (cents / 100n).toString();
  return nonnegativeSom(value) ? value : '';
}
export function closingUpdateIssue(payload: ClosingUpdate, report: ClosingReport): string | null {
  if (!Number.isSafeInteger(payload.expectedVersion) || payload.expectedVersion !== report.version ||
      !payload.reason.trim() || payload.reason.length > 2000) {
    return 'Необходима текущая версия отчёта и причина исправления (до 2000 символов).';
  }
  if (!nonnegativeSom(payload.actualCash) || !nonnegativeSom(payload.actualBank)) {
    return 'Фактические остатки: целые неотрицательные суммы в сомах.';
  }
  if ((payload.cashNote?.length ?? 0) > 2000 || (payload.bankNote?.length ?? 0) > 2000) {
    return 'Примечание не более 2000 символов.';
  }
  for (const [name,actual,account,note] of [
    ['CASH',payload.actualCash,report.cash,payload.cashNote],
    ['BANK',payload.actualBank,report.bank,payload.bankNote],
  ] as const) {
    const current = amountCents(account.expected),original = amountCents(account.originalExpected);
    if (current === null || original === null) return 'Ожидаемая сумма не может быть проверена точно.';
    if ((BigInt(actual)*100n!==current || BigInt(actual)*100n!==original) && !note?.trim()) {
      return 'Укажите причину расхождения для ' + name + '.';
    }
  }
  return null;
}

/** These forms do not modify account balances on their own.
 * Only the dedicated backend correction expense endpoint creates an actual posting.
 */
export function ClosingMaintenance({date,access,report,onChanged}:{
  date:string;access:ClosingAccess;report:ClosingReport|undefined;onChanged:()=>Promise<void>
}) {
  const {hasPermission}=useAuth(), client=useQueryClient();
  const canWrite=hasPermission('DAILY_CLOSING_WRITE');
  const canUnlock=hasPermission('DAILY_CLOSING_UNLOCK_MANAGER') ||
    hasPermission('DAILY_CLOSING_UNLOCK_ADMIN');
  const admin=hasPermission('DAILY_CLOSING_UNLOCK_ADMIN');
  const canCorrect=hasPermission('DAILY_CLOSING_EXPENSE_CORRECT');
  const auditRead=hasPermission('DAILY_CLOSING_AUDIT_READ');
  const [unlockReason,setUnlockReason]=useState('');
  const [unlockError,setUnlockError]=useState('');
  const [reason,setReason]=useState('');
  const [cash,setCash]=useState(()=>report?editableWholeSom(report.cash.actual):'');
  const [bank,setBank]=useState(()=>report?editableWholeSom(report.bank.actual):'');
  const [cashNote,setCashNote]=useState(report?.cash.note??'');
  const [bankNote,setBankNote]=useState(report?.bank.note??'');
  const [editError,setEditError]=useState('');
  const [category,setCategory]=useState<ExpenseCategory>('OTHER');
  const [expenseAccount,setExpenseAccount]=useState<FinanceAccount>('CASH');
  const [expenseAmount,setExpenseAmount]=useState('');
  const [expenseComment,setExpenseComment]=useState('');
  const [expenseError,setExpenseError]=useState('');
  const history=useQuery({queryKey:closingKeys.history(date),
    queryFn:()=>closingApi.history(date),enabled:auditRead && access.reportExists});

  async function refreshed(){
    await Promise.all([
      onChanged(),
      client.invalidateQueries({queryKey:closingKeys.history(date)}),
      client.invalidateQueries({queryKey:['finance']}),
    ]);
  }
  const editAllowed=!!report && canWrite && access.canEdit;
  const correctionAllowed=!!report && canCorrect && access.canEdit && date<bishkekToday();
  return <div className="space-y-5">
    {canUnlock && access.canUnlock && access.accessStatus!=='TEMPORARILY_UNLOCKED' &&
      <section className="space-y-3 rounded-xl border bg-white p-4">
        <h2 className="font-bold">Временная разблокировка даты</h2>
        <p className="text-sm">Правила срока определяет backend: для менеджера — до пяти дней,
          для администратора — по разрешению. Разблокировка не отменяет текущий финансовый lock.</p>
        <GuardedPost<{reason:string}> kind="daily-unlock" scope={date}
          allowed={canUnlock} title="Разблокировать отчётную дату?"
          label="Разблокировать" operationKey={()=>crypto.randomUUID()}
          meta={()=>({date})} post={p=>closingApi.unlock(date,p.reason)} onDone={refreshed}>
          {({start,locked})=><div className="space-y-2">
            <label className="block text-sm font-semibold">Причина разблокировки
              <textarea aria-label="Причина разблокировки" className="w-full rounded-lg border p-2"
                maxLength={2000} value={unlockReason} disabled={locked}
                onChange={e=>setUnlockReason(e.target.value)}/>
            </label>
            {unlockError && <p role="alert">{unlockError}</p>}
            <Button disabled={locked || !unlockReason.trim()} onClick={()=>{
              if(!unlockReason.trim() || unlockReason.length>2000){
                setUnlockError('Укажите причину до 2000 символов.');return;
              }
              setUnlockError('');
              start({reason:unlockReason.trim()},'Дата '+date+'. Временный доступ без изменения остатков.');
            }}>Запросить разблокировку</Button>
          </div>}
        </GuardedPost>
      </section>}
    {admin && access.accessStatus==='TEMPORARILY_UNLOCKED' &&
      <GuardedPost<{id:string}> kind="daily-relock" scope={date}
        allowed={admin} title="Отозвать доступ к отчётной дате?" label="Заблокировать" destructive
        operationKey={()=>crypto.randomUUID()} meta={()=>({date})}
        post={()=>closingApi.lock(date)} onDone={refreshed}>
        {({start,locked})=><Button type="button" variant="outline" disabled={locked}
          onClick={()=>start({id:date},'Отозвать действующую разблокировку '+date+'.')}>
          Отозвать временную разблокировку
        </Button>}
      </GuardedPost>}
    {report && canWrite && <section className="space-y-3 rounded-xl border bg-white p-4">
      <h2 className="text-lg font-bold">Исправить наблюдения в закрытом отчёте</h2>
      <p className="text-sm">Версия {report.version}. Исправляется только фактический пересчёт,
        не ledger и не исходный финансовый остаток. При конфликте версий перечитайте отчёт.</p>
      <GuardedPost<ClosingUpdate> kind="daily-report-edit" scope={date}
        allowed={canWrite} title="Изменить закрытый отчёт?" label="Сохранить исправление"
        operationKey={()=>crypto.randomUUID()}
        meta={v=>({date,version:String(v.expectedVersion),cash:v.actualCash,bank:v.actualBank})}
        post={value=>closingApi.update(date,value)} onDone={refreshed}>
        {({start,locked})=><form aria-label="Исправление закрытия" className="space-y-3"
          onSubmit={e=>{
            e.preventDefault();
            if(!editAllowed){setEditError('Этот отчёт заблокирован.');return;}
            const payload:ClosingUpdate={expectedVersion:report.version,
              actualCash:cash.trim(),actualBank:bank.trim(),
              cashNote:optional(cashNote),bankNote:optional(bankNote),reason:reason.trim()};
            const issue=closingUpdateIssue(payload,report);
            if(issue){setEditError(issue);return;}
            setEditError('');
            start(payload,'Дата '+date+', версия '+report.version+'. CASH: '+som(payload.actualCash)+
              ', BANK: '+som(payload.actualBank)+'. Причина исправления будет сохранена в аудите.');
          }}>
          <div className="grid gap-3 sm:grid-cols-2">
            {([
              ['CASH',cash,setCash,cashNote,setCashNote],
              ['BANK',bank,setBank,bankNote,setBankNote],
            ] as const).map(([name,amount,setAmount,note,setNote])=>
              <div key={name} className="space-y-2">
                <label className="block text-sm font-semibold">Фактически {name}
                  <Input aria-label={'Исправленный факт '+name} maxLength={17} inputMode="numeric"
                    disabled={locked||!editAllowed} value={amount}
                    onChange={e=>setAmount(e.target.value)}/>
                </label>
                <label className="block text-sm font-semibold">Примечание {name}
                  <textarea aria-label={'Исправленное примечание '+name} className="w-full rounded-lg border p-2"
                    maxLength={2000} disabled={locked||!editAllowed} value={note}
                    onChange={e=>setNote(e.target.value)}/>
                </label>
              </div>)}
          </div>
          <label className="block text-sm font-semibold">Причина исправления *
            <textarea aria-label="Причина исправления отчёта" className="w-full rounded-lg border p-2"
              maxLength={2000} disabled={locked||!editAllowed} value={reason}
              onChange={e=>setReason(e.target.value)}/>
          </label>
          {editError&&<p role="alert" className="text-red-700">{editError}</p>}
          <Button type="submit" disabled={locked||!editAllowed||!reason.trim()||
            !nonnegativeSom(cash)||!nonnegativeSom(bank)}>Проверить изменение</Button>
        </form>}
      </GuardedPost>
    </section>}
    {report && canCorrect && date===bishkekToday() &&
      <p role="status" className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
        Поздний расход за уже закрытый текущий финансовый день запрещён backend:
        денежные остатки сегодня заблокированы. Исправление отчётных наблюдений
        остаётся отдельной операцией без денежной проводки.
      </p>}
    {report && canCorrect && <section className="space-y-3 rounded-xl border bg-white p-4">
      <h2 className="text-lg font-bold">Документально подтверждённый поздний расход</h2>
      <p className="text-xs text-slate-600">Это реальная проводка за дату {date}, а не способ скрыть
        расхождение. Backend пересчитывает все затронутые закрытые отчёты с аудитом.</p>
      <GuardedPost<ExpenseInput> kind="daily-expense-correction" scope={date}
        canReplay allowed={canCorrect} title="Провести поздний расход?"
        label="Провести расход" destructive operationKey={p=>p.idempotencyKey}
        meta={p=>({date,amount:p.amount,account:p.paymentMethod,category:p.category})}
        post={p=>closingApi.correctedExpense(date,p)} onDone={async()=>{
          setExpenseAmount('');setExpenseComment('');await refreshed();
        }}>
        {({start,locked})=><form aria-label="Расход за закрытую дату" className="space-y-3"
          onSubmit={e=>{
            e.preventDefault();
            if(!correctionAllowed){setExpenseError('Нет доступа на корректировку этой даты.');return;}
            const payload:ExpenseInput={idempotencyKey:crypto.randomUUID(),
              category,amount:expenseAmount.trim(),paymentMethod:expenseAccount,
              expenseDate:date,comment:optional(expenseComment)};
            const issue=validateExpense(payload);
            if(issue){setExpenseError(issue);return;}
            setExpenseError('');
            start(payload,'Поздний расход за '+date+': '+som(payload.amount)+' с '+expenseAccount+
              '. Проведение денег и пересчёт отчётов выполняет backend.');
          }}>
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="block text-sm font-semibold">Категория
              <select aria-label="Категория позднего расхода" className="w-full rounded-lg border p-2"
                value={category} disabled={locked||!correctionAllowed}
                onChange={e=>setCategory(e.target.value as ExpenseCategory)}>
                {categories.map(x=><option key={x.value} value={x.value}>{x.label}</option>)}
              </select>
            </label>
            <label className="block text-sm font-semibold">Счёт
              <select aria-label="Счёт позднего расхода" className="w-full rounded-lg border p-2"
                value={expenseAccount} disabled={locked||!correctionAllowed}
                onChange={e=>setExpenseAccount(e.target.value as FinanceAccount)}>
                <option value="CASH">CASH</option><option value="BANK">BANK</option>
              </select>
            </label>
          </div>
          <label className="block text-sm font-semibold">Сумма, целые сомы
            <Input aria-label="Сумма позднего расхода" inputMode="numeric" maxLength={17}
              value={expenseAmount} disabled={locked||!correctionAllowed}
              onChange={e=>setExpenseAmount(e.target.value)}/>
          </label>
          <label className="block text-sm font-semibold">Комментарий
            <textarea aria-label="Комментарий позднего расхода" className="w-full rounded-lg border p-2"
              maxLength={1000} value={expenseComment} disabled={locked||!correctionAllowed}
              onChange={e=>setExpenseComment(e.target.value)}/>
          </label>
          {expenseError&&<p role="alert" className="text-red-700">{expenseError}</p>}
          <Button type="submit" disabled={locked||!correctionAllowed||!nonnegativeSom(expenseAmount)||
            expenseAmount.trim()==='0'}>Проверить поздний расход</Button>
        </form>}
      </GuardedPost>
    </section>}
    {auditRead && access.reportExists && <section className="space-y-3 rounded-xl border bg-white p-4">
      <h2 className="text-lg font-bold">Аудит закрытия</h2>
      {history.isPending?<p>Загружаем историю…</p>:
        history.isError?<ErrorState error={history.error} onRetry={()=>void history.refetch()}/>:
        history.data.length===0?<p>Записей аудита нет.</p>:
        <div className="space-y-2">{history.data.map((x,index)=>
          <article key={x.at+x.action+index} className="rounded-lg border p-3 text-sm">
            <strong>{x.action}</strong> · {x.actor} · {dateLabel(x.at)}
          </article>)}</div>}
    </section>}
  </div>;
}
