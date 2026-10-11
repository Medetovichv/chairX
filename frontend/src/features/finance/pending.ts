import { validUUID } from './validation';
export type PendingFinance={
 key:string;scope:string;kind:string;meta:Record<string,string>;
};
export type ReadFinance={kind:'missing'}|{kind:'invalid'}|{kind:'pending';record:PendingFinance};
const storageKey=(userId:string,kind:string,scope:string)=>
 'chairx:f10:pending:v1:'+encodeURIComponent(userId)+':'+encodeURIComponent(kind)+':'+encodeURIComponent(scope);
/** Tab-local journal: never store customer names, comments, references or credentials. */
export function readFinancePending(userId:string|undefined,kind:string,scope:string):ReadFinance{
 if(!userId||!kind||!scope)return {kind:'invalid'};
 try{
  const raw=sessionStorage.getItem(storageKey(userId,kind,scope));
  if(raw===null)return {kind:'missing'};
  if(raw.length>2048)return {kind:'invalid'};
  const value:unknown=JSON.parse(raw);
  if(!value||typeof value!=='object'||Array.isArray(value))return {kind:'invalid'};
  const p=value as Record<string,unknown>;
  if(!validUUID(String(p.key))||p.scope!==scope||p.kind!==kind||
   !p.meta||typeof p.meta!=='object'||Array.isArray(p.meta))return {kind:'invalid'};
  const meta=p.meta as Record<string,unknown>;
  if(Object.keys(meta).length>12||
     Object.values(meta).some(v=>typeof v!=='string'||v.length>80))return {kind:'invalid'};
  return {kind:'pending',record:p as unknown as PendingFinance};
 }catch{return {kind:'invalid'};}
}
export function saveFinancePending(userId:string,record:PendingFinance):boolean{
 if(!validUUID(record.key)||Object.values(record.meta).some(v=>typeof v!=='string'||v.length>80))return false;
 const old=readFinancePending(userId,record.kind,record.scope);
 if(old.kind!=='missing')return old.kind==='pending'&&JSON.stringify(old.record)===JSON.stringify(record);
 try{
  const key=storageKey(userId,record.kind,record.scope),raw=JSON.stringify(record);
  sessionStorage.setItem(key,raw);
  return sessionStorage.getItem(key)===raw;
 }catch{return false;}
}
export function clearFinancePending(userId:string,kind:string,scope:string):boolean{
 try{
  const key=storageKey(userId,kind,scope);
  sessionStorage.removeItem(key);
  return sessionStorage.getItem(key)===null;
 }catch{return false;}
}
