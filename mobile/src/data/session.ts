/**
 * Who is signed in, and a hook to read it.
 *
 * Session is the single source of truth for which half of the app is
 * reachable. Driving navigation from it rather than from the sign-in callback
 * means signing out from anywhere lands correctly, including after a reload.
 */

import { useSyncExternalStore } from 'react';

import type { Session } from './types';

const KEY = 'salarybox.session';

/**
 * Three states, not two. Reading persisted state takes a tick, and treating
 * "still loading" as "signed out" makes a returning user watch the sign-in
 * form flash past before landing on their own screen.
 */
export type SessionState = { status: 'loading' } | { status: 'resolved'; session: Session | null };

let state: SessionState = { status: 'loading' };
const listeners = new Set<() => void>();

function emit() {
  for (const listener of listeners) listener();
}

export function hydrateSession(): void {
  if (state.status === 'resolved') return;
  let session: Session | null = null;
  try {
    const raw = globalThis.localStorage?.getItem(KEY);
    if (raw) session = JSON.parse(raw) as Session;
  } catch {
    // A corrupt or blocked store is not worth failing a launch over. Treating
    // it as signed out is both safe and recoverable by signing in again.
    session = null;
  }
  state = { status: 'resolved', session };
  emit();
}

export function setSession(session: Session | null): void {
  state = { status: 'resolved', session };
  try {
    if (session) globalThis.localStorage?.setItem(KEY, JSON.stringify(session));
    else globalThis.localStorage?.removeItem(KEY);
  } catch {
    // Private browsing can refuse writes. The in-memory session still works for
    // this tab, which is better than refusing to sign in at all.
  }
  emit();
}

export function signOut(): void {
  setSession(null);
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function snapshot(): SessionState {
  return state;
}

/** Server snapshot is always "loading": the page is pre-rendered with no user. */
const SERVER_STATE: SessionState = { status: 'loading' };

export function useSession(): SessionState {
  return useSyncExternalStore(subscribe, snapshot, () => SERVER_STATE);
}
