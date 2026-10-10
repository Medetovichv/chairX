import { Button } from '../../../shared/ui/button';

export function CatalogPager({ page, totalPages, totalElements, onPage }: {
  page: number; totalPages: number; totalElements: number; onPage: (page: number) => void;
}) {
  return (
    <nav aria-label="Страницы каталога" className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 px-4 py-4">
      <p className="text-sm text-slate-500">
        Всего: <strong className="text-slate-700">{totalElements}</strong>
        <span className="ml-2">Страница {totalPages === 0 ? 0 : page + 1} из {totalPages}</span>
      </p>
      <div className="flex gap-2">
        <Button type="button" variant="outline" disabled={page <= 0} onClick={() => onPage(page - 1)}>Назад</Button>
        <Button type="button" variant="outline" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>Вперёд</Button>
      </div>
    </nav>
  );
}
