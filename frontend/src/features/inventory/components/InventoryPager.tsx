import { Button } from '../../../shared/ui/button';

export function InventoryPager({ page, total, size, onPage }: {
  page: number; total: number; size: number; onPage: (page: number) => void;
}) {
  const totalPages = Math.ceil(total / size);
  return <nav aria-label="Пагинация склада" className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 p-4">
    <span className="text-sm text-slate-600">Всего: {total} · Страница {totalPages === 0 ? 0 : page + 1} из {totalPages}</span>
    <div className="flex gap-2">
      <Button variant="outline" disabled={page === 0} onClick={() => onPage(page - 1)}>Предыдущая</Button>
      <Button variant="outline" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>Следующая</Button>
    </div>
  </nav>;
}
