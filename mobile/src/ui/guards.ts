/**
 * Route guards.
 *
 * These live in an effect, not in a render body. Calling router.replace while
 * rendering produces React's "cannot update a component while rendering a
 * different component" warning, and the reason it warns is real: the
 * navigation is a side effect, and React is free to render a component more
 * than once before committing it. Redirecting from render means redirecting an
 * unpredictable number of times.
 */

import { router } from 'expo-router';
import { useEffect } from 'react';

import { useSession, type SessionState } from '@/src/data/session';
import type { UserRole } from '@/src/data/types';

export function useRequireRole(role: UserRole): SessionState {
  const state = useSession();

  useEffect(() => {
    // Still reading persisted state: redirecting now would bounce a signed-in
    // user to the login screen for a frame.
    if (state.status !== 'resolved') return;
    if (!state.session) {
      router.replace('/login');
      return;
    }
    if (state.session.role !== role) router.replace('/');
  }, [state, role]);

  return state;
}
