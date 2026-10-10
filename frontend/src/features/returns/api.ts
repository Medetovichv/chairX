import { apiClient } from '../../shared/api/client';
import type { ReturnPage,ReturnResponse,ReturnInput,ExchangeInput,ExchangeResponse,ExchangeSettlement,Defect,DefectPage,DefectStatus,OpenDefectInput } from './types';
const enc=(id:string)=>encodeURIComponent(id);
export const F09_PAGE_SIZE=20;
export const returnKeys={lists:['returns','list'] as const,list:(saleId:string,page:number)=>['returns','list',saleId,page] as const,
  detail:(id:string)=>['returns','detail',id] as const,history:(saleId:string)=>['returns','history',saleId] as const,
  exchange:(id:string)=>['exchanges','detail',id] as const,settlements:(id:string)=>['exchanges','settlements',id] as const,
  defects:['defects','list'] as const,defectList:(status:DefectStatus|'',page:number)=>['defects','list',status,page] as const,
  defect:(id:string)=>['defects','detail',id] as const};
export const returnsApi={
  list:(saleId:string,page:number):Promise<ReturnPage>=>apiClient.get('/api/returns?'+new URLSearchParams({
    page:String(page),size:String(F09_PAGE_SIZE),...(saleId?{saleId}:{}),
  })),
  get:(id:string):Promise<ReturnResponse>=>apiClient.get('/api/returns/'+enc(id)),
  create:(input:ReturnInput):Promise<ReturnResponse>=>apiClient.post('/api/returns',input),
  // The entire sale history is needed for the advisory remaining-quantity UI.
  // Fail closed if the server returns inconsistent pagination or an excessive dataset.
  history:async(saleId:string):Promise<ReturnResponse[]>=>{
    const first=await returnsApi.list(saleId,0);
    if(first.total>1000||first.size!==20)throw Error('История слишком велика для предварительной проверки. Обратитесь к администратору.');
    const pages=Math.ceil(first.total/20),summaries=[...first.items];
    for(let page=1;page<pages;page++){
      const next=await returnsApi.list(saleId,page);
      if(next.page!==page||next.total!==first.total)throw Error('История возвратов изменилась. Обновите страницу.');
      summaries.push(...next.items);
    }
    if(summaries.length!==first.total)throw Error('История возвратов загружена не полностью.');
    return Promise.all(summaries.map(s=>returnsApi.get(s.id)));
  },
};
export const exchangesApi={
  create:(input:ExchangeInput):Promise<ExchangeResponse>=>apiClient.post('/api/exchanges',input),
  get:(id:string):Promise<ExchangeResponse>=>apiClient.get('/api/exchanges/'+enc(id)),
  settlements:(id:string):Promise<ExchangeSettlement[]>=>apiClient.get('/api/exchanges/'+enc(id)+'/settlements'),
};
export const defectsApi={
  list:(status:DefectStatus|'',page:number):Promise<DefectPage>=>apiClient.get('/api/defects?'+new URLSearchParams({
    page:String(page),size:String(F09_PAGE_SIZE),...(status?{status}:{}),
  })),
  get:(id:string):Promise<Defect>=>apiClient.get('/api/defects/'+enc(id)),
  open:(input:OpenDefectInput):Promise<Defect>=>apiClient.post('/api/defects',input),
  wait:(id:string):Promise<Defect>=>apiClient.post('/api/defects/'+enc(id)+'/wait-for-parts'),
  resolve:(id:string,note:string):Promise<Defect>=>apiClient.post('/api/defects/'+enc(id)+'/resolve',{resolutionNote:note}),
  writeOff:(id:string,note:string):Promise<Defect>=>apiClient.post('/api/defects/'+enc(id)+'/write-off',{resolutionNote:note}),
};
