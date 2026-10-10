import type { ProductInput, VariantInput } from './types';

export const palette = ['Чёрный', 'Белый', 'Серый', 'Бежевый', 'Коричневый', 'Синий', 'Красный', 'Зелёный'] as const;
export const optional = (value: string): string | null => value.trim() || null;

export function validateProduct(input: { name: string; category: string; description: string }): string | null {
  if (!input.name.trim()) return 'Укажите название модели.';
  if (input.name.trim().length > 200) return 'Название не должно превышать 200 символов.';
  if (input.category.trim().length > 120) return 'Категория не должна превышать 120 символов.';
  if (input.description.trim().length > 4000) return 'Описание не должно превышать 4000 символов.';
  return null;
}
export function productInput(input: { name: string; category: string; description: string }): ProductInput {
  return { name: input.name.trim(), category: optional(input.category), description: optional(input.description) };
}

// Strict decimal string: max 17 digits before point, max two after.
// Do not parseFloat, round, or calculate inventory/financial values in the UI.
export function validateMoney(value: string): string | null {
  const normalized = value.trim().replace(',', '.');
  if (!/^\d+(?:\.\d{1,2})?$/.test(normalized)) {
    return 'Введите неотрицательную цену с максимум двумя знаками после запятой.';
  }
  if (normalized.split('.')[0].length > 17) return 'Цена слишком велика (максимум 17 цифр до запятой).';
  return null;
}
export function validateVariant(input: { name: string; color: string; sku: string; price: string }): string | null {
  if (!input.name.trim()) return 'Укажите название вариации.';
  if (input.name.trim().length > 200) return 'Название вариации не должно превышать 200 символов.';
  if (input.color.trim().length > 100) return 'Цвет не должен превышать 100 символов.';
  if (input.sku.trim().length > 100) return 'SKU не должен превышать 100 символов.';
  return validateMoney(input.price);
}
export function variantInput(input: { name: string; color: string; sku: string; price: string }): VariantInput {
  return {
    name: input.name.trim(),
    color: optional(input.color),
    sku: optional(input.sku),
    recommendedSalePrice: input.price.trim().replace(',', '.'),
  };
}

// Display-only formatter from the original decimal representation; no rounding.
export function formatCatalogPrice(value: string | number): string {
  const raw = String(value);
  if (!/^\d+(?:\.\d+)?$/.test(raw)) return '—';
  const [integral, fraction] = raw.split('.');
  const grouped = integral.replace(/\B(?=(\d{3})+(?!\d))/g, '\u00a0');
  const decimals = fraction ? ',' + fraction.slice(0, 2) : '';
  return grouped + decimals + '\u00a0сом';
}
