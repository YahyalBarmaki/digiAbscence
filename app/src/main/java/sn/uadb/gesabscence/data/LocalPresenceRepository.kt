package sn.uadb.gesabscence.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import sn.uadb.gesabscence.data.model.PresenceConfirmation

private val Context.presenceStore: DataStore<Preferences> by preferencesDataStore(name = "presence_store")

/**
 * DataStore-backed [PresenceRepository]. The confirmed-keys set makes the
 * anti-doublon rule survive process death (button stays disabled after a
 * relaunch); the outbox holds full confirmations for Module 5 to upload.
 */
class LocalPresenceRepository(private val context: Context) : PresenceRepository {

    override suspend fun confirm(studentId: String, sessionId: String, nowMs: Long): ConfirmResult {
        if (studentId.isBlank() || sessionId.isBlank()) {
            return ConfirmResult.Failed("Identité élève ou session manquante.")
        }
        val key = PresenceConfirmation.key(studentId, sessionId)
        val confirmation = PresenceConfirmation(
            studentId = studentId,
            sessionId = sessionId,
            timestampMillis = nowMs,
            synced = false,
        )

        var result: ConfirmResult = ConfirmResult.Success(confirmation)
        // Single atomic transaction: check-and-add so a double tap can't
        // slip two records past the guard.
        context.presenceStore.edit { prefs ->
            val keys = prefs[KEY_CONFIRMED] ?: emptySet()
            if (key in keys) {
                result = ConfirmResult.AlreadyConfirmed
                return@edit
            }
            prefs[KEY_CONFIRMED] = keys + key
            prefs[KEY_OUTBOX] = (prefs[KEY_OUTBOX].orEmpty()) + encode(confirmation) + "\n"
        }
        return result
    }

    override suspend fun isConfirmed(studentId: String, sessionId: String): Boolean {
        val key = PresenceConfirmation.key(studentId, sessionId)
        val keys = context.presenceStore.data.first()[KEY_CONFIRMED] ?: emptySet()
        return key in keys
    }

    override suspend fun pendingSync(): List<PresenceConfirmation> =
        readOutbox().filterNot { it.synced }

    override suspend fun markSynced(keys: Collection<String>) {
        val target = keys.toSet()
        context.presenceStore.edit { prefs ->
            val updated = readOutboxFrom(prefs[KEY_OUTBOX].orEmpty()).map {
                if (it.dedupKey in target) it.copy(synced = true) else it
            }
            prefs[KEY_OUTBOX] = updated.joinToString("") { encode(it) + "\n" }
        }
    }

    private suspend fun readOutbox(): List<PresenceConfirmation> =
        readOutboxFrom(context.presenceStore.data.first()[KEY_OUTBOX].orEmpty())

    private fun readOutboxFrom(raw: String): List<PresenceConfirmation> =
        raw.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull(::decode)
            .toList()

    // --- line format: studentId \t sessionId \t timestamp \t synced ---

    private fun encode(c: PresenceConfirmation): String =
        listOf(c.studentId, c.sessionId, c.timestampMillis.toString(), c.synced.toString())
            .joinToString("\t")

    private fun decode(line: String): PresenceConfirmation? {
        val p = line.split("\t")
        if (p.size != 4) return null
        val ts = p[2].toLongOrNull() ?: return null
        return PresenceConfirmation(
            studentId = p[0],
            sessionId = p[1],
            timestampMillis = ts,
            synced = p[3].toBooleanStrictOrNull() ?: false,
        )
    }

    companion object {
        private val KEY_CONFIRMED = stringSetPreferencesKey("confirmed_keys")
        private val KEY_OUTBOX = stringPreferencesKey("outbox")
    }
}
