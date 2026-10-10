import { uuid } from './validation';
export type F09Operation='return'|'exchange'|'defect'|'writeoff'|'resolve'|'wait';
export interface PendingOperation {kind:F09Operation;key:string;entityId:string}
/** No names, contact information, reason/comment, or credentials are stored. */
const storageKey=(userId:string,kind:F09Operation,scope?:string)=>
 'chairx:f09:pending:v1:'+encodeURIComponent(userId)+':'+kind+(scope?':'+encodeURIComponent(scope):'');
export type PendingRead={kind:'missing'}|{kind:'pending';data:PendingOperation}|{kind:'invalid'};
export function readPending(userId:string|undefined,kind:F09Operation,scope?:string):PendingRead{
 if(!userId||(scope!==undefined&&!uuid(scope)))return {kind:'invalid'};
 try{
  const raw=sessionStorage.getItem(storageKey(userId,kind,scope));
  if(raw===null)return {kind:'missing'};
  if(raw.length>600)return {kind:'invalid'};
  const obj:unknown=JSON.parse(raw);
  if(!obj||typeof obj!=='object')return {kind:'invalid'};
  const v=obj as Record<string,unknown>;
  if(v.kind!==kind||typeof v.key!=='string'||!uuid(v.key)||typeof v.entityId!=='string'||!uuid(v.entityId))return {kind:'invalid'};
  return {kind:'pending',data:v as unknown as PendingOperation};
 }catch{return {kind:'invalid'};}
}
export function persistPending(userId:string,kind:F09Operation,key:string,entityId:string,scope?:string):boolean{
 if(!uuid(key)||!uuid(entityId)||(scope!==undefined&&scope!==entityId)||readPending(userId,kind,scope).kind!=='missing')return false;
 try{
  const raw=JSON.stringify({kind,key,entityId});
  sessionStorage.setItem(storageKey(userId,kind,scope),raw);
  return sessionStorage.getItem(storageKey(userId,kind,scope))===raw;
 }catch{return false;}
}
export function clearPending(userId:string,kind:F09Operation,scope?:string):boolean{
 try{sessionStorage.removeItem(storageKey(userId,kind,scope));return sessionStorage.getItem(storageKey(userId,kind,scope))===null;}
 catch{return false;}
}
