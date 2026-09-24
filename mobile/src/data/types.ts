/** Mirrors the Room entities in the Android module, minus Room's annotations. */

export type UserRole = 'ADMIN' | 'STAFF';

export interface User {
  username: string;
  passwordHash: string;
  salt: string;
  role: UserRole;
  staffId: number | null;
}

export interface Staff {
  id: number;
  employeeId: string;
  name: string;
  createdAt: number;
  enrolledAt: number | null;
}

export interface FaceTemplate {
  id: number;
  staffId: number;
  /** Unit-norm embedding. Stored as a plain array: IndexedDB structured-clones
   *  typed arrays fine, but a plain array survives export and inspection. */
  embedding: number[];
  thumbnail: string;
  createdAt: number;
}

export interface AttendanceRecord {
  id: number;
  staffId: number;
  employeeId: string;
  markedAt: number;
  selfie: string;
  matchScore: number;
  /**
   * The threshold in force when this decision was made, written onto the
   * record so that changing the constant later cannot retroactively rewrite
   * what a past decision meant.
   */
  threshold: number;
  latitude: number | null;
  longitude: number | null;
  accuracy: number | null;
  address: string | null;
}

export interface Session {
  username: string;
  role: UserRole;
  staffId: number | null;
}
