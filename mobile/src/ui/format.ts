/** Date and text formatting, kept in one place so the copy stays consistent. */

const TIME: Intl.DateTimeFormatOptions = { hour: 'numeric', minute: '2-digit' };

export function formatTime(value: number): string {
  return new Date(value).toLocaleTimeString(undefined, TIME);
}

export function formatDateTime(value: number): string {
  return new Date(value).toLocaleString(undefined, {
    day: 'numeric',
    month: 'short',
    ...TIME,
  });
}

/**
 * "Today" and "Yesterday" rather than a date, for the two days people actually
 * think about. Anything older gets a real date, because "6 days ago" makes the
 * reader do arithmetic.
 */
export function formatDay(value: number): string {
  const date = new Date(value);
  const today = new Date();
  const startOfToday = new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime();
  const startOfThat = new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
  const days = Math.round((startOfToday - startOfThat) / 86_400_000);
  if (days === 0) return 'Today';
  if (days === 1) return 'Yesterday';
  return date.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' });
}

export function firstName(name: string): string {
  const trimmed = name.trim();
  const first = trimmed.split(' ')[0];
  return first.length > 0 ? first : trimmed;
}

export function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

export function formatPercent(value: number): string {
  return `${Math.round(value * 100)}%`;
}

export function formatCoordinates(latitude: number, longitude: number): string {
  return `${latitude.toFixed(4)}, ${longitude.toFixed(4)}`;
}
