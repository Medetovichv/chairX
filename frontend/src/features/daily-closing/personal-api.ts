import {apiClient} from '../../shared/api/client';

export interface EmployeeSaleLine {
 saleId:string; saleNumber:string; quantity:number; total:string; goodsTotal:string; deliveryTotal:string; completedAt:string;
 products?:string|null;phone?:string|null;address?:string|null;
 fulfillment?:string|null;deliveryStatus?:string|null;paymentStatus?:string|null;
}
export interface PersonalExpenseLine {
 expenseId:string;category:string;amount:string;paymentMethod:'CASH'|'BANK';
 comment:string|null;createdAt:string;createdBy?:string|null;
}
export interface PersonalClosing {
 businessDate:string; closed:boolean; canClose:boolean;
 expectedCash:string; expectedBank:string;
 actualCash:string|null; actualBank:string|null;
 cashNote:string|null; bankNote:string|null;
 expectedTotal?:string;actualTotal?:string|null;totalNote?:string|null;
 orders:number; chairs:number; salesValue:string;
 sales:EmployeeSaleLine[]; expenses:PersonalExpenseLine[];
 expenseCash:string;expenseBank:string; collectionsChangedAfterClose:boolean;
}
export interface PersonalCloseRequest {
 expectedCash:string;expectedBank:string; actualCash:string|null;actualBank:string|null;
 cashNote:string|null;bankNote:string|null;actualTotal?:string|null;totalNote?:string|null;
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

export interface ManagerEmployeeClosing {
 employeeId:string;username:string;displayName:string;closed:boolean;
 expectedCash:string|null;expectedBank:string|null;actualCash:string|null;actualBank:string|null;
 actualTotal?:string|null;totalNote?:string|null;
}
export interface ManagerExpense {
 id:string;createdBy:string;category:string;paymentMethod:string;amount:string;comment:string|null;
}
export interface ManagerClosingReport {
 businessDate:string;employees:ManagerEmployeeClosing[];expenses:ManagerExpense[];
 expenseTotal:string;cashExpenses:string;bankExpenses:string;
 totalExpectedCash:string;totalExpectedBank:string;totalActualCash:string;totalActualBank:string;
 closedEmployees:number;totalEmployees:number;expectedTotal?:string;actualTotal?:string;
 operationalClosed:boolean;operationalCloser:string|null;
}
export const managerClosingApi={
 report:(date:string)=>apiClient.get<ManagerClosingReport>(
  '/api/finance/employee-closings/'+encodeURIComponent(date)),
};
