import {useState} from 'react';
import {authSession} from '../../auth/session';
import {Button} from '../../../shared/ui/button';
export function FinanceExportButton({from,to,account}:{from:string;to:string;account:string}){
 const [error,setError]=useState(''),[loading,setLoading]=useState(false);
 async function download(){
  setLoading(true);setError('');
  try{
   const query=new URLSearchParams({from,to,...(account?{account}:{})});
   const response=await authSession.fetchTracked('/api/finance/ledger/export?'+query.toString(),{
    method:'GET',headers:authSession.requiredAuthHeaders(),credentials:'same-origin',redirect:'error'
   });
   if(!response.ok)throw new Error(response.status===422?'Слишком много операций. Сократите период.':'Не удалось экспортировать журнал: '+response.status);
   const blob=await response.blob();
   const url=URL.createObjectURL(blob);
   try{
    const a=document.createElement('a');a.href=url;a.download='chairx-finance-'+from+'-'+to+'.csv';
    document.body.appendChild(a);a.click();a.remove();
   }finally{URL.revokeObjectURL(url);}
  }catch(e){setError(e instanceof Error?e.message:'Не удалось экспортировать журнал.');}
  finally{setLoading(false);}
 }
 return <div className="flex flex-col gap-1">
  <Button type="button" variant="outline" disabled={loading} onClick={()=>void download()}>
   {loading?'Экспортируем…':'Экспорт CSV'}
  </Button>
  {error&&<p role="alert" className="text-xs text-red-700">{error}</p>}
 </div>;
}
