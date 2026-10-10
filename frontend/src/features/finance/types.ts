import type { Money } from '../sales/types';
export type { Money };
export type FinanceAccount='CASH'|'BANK';
export type PaymentMethod='CASH'|'TRANSFER';
export type PaymentChannel='CASH'|'BANK_TRANSFER'|'MBANK'|'BANK_INSTALLMENT';
export interface FinanceAccountResponse{code:FinanceAccount;balance:Money;initialized:boolean}
export interface CashFlowSummary{
 from:string;to:string;payments:Money;paymentCorrections:Money;refunds:Money;
 exchangePayments:Money;exchangeRefunds:Money;operatingExpenses:Money;
 purchasePayments:Money;totalIn:Money;totalOut:Money;netCashFlow:Money;
}
export interface PaymentResponse{
 id:string;saleId:string;amount:Money;method:PaymentMethod;status:'PAID'|'CANCELLED';
 reference:string|null;comment:string|null;paidBy:string;paidAt:string;
 cancelledBy:string|null;cancelledAt:string|null;cancellationReason:string|null;channel:PaymentChannel;
}
export interface PaymentInput{saleId:string;method:PaymentMethod;channel:PaymentChannel;reference:string|null;comment:string|null}
export type RefundMethod=PaymentMethod;
export interface RefundResponse{
 id:string;saleId:string;returnId:string|null;amount:Money;method:RefundMethod;reason:string;
 reference:string|null;comment:string|null;refundedBy:string;refundedAt:string;
}
export interface RefundInput{
 idempotencyKey:string;saleId:string;returnId:string|null;amount:string;method:RefundMethod;
 reason:string;reference:string|null;comment:string|null;
}
export type ExpenseCategory='ADVERTISING'|'RENT'|'SALARY'|'DELIVERY'|'UTILITIES'|'OTHER';
export interface ExpenseInput{
 idempotencyKey:string;category:ExpenseCategory;amount:string;
 paymentMethod:FinanceAccount;expenseDate:string;comment:string|null;
}
export interface ExpenseResponse{
 id:string;category:ExpenseCategory;amount:Money;paymentMethod:FinanceAccount;
 expenseDate:string;comment:string|null;createdBy:string;createdAt:string;
}
export interface SettlementInput{
 idempotencyKey:string;direction:'IN'|'OUT';method:PaymentMethod;amount:string;reference:string|null;
}
export interface SettlementResponse{
 id:string;exchangeId:string;direction:'IN'|'OUT';method:PaymentMethod;amount:Money;
 reference:string|null;idempotencyKey:string;createdBy:string;createdAt:string;
}
export interface TransferInput{transferId:string;from:FinanceAccount;to:FinanceAccount;amount:string}
export interface TransferResponse extends TransferInput{}
export interface OpeningInput{account:FinanceAccount;amount:string}
