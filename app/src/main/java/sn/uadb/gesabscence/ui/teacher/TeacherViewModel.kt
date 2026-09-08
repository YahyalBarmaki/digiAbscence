package sn.uadb.gesabscence.ui.teacher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sn.uadb.gesabscence.ble.AdvertiserStatus
import sn.uadb.gesabscence.ble.AdvertiserStatusBus
import sn.uadb.gesabscence.ble.BleAdvertiserService
import sn.uadb.gesabscence.data.RolePreferences
import sn.uadb.gesabscence.data.SessionBackend
import sn.uadb.gesabscence.data.SessionBackendFactory
import sn.uadb.gesabscence.util.SessionId

data class TeacherUiState(
    val isAdvertising: Boolean = false,
    val isStarting: Boolean = false,
    val sessionId: String? = null,
    val startedAtMillis: Long? = null,
    val teacherId: String? = null,
    val classId: String? = null,
    val backendSynced: Boolean = false,
    val errorMessage: String? = null,
) {
    val identitySet: Boolean get() = !teacherId.isNullOrBlank() && !classId.isNullOrBlank()
}

/**
 * Modules 2 & 5: drives the foreground [BleAdvertiserService] and mirrors the
 * session into Firestore — `openSession` on start, `closeSession` on stop
 * (which marks absent every rostered student who never confirmed). Backend
 * calls are no-ops when Firebase is not configured.
 */
class TeacherViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = RolePreferences(app)
    private val sessionBackend: SessionBackend = SessionBackendFactory.create(app)

    private data class LocalState(
        val sessionId: String? = null,
        val startedAtMillis: Long? = null,
        val backendSynced: Boolean = false,
    )

    private val local = MutableStateFlow(LocalState())

    val uiState: StateFlow<TeacherUiState> =
        combine(
            local,
            AdvertiserStatusBus.status,
            prefs.teacherId,
            prefs.classId,
        ) { l, status, teacherId, classId ->
            val base = TeacherUiState(
                sessionId = l.sessionId,
                startedAtMillis = l.startedAtMillis,
                teacherId = teacherId,
                classId = classId,
                backendSynced = l.backendSynced,
            )
            when (status) {
                AdvertiserStatus.Idle -> base
                AdvertiserStatus.Starting -> base.copy(isStarting = true)
                is AdvertiserStatus.Advertising -> base.copy(
                    isAdvertising = true,
                    sessionId = status.sessionId,
                )

                is AdvertiserStatus.Error -> base.copy(errorMessage = status.reason)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TeacherUiState())

    fun setTeacherIdentity(teacherId: String, classId: String) {
        val t = teacherId.trim()
        val c = classId.trim()
        if (t.isEmpty() || c.isEmpty()) return
        viewModelScope.launch { prefs.setTeacherIdentity(t, c) }
    }

    fun startSession() {
        if (uiState.value.isAdvertising || uiState.value.isStarting) return
        val sessionId = SessionId.generate()
        local.value = LocalState(sessionId = sessionId, startedAtMillis = System.currentTimeMillis())
        BleAdvertiserService.start(getApplication(), sessionId)

        val state = uiState.value
        if (sessionBackend.isAvailable && state.identitySet) {
            viewModelScope.launch {
                sessionBackend.open(sessionId, state.classId!!, state.teacherId!!)
                    .onSuccess { local.value = local.value.copy(backendSynced = true) }
            }
        }
    }

    fun stopSession() {
        val sessionId = local.value.sessionId
        BleAdvertiserService.stop(getApplication())
        AdvertiserStatusBus.update(AdvertiserStatus.Idle)
        if (sessionBackend.isAvailable && sessionId != null) {
            viewModelScope.launch { sessionBackend.close(sessionId) }
        }
        local.value = LocalState()
    }

    fun dismissError() {
        if (AdvertiserStatusBus.status.value is AdvertiserStatus.Error) {
            AdvertiserStatusBus.update(AdvertiserStatus.Idle)
        }
    }
}
