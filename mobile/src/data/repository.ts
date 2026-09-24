/**
 * The application's data rules, in one place.
 *
 * Screens call these; nothing outside this file talks to IndexedDB directly.
 * The Android build splits this across three repositories, which is right when
 * each has a DAO and a coroutine scope behind it. Here it is one file because
 * splitting it would only add import statements.
 */

import { attendance, staffStore, templates, users } from './db';
import { constantTimeEquals, hashPassword, newSalt } from './crypto';
import type { AttendanceRecord, FaceTemplate, Session, Staff, User } from './types';

export const ADMIN_USERNAME = 'admin';
export const ADMIN_PASSWORD = 'admin123';
/**
 * Every staff member signs in with their employee ID and this password. A real
 * deployment would issue a one-time credential at enrolment instead.
 */
export const DEFAULT_STAFF_PASSWORD = 'staff123';

const DEMO_STAFF: [string, string][] = [
  ['EMP-001', 'Priya Sharma'],
  ['EMP-002', 'Rahul Mehta'],
  ['EMP-003', 'Aisha Khan'],
];

const DAY_MS = 86_400_000;

/* -------------------------------------------------------------------------
 * Seeding
 * ---------------------------------------------------------------------- */

let seeding: Promise<void> | null = null;

/**
 * Idempotent, and awaited by the sign-in path rather than fired and forgotten.
 *
 * The Android build shipped a bug here worth not repeating: the seed ran
 * asynchronously from Room's onCreate, so on a genuinely fresh install the
 * first sign-in raced it and rejected correct credentials. Every subsequent
 * launch worked, which is the worst kind of bug to demo.
 */
export function ensureSeeded(): Promise<void> {
  if (!seeding) {
    seeding = (async () => {
      if ((await users.count()) > 0) return;

      const now = Date.now();
      const salt = newSalt();
      await users.put({
        username: ADMIN_USERNAME,
        passwordHash: await hashPassword(ADMIN_PASSWORD, salt),
        salt,
        role: 'ADMIN',
        staffId: null,
      });

      for (let index = 0; index < DEMO_STAFF.length; index += 1) {
        const [employeeId, name] = DEMO_STAFF[index];
        const staffId = await staffStore.add({
          employeeId,
          name,
          createdAt: now - (DEMO_STAFF.length - index) * DAY_MS,
          enrolledAt: null,
        });
        await users.put(await newStaffUser(employeeId, staffId));
      }
    })();
  }
  return seeding;
}

async function newStaffUser(employeeId: string, staffId: number): Promise<User> {
  const salt = newSalt();
  return {
    username: employeeId.toUpperCase(),
    passwordHash: await hashPassword(DEFAULT_STAFF_PASSWORD, salt),
    salt,
    role: 'STAFF',
    staffId,
  };
}

/* -------------------------------------------------------------------------
 * Authentication
 * ---------------------------------------------------------------------- */

export async function signIn(username: string, password: string): Promise<Session | null> {
  await ensureSeeded();
  const user = await users.get(username.trim().toUpperCase());
  const fallback = user ?? (await users.get(username.trim().toLowerCase()));
  if (!fallback) return null;

  const candidate = await hashPassword(password, fallback.salt);
  if (!constantTimeEquals(candidate, fallback.passwordHash)) return null;

  return { username: fallback.username, role: fallback.role, staffId: fallback.staffId };
}

/* -------------------------------------------------------------------------
 * Staff
 * ---------------------------------------------------------------------- */

export interface StaffSummary extends Staff {
  templateCount: number;
  markedToday: boolean;
}

export async function listStaff(): Promise<StaffSummary[]> {
  await ensureSeeded();
  const [people, allTemplates, allAttendance] = await Promise.all([
    staffStore.all(),
    templates.all(),
    attendance.all(),
  ]);
  const since = startOfToday();

  return people
    .map((person) => ({
      ...person,
      templateCount: allTemplates.filter((template) => template.staffId === person.id).length,
      markedToday: allAttendance.some(
        (record) => record.staffId === person.id && record.markedAt >= since,
      ),
    }))
    .sort((a, b) => a.name.localeCompare(b.name));
}

export interface AddStaffResult {
  ok: boolean;
  staffId?: number;
  error?: string;
}

