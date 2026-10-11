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


/**
 * Display a readable sale number, preserving the exact server number for
 * routes, tooltips and search. Never derive a document number from UUID.
 */
export function displaySaleNumber(serverNumber: string): string {
  const match = /^SALE-[0-9]{4}-0*([0-9]+)$/.exec(serverNumber);
  return match ? '#' + match[1] : serverNumber;
}
