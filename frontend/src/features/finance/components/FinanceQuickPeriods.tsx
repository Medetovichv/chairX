import {bishkekToday,validPeriod} from '../validation';
import type {FinancePeriod} from '../useFinancePeriod';
type Preset='week'|'month'|'quarter';
const iso=(d:Date)=>d.toISOString().slice(0,10);
export function periodPreset(preset:Preset):FinancePeriod {
 const today=bishkekToday();
 const [year,month,day]=today.split('-').map(Number);
 const startOfToday=new Date(Date.UTC(year!,month!-1,day!));
 const tomorrow=new Date(startOfToday);
 tomorrow.setUTCDate(tomorrow.getUTCDate()+1);
 let from:Date;
 if(preset==='week'){
  from=new Date(startOfToday);from.setUTCDate(from.getUTCDate()-6);
 }else if(preset==='month'){
  from=new Date(Date.UTC(year!,month!-1,1));
 }else{
  from=new Date(Date.UTC(year!,Math.floor((month!-1)/3)*3,1));
 }
 return {from:iso(from),to:iso(tomorrow)};
}
export function FinanceQuickPeriods({onChoose}:{onChoose:(period:FinancePeriod)=>void}){
 return <div aria-label="Быстрый выбор финансового периода" className="flex flex-wrap gap-2">
  {([['week','7 дней'],['month','Месяц'],['quarter','Квартал']] as const).map(([preset,label])=>{
   const period=periodPreset(preset);
   return <button key={preset} type="button" disabled={!validPeriod(period.from,period.to)}
    onClick={()=>onChoose(period)}
    className="min-h-11 rounded-xl border border-blue-200 bg-blue-50 px-3 text-sm font-semibold text-blue-700 hover:bg-blue-100 focus-visible:outline-2 focus-visible:outline-blue-600 disabled:opacity-50">{label}</button>;
  })}
 </div>;
}
