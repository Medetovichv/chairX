import { Input } from '../../../shared/ui/input';

export function CustomerSearch({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  return <label className="block space-y-2 text-sm font-semibold text-slate-700">
    Поиск клиентов
    <Input type="search" maxLength={200} aria-label="Поиск клиентов"
      value={value} onChange={(event) => onChange(event.target.value)}
      placeholder="Имя, телефон, WhatsApp или Instagram" autoComplete="off" />
  </label>;
}
