export type ReturnCondition='SELLABLE'|'BLOCKED';
export interface ReturnItem {id:string;saleItemId:string;quantity:number;condition:ReturnCondition}
export interface ReturnCore {id:string;saleId:string;warehouseId:string;createdAt:string;reason:string;createdBy:string}
export interface ReturnSummary extends ReturnCore {saleNumber:string}
export interface ReturnResponse extends ReturnCore {items:ReturnItem[];comment:string|null}
export interface ReturnPage {items:ReturnSummary[];page:number;size:number;total:number}
export interface ReturnInput {saleId:string;warehouseId:string;idempotencyKey:string;items:{saleItemId:string;quantity:number;condition:ReturnCondition}[];reason:string;comment:string|null}
export type DefectStatus='OPEN'|'WAITING_PARTS'|'RESOLVED'|'WRITTEN_OFF';
export interface Defect {id:string;warehouseId:string;productVariantId:string;supplierId:string|null;purchaseReceiptItemId:string|null;quantity:number;status:DefectStatus;description:string;resolutionNote:string|null;createdBy:string;createdAt:string;resolvedBy:string|null;resolvedAt:string|null}
export interface DefectPage {items:Defect[];page:number;size:number;total:number}
export interface OpenDefectInput {warehouseId:string;productVariantId:string;supplierId:string|null;purchaseReceiptItemId:string|null;quantity:number;description:string}
