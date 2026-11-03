/** Mirrors server SaleCancellationReason; never persist arbitrary reason codes. */
export const cancellationReasons = [
  {value:'CUSTOMER_CHANGED_MIND',label:'Клиент передумал'},
  {value:'INSUFFICIENT_FUNDS',label:'Недостаточно средств'},
  {value:'PRODUCT_MISMATCH',label:'Товар не соответствует ожиданиям'},
  {value:'DUPLICATE_ORDER',label:'Дублирующий заказ'},
  {value:'OTHER',label:'Другая причина'},
] as const;

export type SaleCancellationReason = typeof cancellationReasons[number]['value'];
export type CancelSaleInput = {reason:SaleCancellationReason;comment:string|null};
export type SaleCancellationDetails = CancelSaleInput & {cancelledBy:string;cancelledAt:string};
export const cancellationLabel=(value:SaleCancellationReason)=>
  cancellationReasons.find(r=>r.value===value)?.label??'Неизвестная причина';
