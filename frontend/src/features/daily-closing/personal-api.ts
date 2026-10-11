import {apiClient} from '../../shared/api/client';

export interface EmployeeSaleLine {
 saleId:string; saleNumber:string; quantity:number; total:string; completedAt:string;
}
export interface PersonalClosing {
 businessDate:string; closed:boolean; canClose:boolean;
 expectedCash:string; expectedBank:string;
 actualCash:string|null; actualBank:string|null;
 cashNote:string|null; bankNote:string|null;
 orders:number; chairs:number; salesValue:string;
 sales:EmployeeSaleLine[]; collectionsChangedAfterClose:boolean;
}
export interface PersonalCloseRequest {
 expectedCash:string;expectedBank:string; actualCash:string;actualBank:string;
 cashNote:string|null;bankNote:string|null;
}
export const personalCloseKeys = {
 dates: ['personal-close','dates'] as const,
 report: (date:string)=>['personal-close',date] as const,
};
export const personalCloseApi = {
 dates:()=>apiClient.get<string[]>('/api/finance/my-closings'),
 report:(date:string)=>apiClient.get<PersonalClosing>('/api/finance/my-closings/'+encodeURIComponent(date)),
 close:(date:string,input:PersonalCloseRequest)=>
  apiClient.post<PersonalClosing>('/api/finance/my-closings/'+encodeURIComponent(date),input),
};
