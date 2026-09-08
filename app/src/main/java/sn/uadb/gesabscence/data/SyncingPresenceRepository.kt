package sn.uadb.gesabscence.data

import android.content.Context

/**
 * Offline-first [PresenceRepository]: every confirmation is written locally
 * (which enforces the anti-doublon rule and works with no network), then
 * best-effort pushed to the [PresenceBackend]. Unsynced confirmations sit in
 * the local outbox until [flushOutbox] gets them through.
 */
class SyncingPresenceRepository(
    private val local: LocalPresenceRepository,
    private val backend: PresenceBackend,
) : PresenceRepository {

    val backendAvailable: Boolean get() = backend.isAvailable

    override suspend fun confirm(studentId: String, sessionId: String, nowMs: Long): ConfirmResult {
        val result = local.confirm(studentId, sessionId, nowMs)
        if (result is ConfirmResult.Success && backend.isAvailable) {
            backend.confirm(result.confirmation).onSuccess {
                local.markSynced(listOf(result.confirmation.dedupKey))
            }
        }
        return result
    }

    override suspend fun isConfirmed(studentId: String, sessionId: String): Boolean =
        local.isConfirmed(studentId, sessionId)

    override suspend fun pendingSync() = local.pendingSync()

    override suspend fun markSynced(keys: Collection<String>) = local.markSynced(keys)

    /** Retry every pending confirmation against the backend. */
    suspend fun flushOutbox() {
        if (!backend.isAvailable) return
        val synced = mutableListOf<String>()
        for (confirmation in local.pendingSync()) {
            backend.confirm(confirmation).onSuccess { synced += confirmation.dedupKey }
        }
        if (synced.isNotEmpty()) local.markSynced(synced)
    }
}

object PresenceRepositoryProvider {
    fun create(context: Context): SyncingPresenceRepository =
        SyncingPresenceRepository(
            LocalPresenceRepository(context.applicationContext),
            PresenceBackendFactory.create(context.applicationContext),
        )
}
