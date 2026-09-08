import {HttpsError, onCall} from "firebase-functions/v2/https";
import {onSchedule} from "firebase-functions/v2/scheduler";
import {logger} from "firebase-functions/v2";
import {FieldValue, getFirestore, Timestamp} from "firebase-admin/firestore";
import {
  Collections,
  isNonEmptyString,
  isValidSessionId,
  Presence,
  presenceId,
  Session,
  Student,
} from "./model";

const REGION = "europe-west1";

// ---------------------------------------------------------------------------
// openSession — the teacher's app calls this when it starts advertising.
// ---------------------------------------------------------------------------

interface OpenSessionInput {
  sessionId?: unknown; // the BLE short code generated on the teacher's phone
  classId?: unknown;
  teacherId?: unknown;
  label?: unknown;
  /** Minutes until the scheduled sweep may auto-close this session. */
  autoCloseInMinutes?: unknown;
}

export const openSession = onCall<OpenSessionInput>(
  {region: REGION},
  async (request) => {
    const {sessionId, classId, teacherId, label, autoCloseInMinutes} = request.data ?? {};

    if (!isValidSessionId(sessionId)) {
      throw new HttpsError("invalid-argument", "sessionId invalide (8 hex attendus).");
    }
    if (!isNonEmptyString(classId) || !isNonEmptyString(teacherId)) {
      throw new HttpsError("invalid-argument", "classId et teacherId sont requis.");
    }

    const db = getFirestore();
    const ref = db.collection(Collections.sessions).doc(sessionId);

    const autoCloseAt =
      typeof autoCloseInMinutes === "number" && autoCloseInMinutes > 0 ?
        Timestamp.fromMillis(Date.now() + autoCloseInMinutes * 60_000) :
        undefined;

    await db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      if (snap.exists && (snap.data() as Session).status === "active") {
        // Idempotent: re-opening an already-active session is fine.
        return;
      }
      const session: Partial<Session> = {
        classId,
        teacherId,
        status: "active",
        openedAt: Timestamp.now(),
        ...(isNonEmptyString(label) ? {label} : {}),
        ...(autoCloseAt ? {autoCloseAt} : {}),
      };
      tx.set(ref, {...session, openedAt: FieldValue.serverTimestamp()}, {merge: true});
    });

    logger.info("openSession", {sessionId, classId, teacherId});
    return {sessionId, status: "active"};
  },
);

// ---------------------------------------------------------------------------
// closeSession — marks absent every rostered student who did not confirm.
// ---------------------------------------------------------------------------

interface CloseSessionInput {
  sessionId?: unknown;
}

export interface CloseSessionResult {
  sessionId: string;
  present: number;
  absent: number;
  absentStudentIds: string[];
}

export const closeSession = onCall<CloseSessionInput, Promise<CloseSessionResult>>(
  {region: REGION},
  async (request) => {
    const {sessionId} = request.data ?? {};
    if (!isValidSessionId(sessionId)) {
      throw new HttpsError("invalid-argument", "sessionId invalide.");
    }
    return closeSessionInternal(sessionId);
  },
);

/**
 * Shared close logic (used by the callable and the scheduled sweep).
 * Safe to call twice: a already-closed session just returns its stored
 * counts without rewriting anything.
 */
export async function closeSessionInternal(sessionId: string): Promise<CloseSessionResult> {
  const db = getFirestore();
  const sessionRef = db.collection(Collections.sessions).doc(sessionId);
  const sessionSnap = await sessionRef.get();
  if (!sessionSnap.exists) {
    throw new HttpsError("not-found", "Session inconnue.");
  }
  const session = sessionSnap.data() as Session;

  if (session.status === "closed") {
    const c = session.counts ?? {present: 0, absent: 0};
    return {sessionId, present: c.present, absent: c.absent, absentStudentIds: []};
  }

  // Roster for the class.
  const rosterSnap = await db
    .collection(Collections.students)
    .where("classId", "==", session.classId)
    .get();

  // Who already confirmed.
  const presentSnap = await db
    .collection(Collections.presences)
    .where("sessionId", "==", sessionId)
    .where("status", "==", "present")
    .get();
  const presentIds = new Set(presentSnap.docs.map((d) => (d.data() as Presence).studentId));

  const absentStudentIds: string[] = [];
  let batch = db.batch();
  let ops = 0;

  for (const doc of rosterSnap.docs) {
    const studentId = doc.id;
    if (presentIds.has(studentId)) continue;
    absentStudentIds.push(studentId);

    const student = doc.data() as Student;
    const absence: Presence = {
      sessionId,
      studentId,
      classId: student.classId,
      status: "absent",
      recordedAt: Timestamp.now(),
      source: "manual",
    };
    batch.set(
      db.collection(Collections.presences).doc(presenceId(sessionId, studentId)),
      {...absence, recordedAt: FieldValue.serverTimestamp()},
      {merge: true},
    );
    if (++ops === 450) {
      await batch.commit();
      batch = db.batch();
      ops = 0;
    }
  }

  const counts = {present: presentIds.size, absent: absentStudentIds.length};
  batch.set(
    sessionRef,
    {status: "closed", closedAt: FieldValue.serverTimestamp(), counts},
    {merge: true},
  );
  await batch.commit();

  logger.info("closeSession", {sessionId, ...counts});
  // Module 6 (FCM): notify the guardians of `absentStudentIds` here.
  return {sessionId, ...counts, absentStudentIds};
}

// ---------------------------------------------------------------------------
// Scheduled sweep — auto-close sessions past their autoCloseAt.
// ---------------------------------------------------------------------------

export const autoCloseExpiredSessions = onSchedule(
  {region: REGION, schedule: "every 5 minutes"},
  async () => {
    const db = getFirestore();
    const now = Timestamp.now();
    const due = await db
      .collection(Collections.sessions)
      .where("status", "==", "active")
      .where("autoCloseAt", "<=", now)
      .get();

    if (due.empty) return;
    logger.info("autoCloseExpiredSessions", {count: due.size});
    for (const doc of due.docs) {
      try {
        await closeSessionInternal(doc.id);
      } catch (err) {
        logger.error("autoClose failed", {sessionId: doc.id, err});
      }
    }
  },
);
