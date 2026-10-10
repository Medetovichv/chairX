import { validLocalDate, validUUID } from './validation';

export type DeliveryAction = 'dispatch'|'deliver'|'fail'|'cancel'|'return';
export type DeliveryIntent = { action: DeliveryAction; reason?: string; warehouseId?: string };
export type PendingDeliveryMutation =
  | {kind:'action'; intent:DeliveryIntent}
  | {kind:'date'; plannedDate:string|null};

export type PendingDeliveryRead =
  | {kind:'missing'}
  | {kind:'pending';mutation:PendingDeliveryMutation}
  | {kind:'invalid'};

const PREFIX='chairx:delivery:mutation-pending:v1:';
const key=(userId:string,deliveryId:string)=>
  PREFIX+encodeURIComponent(userId)+':'+encodeURIComponent(deliveryId);

function validMutation(value:unknown):value is PendingDeliveryMutation{
  if(!value||typeof value!=='object'||Array.isArray(value))return false;
  const d=value as Record<string,unknown>;
  if(d.kind==='date')return d.plannedDate===null||
    typeof d.plannedDate==='string'&&validLocalDate(d.plannedDate);
  if(d.kind!=='action'||!d.intent||typeof d.intent!=='object'||Array.isArray(d.intent))return false;
  const i=d.intent as Record<string,unknown>;
  if(!['dispatch','deliver','fail','cancel','return'].includes(String(i.action)))return false;
  if(i.action==='fail')return typeof i.reason==='string'&&
    i.reason.trim().length>0&&i.reason.length<=1000;
  if(i.action==='return')return typeof i.warehouseId==='string'&&validUUID(i.warehouseId);
  return i.reason===undefined&&i.warehouseId===undefined;
}

/**
 * Stored only in current tab, scoped to the authenticated user/delivery.
 * Backend is the source of truth; this journal never authorizes a replay.
 */
export function readPendingDelivery(userId:string|undefined,deliveryId:string):PendingDeliveryRead{
  if(!userId||!validUUID(deliveryId))return {kind:'invalid'};
  try{
    const raw=sessionStorage.getItem(key(userId,deliveryId));
    if(raw===null)return {kind:'missing'};
    if(raw.length>4096)return {kind:'invalid'};
    const parsed:unknown=JSON.parse(raw);
    return validMutation(parsed)?{kind:'pending',mutation:parsed}:{kind:'invalid'};
  }catch{return {kind:'invalid'};}
}

/** Fail closed when storage is unavailable, invalid, or already occupied. */
export function savePendingDelivery(userId:string,deliveryId:string,mutation:PendingDeliveryMutation):boolean{
  if(!validMutation(mutation))return false;
  if(readPendingDelivery(userId,deliveryId).kind!=='missing')return false;
  try{
    const raw=JSON.stringify(mutation);
    sessionStorage.setItem(key(userId,deliveryId),raw);
    return sessionStorage.getItem(key(userId,deliveryId))===raw;
  }catch{return false;}
}
export function clearPendingDelivery(userId:string,deliveryId:string):boolean{
  try{
    sessionStorage.removeItem(key(userId,deliveryId));
    return sessionStorage.getItem(key(userId,deliveryId))===null;
  }catch{return false;}
}
