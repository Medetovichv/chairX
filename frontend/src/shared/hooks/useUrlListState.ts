import { useSearchParams } from 'react-router';
import type { Dispatch, SetStateAction } from 'react';
type Scalar = string | number | boolean | null;

/** Read from the URL on every render so Back/Forward restores the complete list. */
export function useUrlListState<T extends { [K in keyof T]: Scalar }>(
  defaults: T,
  validate?: (value: T) => T,
): [T, Dispatch<SetStateAction<T>>] {
  const [params, setParams] = useSearchParams();
  const decode = (source: URLSearchParams): T => {
    const result = { ...defaults };
    for (const key of Object.keys(defaults) as Array<keyof T & string>) {
      const raw = source.get(key);
      if (raw === null) continue;
      const fallback = defaults[key];
      let value: Scalar = raw.slice(0, 200);
      if (typeof fallback === 'boolean')
        value = raw === 'true' ? true : raw === 'false' ? false : fallback;
      if (typeof fallback === 'number') value = /^\d{1,7}$/.test(raw) ? Number(raw) : fallback;
      if (
        /^(from|to|plannedFrom|plannedTo|deliveredOn)$/.test(key) &&
        raw &&
        (!/^\d{4}-\d{2}-\d{2}$/.test(raw) ||
          !Number.isFinite(Date.parse(raw)) ||
          new Date(raw).toISOString().slice(0, 10) !== raw)
      )
        value = fallback;
      result[key] = value as T[typeof key];
    }
    return validate ? validate(result) : result;
  };
  const setValue: Dispatch<SetStateAction<T>> = (update) =>
    setParams((previous) => {
      const value = typeof update === 'function' ? update(decode(previous)) : update;
      const next = new URLSearchParams(previous);
      for (const key of Object.keys(defaults) as Array<keyof T & string>) {
        const field = value[key];
        if (field === defaults[key] || field === null || field === '') next.delete(key);
        else next.set(key, String(field));
      }
      return next;
    });
  return [decode(params), setValue];
}
