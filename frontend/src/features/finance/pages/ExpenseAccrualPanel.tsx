import {useState,type FormEvent} from 'react';
import {useQuery,useQueryClient} from '@tanstack/react-query';
import {financeApi} from '../api';
import {categories,positiveSom,som,validLocalDate} from '../validation';
import {Button} from '../../../shared/ui/button';
import {Input} from '../../../shared/ui/input';

export function ExpenseAccrualPanel({from,to,canCreate}:{from:string;to:string;canCreate:boolean}) {
 const client=useQueryClient();
 const key=['finance','accruals',from,to] as const;
 const query=useQuery({queryKey:key,queryFn:()=>financeApi.accruals(from,to)});
 const [category,setCategory]=useState('RENT');
 const [amount,setAmount]=useState('');
 const [description,setDescription]=useState('');
 const [start,setStart]=useState(from);
 const [end,setEnd]=useState(to);
 const [error,setError]=useState('');
 const [saving,setSaving]=useState(false);
 async function submit(event:FormEvent){
  event.preventDefault();
  if(!positiveSom(amount)||!description.trim()||!validLocalDate(start)||!validLocalDate(end)||start>=end){
   setError('Проверьте период, назначение и сумму начисления.');return;
  }
  setSaving(true);setError('');
  try {
   await financeApi.createAccrual({idempotencyKey:crypto.randomUUID(),category,
    description:description.trim(),amount,periodStart:start,periodEnd:end});
   setAmount('');setDescription('');
   await client.invalidateQueries({queryKey:key});
  } catch {
   setError('Не удалось сохранить начисление. Проверьте доступ и соединение.');
  } finally {setSaving(false);}
 }
 return <section className="space-y-4 rounded-xl border bg-white p-4" aria-label="Начисленные расходы">
  <h2 className="text-lg font-semibold">Начисленные расходы за период</h2>
  <p className="text-sm text-slate-600">Начисления распределяются по дням и не списывают деньги со счетов.
   Оплаты пока не привязаны к начислениям: поле «Оплачено» не отражает реальную сверку.</p>
  {query.isPending?<p>Загрузка начислений…</p>:query.isError?
    <p role="alert">Не удалось получить начисления.</p>:
   query.data.length===0?<p>За этот период начислений нет.</p>:
   <div className="overflow-x-auto"><table className="w-full text-left text-sm">
    <thead><tr><th>Категория</th><th>Назначение</th><th>Период</th><th>Начислено за выборку</th></tr></thead>
    <tbody>{query.data.map(({accrual,recognized})=><tr className="border-t" key={accrual.id}>
     <td className="p-2">{accrual.category}</td><td className="p-2">{accrual.description}</td>
     <td className="p-2">{accrual.periodStart} — {accrual.periodEnd} (не включая конец)</td>
     <td className="p-2">{som(recognized)}</td>
    </tr>)}</tbody>
   </table></div>}
  {canCreate&&<form onSubmit={e=>void submit(e)} className="grid gap-3 md:grid-cols-2" aria-label="Добавить начисление">
   <label>Категория<select className="mt-1 block w-full rounded border p-2" value={category} onChange={e=>setCategory(e.target.value)}>
    {categories.map(c=><option key={c.value} value={c.value}>{c.label}</option>)}
   </select></label>
   <label>Сумма, сом<Input value={amount} inputMode="numeric" onChange={e=>setAmount(e.target.value)}/></label>
   <label className="md:col-span-2">Назначение<Input maxLength={1000} value={description} onChange={e=>setDescription(e.target.value)}/></label>
   <label>Начало периода<Input type="date" value={start} onChange={e=>setStart(e.target.value)}/></label>
   <label>Конец периода (исключительно)<Input type="date" value={end} onChange={e=>setEnd(e.target.value)}/></label>
   {error&&<p role="alert" className="md:col-span-2 text-red-700">{error}</p>}
   <Button type="submit" disabled={saving||!positiveSom(amount)||!description.trim()}>Создать начисление</Button>
  </form>}
 </section>;
}
