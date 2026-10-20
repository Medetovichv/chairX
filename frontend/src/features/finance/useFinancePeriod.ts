import {useSearchParams} from 'react-router';
import {periodMonth,validPeriod} from './validation';
export interface FinancePeriod {from:string;to:string}
/** Date state is kept in the URL so financial tabs share a consistent period. */
export function useFinancePeriod():readonly [FinancePeriod,(next:FinancePeriod)=>void] {
 const [params,setParams]=useSearchParams();
 const candidate={from:params.get('from')||'',to:params.get('to')||''};
 const current=validPeriod(candidate.from,candidate.to)?candidate:periodMonth();
 const update=(next:FinancePeriod)=>{
  if(!validPeriod(next.from,next.to))return;
  setParams(prev=>{
   const changed=new URLSearchParams(prev);
   changed.set('from',next.from);changed.set('to',next.to);
   return changed;
  },{replace:true});
 };
 return [current,update] as const;
}
