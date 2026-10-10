import { useAuth } from '../features/auth/AuthProvider';
import { EmptyState } from '../shared/components/EmptyState';
import { PageHeader } from '../shared/components/PageHeader';
import { Button } from '../shared/ui/button';

const sections = ['Дата отчёта', 'Итоги', 'CASH', 'BANK', 'Расхождения', 'Комментарии', 'Действия'] as const;

export function DailyClosingPage() {
  const { hasPermission } = useAuth();
  return (
    <div className="space-y-6">
      <PageHeader title="Закрытие дня"
        description="Отдельный рабочий раздел: сотруднику достаточно DAILY_CLOSING_READ без полного доступа к финансам."
        action={hasPermission('DAILY_CLOSING_WRITE')
          ? <Button disabled title="Сохранение отчёта появится в следующем пакете">Сохранить отчёт</Button>
          : undefined}
      />
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {sections.map((section) => (
          <section key={section} className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
            <h3 className="text-sm font-semibold text-slate-700">{section}</h3>
            <p className="mt-3 text-sm text-slate-400">Данные не загружены</p>
          </section>
        ))}
      </div>
      <section aria-label="Завершённые продажи">
        <h3 className="mb-3 text-lg font-semibold text-slate-900">Завершённые продажи</h3>
        <EmptyState title="Список продаж пока не загружен"
          description="Данные, фактические суммы и операции закрытия появятся после подключения backend API." />
      </section>
    </div>
  );
}
