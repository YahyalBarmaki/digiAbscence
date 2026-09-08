package sn.uadb.gesabscence.data.model

/**
 * One student's confirmation of presence for a session.
 *
 * No-arg defaults are kept so Firestore can deserialize it directly in
 * Module 5. [dedupKey] is the client-side uniqueness key: one confirmation
 * per (session, student).
 */
data class PresenceConfirmation(
    val studentId: String = "",
    val sessionId: String = "",
    val timestampMillis: Long = 0L,
    val synced: Boolean = false,
) {
    val dedupKey: String get() = key(studentId, sessionId)

    companion object {
        fun key(studentId: String, sessionId: String): String = "$sessionId::$studentId"
    }
}
