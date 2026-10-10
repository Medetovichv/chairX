import type { Customer, CustomerInput } from './types';

export type CustomerFormValues = Record<keyof CustomerInput, string>;
export const blankCustomer: CustomerFormValues = {
  fullName: '', phone: '', secondaryPhone: '', whatsappPhone: '',
  instagramUsername: '', address: '', cityRegion: '', comment: '',
};

const limits: Record<keyof CustomerInput, number> = {
  fullName: 200, phone: 50, secondaryPhone: 50, whatsappPhone: 50,
  instagramUsername: 100, address: 500, cityRegion: 200, comment: 2000,
};
const labels: Record<keyof CustomerInput, string> = {
  fullName: 'Имя', phone: 'Телефон', secondaryPhone: 'Дополнительный телефон',
  whatsappPhone: 'WhatsApp', instagramUsername: 'Instagram',
  address: 'Адрес', cityRegion: 'Город / регион', comment: 'Комментарий',
};
export const customerFields = [
  'fullName', 'phone', 'secondaryPhone', 'whatsappPhone',
  'instagramUsername', 'cityRegion', 'address', 'comment',
] as const;
export const customerLimits = limits;
export const customerLabels = labels;

export function customerFormValues(initial?: Customer): CustomerFormValues {
  if (!initial) return { ...blankCustomer };
  return Object.fromEntries(customerFields.map((key) => [key, initial[key] ?? ''])) as unknown as CustomerFormValues;
}

export function validateCustomer(values: CustomerFormValues): string | null {
  for (const key of customerFields) {
    if (values[key].trim().length > limits[key]) {
      return labels[key] + ': максимум ' + limits[key] + ' символов.';
    }
  }
  if (!(['phone', 'secondaryPhone', 'whatsappPhone', 'instagramUsername'] as const)
    .some((field) => Boolean(values[field].trim()))) {
    return 'Укажите хотя бы один контакт: телефон, дополнительный телефон, WhatsApp или Instagram.';
  }
  return null;
}

export function customerInput(values: CustomerFormValues): CustomerInput {
  return Object.fromEntries(
    customerFields.map((key) => [key, values[key].trim() || null]),
  ) as CustomerInput;
}

export function customerTitle(customer: Pick<Customer, 'fullName' | 'phone' | 'secondaryPhone' | 'whatsappPhone' | 'instagramUsername'>): string {
  return customer.fullName || customer.phone || customer.secondaryPhone ||
    customer.whatsappPhone || customer.instagramUsername || 'Клиент без имени';
}

export function customerDate(value: string | null): string {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.valueOf()) ? '—' : new Intl.DateTimeFormat('ru-RU', {
    day: '2-digit', month: '2-digit', year: 'numeric', timeZone: 'Asia/Bishkek',
  }).format(date);
}

export function saleAmount(amount: string | number): string {
  const number = Number(amount);
  return Number.isFinite(number) ? new Intl.NumberFormat('ru-RU', {
    minimumFractionDigits: 0, maximumFractionDigits: 2,
  }).format(number) + ' сом' : '—';
}
