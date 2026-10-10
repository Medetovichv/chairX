import type { Money,FinanceAccount,PaymentChannel,PaymentMethod,RefundInput,ExpenseInput,SettlementInput,TransferInput,OpeningInput } from './types';
import { bishkekToday,validLocalDate,validUUID } from '../deliveries/validation';
export {bishkekToday,validLocalDate,validUUID};
export const positiveSom=(v:string)=>/^[1-9][0-9]{0,16}$/.test(v.trim());
export const nonnegativeSom=(v:string)=>/^(0|[1-9][0-9]{0,16})$/.test(v.trim());
export const amountCents=(v:Money|null|undefined):bigint|null=>{
 if(v===null||v===undefined)return null;
 // Backend BigDecimal is normally serialized as JSON number, for which
 // arbitrary precision is not recoverable. Reject unsafe numeric ranges.
 if(typeof v==='number'&&(!Number.isFinite(v)||Math.abs(v)>Number.MAX_SAFE_INTEGER/100))return null;
 const s=String(v);
 if(!/^-?\d+(?:\.\d{1,2})?$/.test(s))return null;
 const [integer,decimal='']=s.split('.');
 const neg=integer!.startsWith('-');
 const abs=neg?integer!.slice(1):integer!;
 const cents=BigInt(abs)*100n+BigInt(decimal.padEnd(2,'0')||'0');
 return neg?-cents:cents;
};
export function som(v:Money|null|undefined):string{
 const cents=amountCents(v);
 if(cents===null)return 'Сумма недоступна для точного отображения';
 const sign=cents<0n?'-':'',abs=cents<0n?-cents:cents;
 const whole=(abs/100n).toString().replace(/\B(?=(\d{3})+(?!\d))/g,' ');
 const remainder=abs%100n;
 return sign+whole+(remainder?','+remainder.toString().padStart(2,'0'):'')+' сом';
}
export const accountFor=(channel:PaymentChannel):FinanceAccount=>channel==='CASH'?'CASH':'BANK';
export const methodFor=(channel:PaymentChannel):PaymentMethod=>channel==='CASH'?'CASH':'TRANSFER';
export const channels:ReadonlyArray<{value:PaymentChannel;label:string}>=[
 {value:'CASH',label:'Наличные'},
 {value:'BANK_TRANSFER',label:'Банковский перевод'},
 {value:'MBANK',label:'M-Bank'},
 {value:'BANK_INSTALLMENT',label:'Банковская рассрочка'},
];
export const categories:ReadonlyArray<{value:ExpenseInput['category'];label:string}>=[
 {value:'ADVERTISING',label:'Реклама'},{value:'RENT',label:'Аренда'},
 {value:'SALARY',label:'Зарплата'},{value:'DELIVERY',label:'Доставка'},
 {value:'UTILITIES',label:'Коммунальные расходы'},{value:'OTHER',label:'Прочее'},
];
export const isAccount=(v:string):v is FinanceAccount=>v==='CASH'||v==='BANK';
export const optional=(v:string)=>v.trim()||null;
export function periodMonth(date:Date=new Date()):{from:string;to:string}{
 const start=bishkekToday(date).slice(0,7)+'-01';
 const next=new Date(start+'T00:00:00Z');
 next.setUTCMonth(next.getUTCMonth()+1);
 return {from:start,to:next.toISOString().slice(0,10)};
}
export function validPeriod(from:string,to:string):boolean{
 if(!validLocalDate(from)||!validLocalDate(to)||from>=to)return false;
 const d1=new Date(from+'T00:00:00Z'),d2=new Date(to+'T00:00:00Z');
 return (d2.getTime()-d1.getTime())/86400000<=93;
}
export function validateRefund(p:RefundInput):string|null{
 if(!validUUID(p.saleId)||!validUUID(p.idempotencyKey)||(p.returnId!==null&&!validUUID(p.returnId)))return 'Некорректный ID продажи, возврата или операции.';
 if(!positiveSom(p.amount))return 'Сумма должна быть целым положительным числом сомов (максимум 17 цифр).';
 if(!['CASH','TRANSFER'].includes(p.method))return 'Недопустимый способ возврата.';
 if(!p.reason.trim()||p.reason.length>500)return 'Причина обязательна (до 500 символов).';
 if((p.reference?.length??0)>200||(p.comment?.length??0)>1000)return 'Слишком длинный номер операции или комментарий.';
 return null;
}
export function validateExpense(p:ExpenseInput):string|null{
 if(!validUUID(p.idempotencyKey)||!positiveSom(p.amount))return 'Введите положительную целую сумму сомов.';
 if(!validLocalDate(p.expenseDate))return 'Некорректная бизнес-дата расхода.';
 if((p.comment?.length??0)>1000)return 'Комментарий не более 1000 символов.';
 return null;
}
export function validateSettlement(p:SettlementInput):string|null{
 if(!validUUID(p.idempotencyKey)||!positiveSom(p.amount))return 'Сумма доплаты/возврата должна быть целыми положительными сомами.';
 if(!['IN','OUT'].includes(p.direction)||!['CASH','TRANSFER'].includes(p.method))return 'Недопустимый способ или направление расчёта.';
 if((p.reference?.length??0)>200)return 'Номер операции: максимум 200 символов.';
 return null;
}
export function validateTransfer(p:TransferInput):string|null{
 if(!validUUID(p.transferId)||!isAccount(p.from)||!isAccount(p.to)||p.from===p.to)return 'Выберите два разных финансовых счёта.';
 if(!positiveSom(p.amount))return 'Сумма перевода должна быть целыми положительными сомами.';
 return null;
}
export function validateOpening(p:OpeningInput):string|null{
 if(!isAccount(p.account)||!nonnegativeSom(p.amount))return 'Начальный остаток: целые неотрицательные сомы (до 17 цифр).';
 return null;
}
export function dateLabel(s:string):string{
 if(validLocalDate(s))return s.split('-').reverse().join('.');
 const date=new Date(s);
 return Number.isNaN(date.getTime())?'—':new Intl.DateTimeFormat('ru-RU',{timeZone:'Asia/Bishkek',day:'2-digit',month:'2-digit',year:'numeric'}).format(date);
}
