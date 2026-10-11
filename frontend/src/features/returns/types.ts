import type { FulfillmentType, Money } from '../sales/types';
export type ReturnCondition='SELLABLE'|'BLOCKED';
export interface ReturnItem {id:string;saleItemId:string;quantity:number;condition:ReturnCondition}
export interface ReturnSummary {id:string;saleId:string;warehouseId:string;createdAt:string;reason:string;createdBy:string;saleNumber:string}
export interface ReturnResponse extends ReturnSummary {items:ReturnItem[];comment:string|null}
export interface ReturnPage {items:ReturnSummary[];page:number;size:number;total:number}
export interface ReturnInput {saleId:string;warehouseId:string;idempotencyKey:string;items:{saleItemId:string;quantity:number;condition:ReturnCondition}[];reason:string;comment:string|null}
export interface ExchangeInput {idempotencyKey:string;returnId:string;fulfillmentType:FulfillmentType;items:{productVariantId:string;warehouseId:string;quantity:number;unitSalePrice:string}[]}
export type ExchangeStatus='PENDING_SETTLEMENT'|'COMPLETED';
export interface ExchangeResponse {id:string;originalSaleId:string;newSaleId:string;returnId:string;status:ExchangeStatus;returnedValue:Money;newSaleTotal:Money;creditApplied:Money;additionalPaymentDue:Money;refundDue:Money}
export interface ExchangeSettlement {id:string;exchangeId:string;direction:string;method:string;amount:Money;reference:string|null;idempotencyKey:string;createdBy:string;createdAt:string}
export type DefectStatus='OPEN'|'WAITING_PARTS'|'RESOLVED'|'WRITTEN_OFF';
export interface Defect {id:string;warehouseId:string;productVariantId:string;supplierId:string|null;purchaseReceiptItemId:string|null;quantity:number;status:DefectStatus;description:string;resolutionNote:string|null;createdBy:string;createdAt:string;resolvedBy:string|null;resolvedAt:string|null}
export interface DefectPage {items:Defect[];page:number;size:number;total:number}
export interface OpenDefectInput {warehouseId:string;productVariantId:string;supplierId:string|null;purchaseReceiptItemId:string|null;quantity:number;description:string}
