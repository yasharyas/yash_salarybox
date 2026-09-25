/**
 * "Something was written": the one signal every write in the repository sends.
 *
 * Load-on-focus alone left a gap you could see. Save an enrolment, go back,
 * and the profile underneath showed its old "Face not enrolled" card until
 * its focus reload returned: only a frame or two, but long enough to catch on
 * a recording, and a wrong answer on screen is worse than a slow one.
 *
 * So a write now waits for every mounted screen to reload before it returns.
 * By the time the caller navigates back, the screen it lands on is already
 * showing the new state. The reads are local IndexedDB queries, so the wait
 * costs milliseconds, and it only happens on writes, which a person makes
 * a handful of times a day.
 */

type Listener = () => Promise<void>;

const listeners = new Set<Listener>();

/** Subscribe to writes. Returns the unsubscribe function, effect style. */
export function onDataChanged(listener: Listener): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** Called by the repository after a write commits. Resolves once screens have reloaded. */
export async function dataChanged(): Promise<void> {
  // allSettled: one screen failing to reload must not fail the write that
  // already happened, or the caller would report a save that succeeded as lost.
  await Promise.allSettled([...listeners].map((listener) => listener()));
}
