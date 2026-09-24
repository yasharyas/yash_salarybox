/**
 * IndexedDB, standing in for Room.
 *
 * localStorage was the obvious first thought and is the wrong tool: selfies and
 * enrolment thumbnails are data URLs, and a handful of them blows through the
 * 5 MB string quota. IndexedDB has no practical size limit here and stores
 * structured values, so embeddings survive a round trip as numbers rather than
 * as JSON.
 *
 * Everything is promise-wrapped at this boundary so no screen ever sees an
 * IDBRequest.
 */

import type { AttendanceRecord, FaceTemplate, Staff, User } from './types';

const DB_NAME = 'salarybox-attendance';
const DB_VERSION = 1;

export const STORE = {
  users: 'users',
  staff: 'staff',
  templates: 'templates',
  attendance: 'attendance',
} as const;

type StoreName = (typeof STORE)[keyof typeof STORE];

let connection: Promise<IDBDatabase> | null = null;

export function openDatabase(): Promise<IDBDatabase> {
  if (connection) return connection;
  connection = new Promise((resolve, reject) => {
    if (typeof indexedDB === 'undefined') {
      reject(new Error('IndexedDB unavailable. Private browsing can disable it.'));
      return;
    }
    const request = indexedDB.open(DB_NAME, DB_VERSION);

    request.onupgradeneeded = () => {
      const db = request.result;

      if (!db.objectStoreNames.contains(STORE.users)) {
        db.createObjectStore(STORE.users, { keyPath: 'username' });
      }
      if (!db.objectStoreNames.contains(STORE.staff)) {
        const staff = db.createObjectStore(STORE.staff, { keyPath: 'id', autoIncrement: true });
        // Unique, so a duplicate employee ID fails at the storage layer even if
        // a screen forgets to check. The add-staff form checks anyway, because
        // a good error message beats a caught exception.
        staff.createIndex('employeeId', 'employeeId', { unique: true });
      }
      if (!db.objectStoreNames.contains(STORE.templates)) {
        const templates = db.createObjectStore(STORE.templates, {
          keyPath: 'id',
          autoIncrement: true,
        });
        templates.createIndex('staffId', 'staffId', { unique: false });
      }
      if (!db.objectStoreNames.contains(STORE.attendance)) {
        const attendance = db.createObjectStore(STORE.attendance, {
          keyPath: 'id',
          autoIncrement: true,
        });
        attendance.createIndex('staffId', 'staffId', { unique: false });
        attendance.createIndex('markedAt', 'markedAt', { unique: false });
      }
    };

    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error('Could not open the database'));
  });
  return connection;
}

function promisify<T>(request: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error('Request failed'));
  });
}

export async function getAll<T>(store: StoreName): Promise<T[]> {
  const db = await openDatabase();
  return promisify(db.transaction(store, 'readonly').objectStore(store).getAll() as IDBRequest<T[]>);
}

export async function getByIndex<T>(
  store: StoreName,
  index: string,
  value: IDBValidKey,
): Promise<T[]> {
  const db = await openDatabase();
  const request = db.transaction(store, 'readonly').objectStore(store).index(index).getAll(value);
  return promisify(request as IDBRequest<T[]>);
}

export async function get<T>(store: StoreName, key: IDBValidKey): Promise<T | undefined> {
  const db = await openDatabase();
  const request = db.transaction(store, 'readonly').objectStore(store).get(key);
  return promisify(request as IDBRequest<T | undefined>);
}

export async function put<T>(store: StoreName, value: T): Promise<IDBValidKey> {
  const db = await openDatabase();
  const transaction = db.transaction(store, 'readwrite');
  const key = await promisify(transaction.objectStore(store).put(value) as IDBRequest<IDBValidKey>);
  await done(transaction);
  return key;
}

export async function add<T>(store: StoreName, value: T): Promise<number> {
  const db = await openDatabase();
  const transaction = db.transaction(store, 'readwrite');
  const key = await promisify(transaction.objectStore(store).add(value) as IDBRequest<IDBValidKey>);
  await done(transaction);
  return key as number;
}

export async function remove(store: StoreName, key: IDBValidKey): Promise<void> {
  const db = await openDatabase();
  const transaction = db.transaction(store, 'readwrite');
  await promisify(transaction.objectStore(store).delete(key));
  await done(transaction);
}

export async function count(store: StoreName): Promise<number> {
  const db = await openDatabase();
  return promisify(db.transaction(store, 'readonly').objectStore(store).count());
}

function done(transaction: IDBTransaction): Promise<void> {
  return new Promise((resolve, reject) => {
    transaction.oncomplete = () => resolve();
    transaction.onabort = () => reject(transaction.error ?? new Error('Transaction aborted'));
    transaction.onerror = () => reject(transaction.error ?? new Error('Transaction failed'));
  });
}

/** Typed convenience wrappers, so call sites do not repeat store names. */
export const users = {
  all: () => getAll<User>(STORE.users),
  get: (username: string) => get<User>(STORE.users, username),
  put: (user: User) => put(STORE.users, user),
  count: () => count(STORE.users),
};

export const staffStore = {
  all: () => getAll<Staff>(STORE.staff),
  get: (id: number) => get<Staff>(STORE.staff, id),
  byEmployeeId: (employeeId: string) =>
    getByIndex<Staff>(STORE.staff, 'employeeId', employeeId).then((rows) => rows[0]),
  add: (staff: Omit<Staff, 'id'>) => add(STORE.staff, staff),
  put: (staff: Staff) => put(STORE.staff, staff),
  remove: (id: number) => remove(STORE.staff, id),
};

export const templates = {
  forStaff: (staffId: number) => getByIndex<FaceTemplate>(STORE.templates, 'staffId', staffId),
  all: () => getAll<FaceTemplate>(STORE.templates),
  add: (template: Omit<FaceTemplate, 'id'>) => add(STORE.templates, template),
  remove: (id: number) => remove(STORE.templates, id),
};

export const attendance = {
  all: () => getAll<AttendanceRecord>(STORE.attendance),
  forStaff: (staffId: number) =>
    getByIndex<AttendanceRecord>(STORE.attendance, 'staffId', staffId),
  add: (record: Omit<AttendanceRecord, 'id'>) => add(STORE.attendance, record),
};
