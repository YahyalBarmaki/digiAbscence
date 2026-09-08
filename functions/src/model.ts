import {Timestamp} from "firebase-admin/firestore";

/** Firestore collection names — single source of truth. */
export const Collections = {
  students: "students",
  classes: "classes",
  sessions: "sessions",
  presences: "presences",
} as const;

export type PresenceStatus = "present" | "absent";
export type SessionStatus = "active" | "closed";

/** students/{studentId} */
export interface Student {
  fullName: string;
  classId: string;
  /** How to reach the guardian(s) — used by Module 6 (FCM). */
  parentContacts?: ParentContact[];
  /** Auth uid of a linked parent account, if any. */
  parentUid?: string;
}

export interface ParentContact {
  type: "fcm" | "sms" | "email";
  value: string;
  label?: string;
}

/**
 * sessions/{sessionId}
 * The document id IS the short BLE code advertised by the teacher
 * (8 hex chars, see the Android `SessionId` helper), so `confirmPresence`
 * can look the session up directly from what the student's phone scanned.
 */
export interface Session {
  classId: string;
  teacherId: string;
  label?: string;
  status: SessionStatus;
  openedAt: Timestamp;
  closedAt?: Timestamp;
  /** Optional deadline for the scheduled auto-close sweep. */
  autoCloseAt?: Timestamp;
  /** Filled in by `closeSession`. */
  counts?: {present: number; absent: number};
}

/**
 * presences/{sessionId}_{studentId}
 * Deterministic id = the client-side anti-doublon key, so a second write
 * for the same (session, student) is impossible.
 */
export interface Presence {
  sessionId: string;
  studentId: string;
  classId: string;
  status: PresenceStatus;
  /** When the student tapped "confirmer" (present only). */
  confirmedAt?: Timestamp;
  /** When this row was written server-side. */
  recordedAt: Timestamp;
  source?: "ble" | "manual";
  rssi?: number;
}

export function presenceId(sessionId: string, studentId: string): string {
  return `${sessionId}_${studentId}`;
}

/** 8 uppercase hex chars, matching the Android SessionId generator. */
export function isValidSessionId(value: unknown): value is string {
  return typeof value === "string" && /^[0-9A-F]{8}$/.test(value);
}

export function isNonEmptyString(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0;
}
