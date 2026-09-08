import {HttpsError, onCall} from "firebase-functions/v2/https";
import {logger} from "firebase-functions/v2";
import {FieldValue, getFirestore, Timestamp} from "firebase-admin/firestore";
import {
  Collections,
  isNonEmptyString,
  isValidSessionId,
  Presence,
  presenceId,
  Session,
} from "./model";

interface ConfirmPresenceInput {
  sessionId?: unknown;
  studentId?: unknown;
  /** Epoch millis captured on the student's device when they tapped. */
  deviceTimestamp?: unknown;
  rssi?: unknown;
}

interface ConfirmPresenceResult {
  status: "recorded" | "already";
  presenceId: string;
  sessionId: string;
  studentId: string;
}

/**
 * Validates and records one student's presence for a session.
 *
 * Rules enforced server-side:
 *  - session must exist and be `active`
 *  - if `autoCloseAt` is set and passed, the session is treated as closed
 *  - at most one presence per (session, student) — deterministic doc id +
 *    a transaction make a duplicate a no-op that returns `already`
 */
export const confirmPresence = onCall<ConfirmPresenceInput, Promise<ConfirmPresenceResult>>(
  {region: "europe-west1", enforceAppCheck: false},
  async (request) => {
    const {sessionId, studentId, deviceTimestamp, rssi} = request.data ?? {};

    if (!isValidSessionId(sessionId)) {
      throw new HttpsError("invalid-argument", "sessionId invalide (8 hex attendus).");
    }
    if (!isNonEmptyString(studentId)) {
      throw new HttpsError("invalid-argument", "studentId manquant.");
    }

    const db = getFirestore();
    const sessionRef = db.collection(Collections.sessions).doc(sessionId);
    const id = presenceId(sessionId, studentId);
    const presenceRef = db.collection(Collections.presences).doc(id);

    const confirmedAt = toTimestamp(deviceTimestamp) ?? Timestamp.now();

    const outcome = await db.runTransaction(async (tx) => {
      const sessionSnap = await tx.get(sessionRef);
      if (!sessionSnap.exists) {
        throw new HttpsError("not-found", "Session inconnue.");
      }
      const session = sessionSnap.data() as Session;

      const expired =
        session.autoCloseAt != null && session.autoCloseAt.toMillis() < Date.now();
      if (session.status !== "active" || expired) {
        throw new HttpsError("failed-precondition", "La session n'est plus active.");
      }

      const existing = await tx.get(presenceRef);
      if (existing.exists) {
        return "already" as const;
      }

      const presence: Presence = {
        sessionId,
        studentId,
        classId: session.classId,
        status: "present",
        confirmedAt,
        recordedAt: Timestamp.now(),
        source: "ble",
        ...(typeof rssi === "number" ? {rssi} : {}),
      };
      tx.set(presenceRef, {...presence, recordedAt: FieldValue.serverTimestamp()});
      return "recorded" as const;
    });

    logger.info("confirmPresence", {sessionId, studentId, outcome});
    return {status: outcome, presenceId: id, sessionId, studentId};
  },
);

function toTimestamp(value: unknown): Timestamp | null {
  if (typeof value !== "number" || !Number.isFinite(value) || value <= 0) return null;
  return Timestamp.fromMillis(value);
}