export async function addStaff(name: string, employeeId: string): Promise<AddStaffResult> {
  const trimmedName = name.trim();
  const trimmedId = employeeId.trim().toUpperCase();

  const nameError = validateName(trimmedName);
  if (nameError) return { ok: false, error: nameError };
  const idError = validateEmployeeId(trimmedId);
  if (idError) return { ok: false, error: idError };

  const existing = await staffStore.byEmployeeId(trimmedId);
  if (existing) {
    // Naming who holds it turns a dead end into something the admin can act on.
    return { ok: false, error: `${trimmedId} already belongs to ${existing.name}` };
  }

  const staffId = await staffStore.add({
    employeeId: trimmedId,
    name: trimmedName,
    createdAt: Date.now(),
    enrolledAt: null,
  });
  await users.put(await newStaffUser(trimmedId, staffId));
  return { ok: true, staffId };
}

export async function getStaff(id: number): Promise<Staff | undefined> {
  return staffStore.get(id);
}

export async function removeStaff(id: number): Promise<void> {
  const person = await staffStore.get(id);
  if (!person) return;
  // Attendance history is deliberately kept. It is a record of something that
  // happened, and it does not stop being true because someone left.
  for (const template of await templates.forStaff(id)) await templates.remove(template.id);
  await staffStore.remove(id);
}

/* -------------------------------------------------------------------------
 * Enrolment
 * ---------------------------------------------------------------------- */

export async function templatesFor(staffId: number): Promise<FaceTemplate[]> {
  return templates.forStaff(staffId);
}

export async function saveEnrolment(
  staffId: number,
  samples: { embedding: Float32Array; thumbnail: string }[],
): Promise<void> {
  for (const existing of await templates.forStaff(staffId)) await templates.remove(existing.id);

  const now = Date.now();
  for (const sample of samples) {
    await templates.add({
      staffId,
      embedding: Array.from(sample.embedding),
      thumbnail: sample.thumbnail,
      createdAt: now,
    });
  }

  const person = await staffStore.get(staffId);
  if (person) await staffStore.put({ ...person, enrolledAt: now });
}

/* -------------------------------------------------------------------------
 * Attendance
 * ---------------------------------------------------------------------- */

export async function historyFor(staffId: number): Promise<AttendanceRecord[]> {
  const records = await attendance.forStaff(staffId);
  return records.sort((a, b) => b.markedAt - a.markedAt);
}

export async function allHistory(): Promise<AttendanceRecord[]> {
  const records = await attendance.all();
  return records.sort((a, b) => b.markedAt - a.markedAt);
}

export async function hasMarkedToday(staffId: number): Promise<boolean> {
  const since = startOfToday();
  const records = await attendance.forStaff(staffId);
  return records.some((record) => record.markedAt >= since);
}

export async function recordAttendance(
  record: Omit<AttendanceRecord, 'id'>,
): Promise<AttendanceRecord> {
  const id = await attendance.add(record);
  return { ...record, id };
}

export function startOfToday(): number {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
}

/* -------------------------------------------------------------------------
 * Validation
 * ---------------------------------------------------------------------- */

/**
 * Letters, plus combining marks.
 *
 * \p{M} is not optional decoration: in Devanagari, Arabic, Thai and others the
 * vowel signs are separate combining characters, so a pattern of \p{L} alone
 * rejects perfectly ordinary names. A name field that only accepts A to Z is a
 * bug wearing the costume of a validation rule.
 */
const NAME_PATTERN = /^[\p{L}][\p{L}\p{M} .'-]*$/u;
const EMPLOYEE_ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9_-]{2,15}$/;

export function validateName(value: string): string | null {
  const trimmed = value.trim();
  if (trimmed.length === 0) return "Enter the staff member's name";
  if (trimmed.length < 2) return 'Name must be at least 2 characters';
  if (trimmed.length > 60) return 'Name must be 60 characters or fewer';
  if (!NAME_PATTERN.test(trimmed)) return 'Use letters, spaces, hyphens and apostrophes only';
  return null;
}

export function validateEmployeeId(value: string): string | null {
  const trimmed = value.trim();
  if (trimmed.length === 0) return 'Enter an employee ID';
  if (!EMPLOYEE_ID_PATTERN.test(trimmed)) {
    return 'Use 3 to 16 letters, numbers, hyphens or underscores';
  }
  return null;
}
