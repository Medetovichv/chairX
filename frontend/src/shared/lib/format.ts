/** Financial figures must come from the backend, never from UI-generated KPI. */
export function formatKgs(amount: number): string {
  return new Intl.NumberFormat('ru-RU', {
    style: 'currency',
    currency: 'KGS',
    maximumFractionDigits: 0,
  }).format(amount);
}

export function formatBusinessDate(date: Date): string {
  return new Intl.DateTimeFormat('ru-RU', {
    timeZone: 'Asia/Bishkek',
    day: '2-digit',
    month: 'long',
    year: 'numeric',
  }).format(date);
}

