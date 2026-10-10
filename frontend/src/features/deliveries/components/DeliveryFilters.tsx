import { Input } from '../../../shared/ui/input';
import { Button } from '../../../shared/ui/button';
import { bishkekToday, nextBishkekDay } from '../validation';
import type { DeliveryFilters as Filters, DeliveryStatus } from '../types';
type Quick='all'|'today'|'upcoming'|DeliveryStatus;
const quicks: {name:string;id:Quick}[]=[
  {name:'Все',id:'all'},{name:'Сегодня',id:'today'},{name:'Предстоящие',id:'upcoming'},
  {name:'Готовые',id:'READY'},{name:'В пути',id:'IN_TRANSIT'},
  {name:'Доставленные',id:'DELIVERED'},{name:'Не доставленные',id:'FAILED'},
  {name:'Отменённые',id:'CANCELLED'},
];
export function DeliveryFilters({value,region,onRegion,quick,onQuick,onChange}:{
  value:Filters;region:string;onRegion:(x:string)=>void;quick:Quick|null;
  onQuick:(x:Quick)=>void;onChange:(patch:Partial<Filters>)=>void;
}){
  return <section aria-label="Фильтры доставок" className="space-y-4 rounded-2xl border border-slate-200 bg-white p-4">
    <div className="flex flex-wrap gap-2" role="group" aria-label="Быстрые фильтры">
      {quicks.map(x=><Button key={x.id} type="button"
        variant={quick===x.id?'default':'outline'} aria-pressed={quick===x.id}
        onClick={()=>onQuick(x.id)}>{x.name}</Button>)}
    </div>
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
      <label className="space-y-1 text-sm font-semibold">Город или регион
        <Input type="search" aria-label="Поиск города или региона"
          maxLength={200} value={region} onChange={e=>onRegion(e.target.value)}
          placeholder="Например, Бишкек или Ош"/>
      </label>
      <label className="space-y-1 text-sm font-semibold">Создана с
        <Input type="date" aria-label="Создана с" value={value.from}
          onChange={e=>onChange({from:e.target.value})}/>
      </label>
      <label className="space-y-1 text-sm font-semibold">Создана по
        <Input type="date" aria-label="Создана по" value={value.to}
          onChange={e=>onChange({to:e.target.value})}/>
      </label>
      <label className="space-y-1 text-sm font-semibold">План с
        <Input type="date" aria-label="Плановая дата с" value={value.plannedFrom}
          onChange={e=>onChange({plannedFrom:e.target.value})}/>
      </label>
      <label className="space-y-1 text-sm font-semibold">План по
        <Input type="date" aria-label="Плановая дата по" value={value.plannedTo}
          onChange={e=>onChange({plannedTo:e.target.value})}/>
      </label>
    </div>
    <p className="text-xs text-slate-500">
      Создана с/по фильтрует дату создания; план с/по — планируемую дату.
      Сегодня в Бишкеке: {bishkekToday()}; предстоящие — с {nextBishkekDay()}.
    </p>
  </section>;
}
export type { Quick };
export function applyQuick(value:Quick): Partial<Filters> {
  if(value==='today')return {status:'',plannedFrom:bishkekToday(),plannedTo:bishkekToday()};
  if(value==='upcoming')return {status:'',plannedFrom:nextBishkekDay(),plannedTo:''};
  return {status:value==='all'?'':value,plannedFrom:'',plannedTo:''};
}
