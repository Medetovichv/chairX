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
export const financeApi={
 accounts:():Promise<FinanceAccountResponse[]>=>apiClient.get('/api/finance/accounts'),
 flow:(from:string,to:string):Promise<CashFlowSummary>=>apiClient.get('/api/finance/cash-flow?'+period(from,to)),
 transfer:(payload:TransferInput):Promise<TransferResponse>=>apiClient.post('/api/finance/transfers',payload),
 initialize:(payload:OpeningInput):Promise<FinanceAccountResponse>=>apiClient.post('/api/finance/opening-balances',payload),
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
