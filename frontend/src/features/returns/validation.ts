import type { Money } from '../sales/types';
import type { ReturnInput,ExchangeInput,OpenDefectInput,DefectStatus } from './types';
export const uuid=(s:string)=>/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(s);
export const positive=(s:string)=>/^[1-9]\d*$/.test(s)&&Number.isSafeInteger(Number(s));
export const integerPrice=(s:string)=>/^(0|[1-9]\d{0,18})$/.test(s);
export const defectStatuses:DefectStatus[]=['OPEN','WAITING_PARTS','RESOLVED','WRITTEN_OFF'];
export const defectLabels:Record<DefectStatus,string>={OPEN:'Открыт',WAITING_PARTS:'Ожидает запчасти',RESOLVED:'Исправлен',WRITTEN_OFF:'Списан'};
export const conditionLabels={SELLABLE:'Исправен',BLOCKED:'Требует проверки / неисправен'} as const;
export const dateLabel=(s:string|null|undefined)=>!s?'—':new Intl.DateTimeFormat('ru-RU',{timeZone:'Asia/Bishkek',day:'2-digit',month:'2-digit',year:'numeric'}).format(new Date(s));
export const som=(value:Money)=>/^[0-9]+(?:\.[0-9]{1,2})?$/.test(String(value))?
  String(value).replace(/\B(?=(\d{3})+(?!\d))/g,' ')+' сом':'—';
export function validateReturn(x:ReturnInput):string|null{
 if(!uuid(x.saleId)||!uuid(x.warehouseId)||!uuid(x.idempotencyKey))return 'Некорректный UUID продажи, склада или операции.';
 if(!x.items.length)return 'Выберите хотя бы одну позицию продажи.';
 const seen=new Set<string>();
 for(const i of x.items){
  if(!uuid(i.saleItemId)||!Number.isSafeInteger(i.quantity)||i.quantity<1)return 'Некорректная позиция или количество возврата.';
  if(seen.has(i.saleItemId))return 'Нельзя возвращать одну позицию дважды в одной операции.';
  if(i.condition!=='SELLABLE'&&i.condition!=='BLOCKED')return 'Некорректное состояние товара.';
  seen.add(i.saleItemId);
 }
 if(!x.reason.trim()||x.reason.length>500)return 'Причина обязательна (максимум 500 символов).';
 if((x.comment?.length??0)>1000)return 'Комментарий не более 1000 символов.';
 return null;
}
export function validateExchange(x:ExchangeInput):string|null{
 if(!uuid(x.idempotencyKey)||!uuid(x.returnId))return 'Выберите существующий возврат.';
 if(!['SELF_PICKUP','CITY_DELIVERY','REGION_DELIVERY'].includes(x.fulfillmentType))return 'Выберите способ получения.';
 if(!x.items.length)return 'Добавьте товар для обмена.';
 const set=new Set<string>();
 for(const i of x.items){
  if(!uuid(i.productVariantId)||!uuid(i.warehouseId)||!Number.isSafeInteger(i.quantity)||i.quantity<1||!integerPrice(i.unitSalePrice))return 'Проверьте вариацию, склад, целое количество и цену в сомах.';
  const key=i.productVariantId+':'+i.warehouseId;
  if(set.has(key))return 'Объедините одинаковые вариации с одного склада.';
  set.add(key);
 }
 return null;
}
export function validateDefect(x:OpenDefectInput):string|null{
 if(!uuid(x.warehouseId)||!uuid(x.productVariantId))return 'Выберите действительный склад и вариацию.';
 if(x.supplierId!==null&&!uuid(x.supplierId))return 'Некорректный поставщик.';
 if(x.purchaseReceiptItemId!==null&&!uuid(x.purchaseReceiptItemId))return 'Некорректная позиция поступления.';
 if(!Number.isSafeInteger(x.quantity)||x.quantity<1)return 'Количество должно быть положительным целым.';
 if(!x.description.trim()||x.description.length>4000)return 'Описание обязательно (максимум 4000 символов).';
 return null;
}
