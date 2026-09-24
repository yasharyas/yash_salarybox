/**
 * Load-on-focus data fetching.
 *
 * Screens here are pushed and popped rather than kept live, and the data
 * behind them changes on other screens: enrol someone and the list behind must
 * show it. Reloading on focus is the simplest thing that is always correct,
 * and these queries read a local IndexedDB, so the cost is a millisecond.
 */

import { useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';

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

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const run = useCallback(load, deps);

  useFocusEffect(
    useCallback(() => {
      let cancelled = false;
      setLoading(true);
      run()
        .then((value) => {
          if (cancelled) return;
          setData(value);
          setError(null);
        })
        .catch((failure: Error) => {
          if (!cancelled) setError(failure);
        })
        .finally(() => {
          if (!cancelled) setLoading(false);
        });
      return () => {
        cancelled = true;
      };
    }, [run, nonce]),
  );

  return { data, loading, error, reload: () => setNonce((value) => value + 1) };
}
