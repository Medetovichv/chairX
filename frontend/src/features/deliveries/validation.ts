import type { CreateDeliveryInput, DeliveryResponse, DeliveryStatus, Money } from './types';
export const deliveryStatuses: DeliveryStatus[] = ['READY','IN_TRANSIT','DELIVERED','FAILED','CANCELLED'];
export const deliveryStatusNames: Record<DeliveryStatus,string> = {
  READY:'Готова к отправке', IN_TRANSIT:'В пути',DELIVERED:'Доставлена',
  FAILED:'Не доставлена',CANCELLED:'Отменена',
};
export function bishkekToday(date: Date = new Date()): string {
  const parts = new Intl.DateTimeFormat('en-US',{
    timeZone:'Asia/Bishkek',year:'numeric',month:'2-digit',day:'2-digit',
  }).formatToParts(date);
  const find=(kind:string)=>parts.find(p=>p.type===kind)?.value??'';
  return find('year')+'-'+find('month')+'-'+find('day');
}
export function nextBishkekDay(date:Date=new Date()):string {
  const start=bishkekToday(date);
  const day=new Date(start+'T00:00:00Z');
  day.setUTCDate(day.getUTCDate()+1);
  return day.toISOString().slice(0,10);
}
export const validUUID=(x:string)=>/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(x);
export const validLocalDate=(x:string)=>/^\d{4}-\d{2}-\d{2}$/.test(x)&&
  !Number.isNaN(Date.parse(x+'T00:00:00Z')) &&
  new Date(x+'T00:00:00Z').toISOString().slice(0,10)===x;
export const validDeliveryCost=(x:string)=>/^(0|[1-9]\d{0,18})$/.test(x.trim());
export const label=(x:string | null | undefined)=>x?.trim()||'—';
export const som=(v:Money | null | undefined)=>{
  if(v===null||v===undefined)return '—';
  const s=String(v);
  if(!/^\d+(?:\.\d{1,2})?$/.test(s))return '—';
  const [whole,dec]=s.split('.');
  return whole!.replace(/\B(?=(\d{3})+(?!\d))/g,' ')+(dec?','+dec:'')+' сом';
};
export const dateLabel=(s:string|null|undefined)=>{
  if(!s)return '—';
  if(validLocalDate(s))return s.split('-').reverse().join('.');
  const value=new Date(s);
  return Number.isNaN(value.getTime())?'—':new Intl.DateTimeFormat('ru-RU',{
    day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit',
    timeZone:'Asia/Bishkek',
  }).format(value);
};
export const optional=(x:string)=>x.trim()||null;
export function validateCreate(d:CreateDeliveryInput):string|null {
  if(!validUUID(d.saleId))return 'Выберите подтверждённый заказ с доставкой.';
  if((d.recipientName?.length??0)>200)return 'Имя: максимум 200 символов.';
  if(!d.recipientPhone.trim()||d.recipientPhone.length>50)return 'Телефон обязателен, максимум 50 символов.';
  if(!d.address.trim()||d.address.length>500)return 'Адрес обязателен, максимум 500 символов.';
  if((d.cityRegion?.length??0)>200)return 'Город или регион: максимум 200 символов.';
  if(!validDeliveryCost(d.deliveryCost))return 'Стоимость доставки: целое неотрицательное число сомов (до 19 цифр).';
  if((d.carrierName?.length??0)>200||(d.trackingNumber?.length??0)>200)return 'Перевозчик и трек-номер: максимум 200 символов.';
  if((d.comment?.length??0)>2000)return 'Комментарий: максимум 2000 символов.';
  if(d.plannedDeliveryDate!==null&&!validLocalDate(d.plannedDeliveryDate))return 'Некорректная плановая дата.';
  return null;
}
export function sameDelivery(a:DeliveryResponse,b:DeliveryResponse):boolean {
  return a.id===b.id&&a.status===b.status&&a.saleId===b.saleId&&
    a.returnedToWarehouseAt===b.returnedToWarehouseAt&&
    a.plannedDeliveryDate===b.plannedDeliveryDate;
}
