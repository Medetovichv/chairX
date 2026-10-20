import { apiClient } from '../../shared/api/client';
import type {
 FinanceAccountResponse,CashFlowSummary,PaymentResponse,PaymentInput,RefundInput,RefundResponse,
 ExpenseInput,ExpenseResponse,SettlementInput,SettlementResponse,TransferInput,TransferResponse,OpeningInput
} from './types';
const enc=(v:string)=>encodeURIComponent(v);
const period=(from:string,to:string)=>new URLSearchParams({from,to}).toString();
export const financeKeys={
 accounts:['finance','accounts'] as const,
 flow:['finance','cash-flow'] as const,
 flowPeriod:(from:string,to:string)=>['finance','cash-flow',from,to] as const,
 expenses:['finance','expenses'] as const,
 expenseList:(from:string,to:string)=>['finance','expenses',from,to] as const,
 expenseTotal:(from:string,to:string)=>['finance','expenses','total',from,to] as const,
 payments:(saleId:string)=>['finance','payments',saleId] as const,
 activePayment:(saleId:string)=>['finance','payments',saleId,'active'] as const,
 refunds:(saleId:string)=>['finance','refunds',saleId] as const,
};
export interface ExpenseAccrual {
 id:string;idempotencyKey:string;category:string;description:string;amount:string|number;
 periodStart:string;periodEnd:string;createdBy:string;
}
export interface ExpenseAccrualLine {
 accrual:ExpenseAccrual;recognized:string|number;paid:string|number;
 outstanding:string|number;prepaid:string|number;
}
export const financeApi={
 accruals:(from:string,to:string):Promise<ExpenseAccrualLine[]>=>apiClient.get('/api/expenses/accruals?'+period(from,to)),
 linkAccrualPayment:(accrualId:string,expenseId:string):Promise<void>=>apiClient.post('/api/expenses/accruals/'+enc(accrualId)+'/payments',{expenseId}),
 createAccrual:(input:{idempotencyKey:string;category:string;description:string;amount:string;periodStart:string;periodEnd:string}):Promise<ExpenseAccrual>=>apiClient.post('/api/expenses/accruals',input),
 accounts:():Promise<FinanceAccountResponse[]>=>apiClient.get('/api/finance/accounts'),
 flow:(from:string,to:string):Promise<CashFlowSummary>=>apiClient.get('/api/finance/cash-flow?'+period(from,to)),
 transfer:(payload:TransferInput):Promise<TransferResponse>=>apiClient.post('/api/finance/transfers',payload),
 initialize:(payload:OpeningInput):Promise<FinanceAccountResponse>=>apiClient.post('/api/finance/opening-balances',payload),
 adjustBalance:(payload:{account:'CASH'|'BANK';targetBalance:string;reason:string;idempotencyKey:string}):Promise<{account:string;previousBalance:string|number;newBalance:string|number;delta:string|number}>=>apiClient.post('/api/finance/accounts/adjustments',payload),
 expenses:(from:string,to:string):Promise<ExpenseResponse[]>=>apiClient.get('/api/expenses?'+period(from,to)),
 expenseTotal:(from:string,to:string):Promise<string|number>=>apiClient.get('/api/expenses/total?'+period(from,to)),
 expense:(id:string):Promise<ExpenseResponse>=>apiClient.get('/api/expenses/'+enc(id)),
 createExpense:(payload:ExpenseInput):Promise<ExpenseResponse>=>apiClient.post('/api/expenses',payload),
 payments:(saleId:string):Promise<PaymentResponse[]>=>apiClient.get('/api/payments/sale/'+enc(saleId)),
 payment:(id:string):Promise<PaymentResponse>=>apiClient.get('/api/payments/'+enc(id)),
 activePayment:(saleId:string):Promise<PaymentResponse>=>apiClient.get('/api/payments/sale/'+enc(saleId)+'/active'),
 postPayment:(payload:PaymentInput):Promise<PaymentResponse>=>apiClient.post('/api/payments',payload),
 cancelPayment:(id:string,reason:string):Promise<PaymentResponse>=>apiClient.post('/api/payments/'+enc(id)+'/cancel',{reason}),
 refunds:(saleId:string):Promise<RefundResponse[]>=>apiClient.get('/api/refunds/sale/'+enc(saleId)),
 refund:(id:string):Promise<RefundResponse>=>apiClient.get('/api/refunds/'+enc(id)),
 createRefund:(payload:RefundInput):Promise<RefundResponse>=>apiClient.post('/api/refunds',payload),
 settle:(id:string,payload:SettlementInput):Promise<SettlementResponse>=>apiClient.post('/api/exchanges/'+enc(id)+'/settlements',payload),
};
