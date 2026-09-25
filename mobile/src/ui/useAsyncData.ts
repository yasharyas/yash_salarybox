/**
 * Load-on-focus data fetching, plus reload-on-write.
 *
 * Screens here are pushed and popped rather than kept live, and the data
 * behind them changes on other screens: enrol someone and the list behind must
 * show it. Reloading on focus is the simplest thing that is always correct,
 * and these queries read a local IndexedDB, so the cost is a millisecond.
 *
 * Focus alone is correct but late: the old data is on screen until the reload
 * returns. So every mounted screen also reloads when the repository writes,
 * and the write waits for it (see src/data/changes.ts). Screens that are still
 * in the stack are already up to date by the time anyone navigates back.
 */

import { useFocusEffect } from 'expo-router';
import { useCallback, useEffect, useRef, useState } from 'react';

import { onDataChanged } from '@/src/data/changes';

export interface AsyncData<T> {
  data: T | null;
  loading: boolean;
  error: Error | null;
  reload: () => void;
}

export function useAsyncData<T>(load: () => Promise<T>, deps: unknown[] = []): AsyncData<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [nonce, setNonce] = useState(0);
  // Every load takes a ticket and only the newest ticket may write state. With
  // two triggers (focus and writes) two loads can overlap, and without this an
  // older, slower query could land last and put stale data back on screen.
  const latest = useRef(0);

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const run = useCallback(load, deps);

  const refresh = useCallback(async () => {
    const ticket = ++latest.current;
    setLoading(true);
    try {
      const value = await run();
      if (ticket !== latest.current) return;
      setData(value);
      setError(null);
    } catch (failure) {
      if (ticket === latest.current) setError(failure as Error);
    } finally {
      if (ticket === latest.current) setLoading(false);
    }
  }, [run]);

  useFocusEffect(
    useCallback(() => {
      refresh();
      return () => {
        // Losing focus abandons whatever is in flight, as before.
        latest.current += 1;
      };
    }, [refresh, nonce]),
  );

  useEffect(() => onDataChanged(refresh), [refresh]);

  return { data, loading, error, reload: () => setNonce((value) => value + 1) };
}
