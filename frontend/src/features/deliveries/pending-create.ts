import type { CreateDeliveryInput } from './types';
import { validateCreate } from './validation';
const PREFIX='chairx:delivery:create-pending:v1:';
const key=(user:string)=>PREFIX+encodeURIComponent(user);
export type PendingCreate =
  |{kind:'missing'}|{kind:'pending';payload:CreateDeliveryInput}|{kind:'invalid'};
export function readPendingCreate(userId:string|undefined):PendingCreate {
  if(!userId)return {kind:'invalid'};
  try{
    const raw=sessionStorage.getItem(key(userId));
    if(raw===null)return {kind:'missing'};
    if(raw.length>8192)return {kind:'invalid'};
    const p:unknown=JSON.parse(raw);
    if(!p||typeof p!=='object'||Array.isArray(p))return {kind:'invalid'};
    const v=p as Record<string,unknown>;
    const str=(k:string)=>typeof v[k]==='string';
    const nul=(k:string)=>v[k]===null||str(k);
    if(!str('saleId')||!str('recipientPhone')||!str('address')||!str('deliveryCost')||
      !nul('recipientName')||!nul('cityRegion')||!nul('carrierName')||
      !nul('trackingNumber')||!nul('comment')||!nul('plannedDeliveryDate'))return {kind:'invalid'};
    return validateCreate(p as CreateDeliveryInput)===null?{kind:'pending',payload:p as CreateDeliveryInput}:{kind:'invalid'};
  }catch{return {kind:'invalid'};}
}
export function savePendingCreate(userId:string,payload:CreateDeliveryInput):boolean {
  if(validateCreate(payload)!==null)return false;
  const prev=readPendingCreate(userId);
  if(prev.kind==='invalid'||prev.kind==='pending')return false; // no server idempotencyKey
  try{
    const raw=JSON.stringify(payload);
    sessionStorage.setItem(key(userId),raw);
    return sessionStorage.getItem(key(userId))===raw;
  }catch{return false;}
}
export function clearPendingCreate(userId:string) {
  try{sessionStorage.removeItem(key(userId));}catch{/* fail closed after reload */}
}
