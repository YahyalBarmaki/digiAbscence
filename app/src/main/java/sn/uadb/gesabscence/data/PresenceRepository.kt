package sn.uadb.gesabscence.data

import sn.uadb.gesabscence.data.model.PresenceConfirmation

/** Outcome of a presence-confirmation attempt. */
sealed interface ConfirmResult {
    data class Success(val confirmation: PresenceConfirmation) : ConfirmResult

    /** This (session, student) pair was already confirmed on this device. */
    data object AlreadyConfirmed : ConfirmResult

    data class Failed(val reason: String) : ConfirmResult
}

/**
 * Stores presence confirmations and enforces the **client-side anti-doublon
 * rule**: at most one confirmation per (session, student). Module 5 plugs a
 * Firestore-backed implementation behind the same contract; until then
 * [LocalPresenceRepository] persists locally and keeps an outbox for later
 * sync.
 */
interface PresenceRepository {

    suspend fun confirm(studentId: String, sessionId: String, nowMs: Long): ConfirmResult

    suspend fun isConfirmed(studentId: String, sessionId: String): Boolean

    /** Confirmations not yet pushed to the backend (Module 5). */
    suspend fun pendingSync(): List<PresenceConfirmation>

    suspend fun markSynced(keys: Collection<String>)
}

/**
 * Pure de-duplication set — the heart of the anti-doublon rule, isolated so
 * it can be unit tested without Android.
 */
class PresenceDedup(initial: Collection<String> = emptyList()) {

    private val confirmed = LinkedHashSet(initial)

    fun keys(): Set<String> = confirmed.toSet()

    fun isConfirmed(key: String): Boolean = key in confirmed

    /** @return true if this key is new (accept), false if it was a duplicate. */
    fun tryAdd(key: String): Boolean = confirmed.add(key)
}
