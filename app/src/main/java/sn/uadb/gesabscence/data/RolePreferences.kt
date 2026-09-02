package sn.uadb.gesabscence.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The two operating modes of the app. */
enum class AppRole { TEACHER, STUDENT }

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ges_absence_prefs")

/**
 * Persists the last chosen [AppRole] so the app reopens directly in the
 * right mode. Also holds the lightweight local identity used later by the
 * BLE + backend layers (student id, RSSI threshold).
 */
class RolePreferences(private val context: Context) {

    val role: Flow<AppRole?> = context.dataStore.data.map { prefs ->
        prefs[KEY_ROLE]?.let { runCatching { AppRole.valueOf(it) }.getOrNull() }
    }

    val studentId: Flow<String?> = context.dataStore.data.map { it[KEY_STUDENT_ID] }

    val rssiThreshold: Flow<Int> = context.dataStore.data.map { it[KEY_RSSI_THRESHOLD] ?: DEFAULT_RSSI }

    suspend fun setRole(role: AppRole) {
        context.dataStore.edit { it[KEY_ROLE] = role.name }
    }

    suspend fun clearRole() {
        context.dataStore.edit { it.remove(KEY_ROLE) }
    }

    suspend fun setStudentId(id: String) {
        context.dataStore.edit { it[KEY_STUDENT_ID] = id }
    }

    suspend fun setRssiThreshold(value: Int) {
        context.dataStore.edit { it[KEY_RSSI_THRESHOLD] = value }
    }

    companion object {
        const val DEFAULT_RSSI = -70
        private val KEY_ROLE = stringPreferencesKey("role")
        private val KEY_STUDENT_ID = stringPreferencesKey("student_id")
        private val KEY_RSSI_THRESHOLD = androidx.datastore.preferences.core.intPreferencesKey("rssi_threshold")
    }
}
