/**
 * PBKDF2-HMAC-SHA256 over a per-user random salt.
 *
 * Parameters are matched to the Android PasswordHasher exactly (120k
 * iterations, 256-bit key) so the two apps agree on what a stored credential
 * means. Storing a demo password in plain text would have been less code and
 * would also have been the wrong thing to show a reviewer.
 */

const ITERATIONS = 120_000;
const KEY_LENGTH_BITS = 256;
const SALT_BYTES = 16;

function subtle(): SubtleCrypto {
  const available = globalThis.crypto?.subtle;
  if (!available) {
    // WebCrypto is restricted to secure contexts. Over plain http on a phone
    // this is the first thing that breaks, and the message should say why.
    throw new Error('WebCrypto unavailable. This page must be served over https or localhost.');
  }
  return available;
}

function toBase64(bytes: Uint8Array): string {
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

function fromBase64(value: string): Uint8Array {
  const binary = atob(value);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

export function newSalt(): string {
  const bytes = new Uint8Array(SALT_BYTES);
  globalThis.crypto.getRandomValues(bytes);
  return toBase64(bytes);
}

export async function hashPassword(password: string, salt: string): Promise<string> {
  const key = await subtle().importKey(
    'raw',
    new TextEncoder().encode(password),
    'PBKDF2',
    false,
    ['deriveBits'],
  );
  const bits = await subtle().deriveBits(
    {
      name: 'PBKDF2',
      salt: fromBase64(salt) as unknown as BufferSource,
      iterations: ITERATIONS,
      hash: 'SHA-256',
    },
    key,
    KEY_LENGTH_BITS,
  );
  return toBase64(new Uint8Array(bits));
}

/**
 * Compares in time proportional to length rather than to the first difference.
 * The threat is modest for a local demo, but a password comparison that leaks
 * its prefix is the kind of detail worth getting right by habit.
 */
export function constantTimeEquals(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let difference = 0;
  for (let i = 0; i < a.length; i += 1) difference |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return difference === 0;
}
