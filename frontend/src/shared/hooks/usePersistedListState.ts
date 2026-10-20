import { useEffect, useState, type Dispatch, type SetStateAction } from 'react';

/** Persist UI-only list state per signed-in user and browser tab. Never persist records or financial data. */
export function usePersistedListState<T>(name: string, userId: string | undefined, initial: T): [T, Dispatch<SetStateAction<T>>] {
  const key = userId ? 'chairx:f16:list:v1:' + encodeURIComponent(userId) + ':' + name : null;
  const [state, setState] = useState<T>(() => {
    if (!key) return initial;
    try {
      const stored = sessionStorage.getItem(key);
      return stored !== null && stored.length < 4096 ? JSON.parse(stored) as T : initial;
    } catch { return initial; }
  });
  useEffect(() => {
    if (!key) return;
    try { sessionStorage.setItem(key, JSON.stringify(state)); }
    catch { /* storage unavailable: current in-memory state remains usable */ }
  }, [key, state]);
  return [state, setState];
}
