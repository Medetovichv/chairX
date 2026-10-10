import { uuid } from './validation';
export type F09Operation='return'|'exchange'|'defect'|'writeoff'|'resolve'|'wait';
export interface PendingOperation {kind:F09Operation;key:string;entityId:string}
/** No names, contact information, reason/comment, or credentials are stored. */
const storageKey=(userId:string,kind:F09Operation)=>'chairx:f09:pending:v1:'+encodeURIComponent(userId)+':'+kind;
export type PendingRead={kind:'missing'}|{kind:'pending';data:PendingOperation}|{kind:'invalid'};
export function readPending(userId:string|undefined,kind:F09Operation):PendingRead{
 if(!userId)return {kind:'invalid'};
 try{
  const raw=sessionStorage.getItem(storageKey(userId,kind));
  if(raw===null)return {kind:'missing'};
  if(raw.length>600)return {kind:'invalid'};
  const obj:unknown=JSON.parse(raw);
  if(!obj||typeof obj!=='object')return {kind:'invalid'};
  const v=obj as Record<string,unknown>;
  if(v.kind!==kind||typeof v.key!=='string'||!uuid(v.key)||typeof v.entityId!=='string'||!uuid(v.entityId))return {kind:'invalid'};
  return {kind:'pending',data:v as unknown as PendingOperation};
 }catch{return {kind:'invalid'};}
}
export function persistPending(userId:string,kind:F09Operation,key:string,entityId:string):boolean{
 if(!uuid(key)||!uuid(entityId)||readPending(userId,kind).kind!=='missing')return false;
 try{
  const raw=JSON.stringify({kind,key,entityId});
  sessionStorage.setItem(storageKey(userId,kind),raw);
  return sessionStorage.getItem(storageKey(userId,kind))===raw;
 }catch{return false;}
}
export function clearPending(userId:string,kind:F09Operation):boolean{
 try{sessionStorage.removeItem(storageKey(userId,kind));return sessionStorage.getItem(storageKey(userId,kind))===null;}
 catch{return false;}
}
