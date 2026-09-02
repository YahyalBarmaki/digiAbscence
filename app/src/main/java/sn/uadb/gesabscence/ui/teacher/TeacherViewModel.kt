package sn.uadb.gesabscence.ui.teacher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import sn.uadb.gesabscence.ble.AdvertiserStatus
import sn.uadb.gesabscence.ble.AdvertiserStatusBus
import sn.uadb.gesabscence.ble.BleAdvertiserService
import sn.uadb.gesabscence.util.SessionId

data class TeacherUiState(
    val isAdvertising: Boolean = false,
    val isStarting: Boolean = false,
    val sessionId: String? = null,
    val startedAtMillis: Long? = null,
    val errorMessage: String? = null,
)

/**
 * Module 2: owns the Teacher session lifecycle and drives the foreground
 * [BleAdvertiserService]. UI state is derived from a local "intent" flow
 * (which session the teacher asked to run) combined with the real advertiser
 * status coming back from [AdvertiserStatusBus].
 */
class TeacherViewModel(app: Application) : AndroidViewModel(app) {

    private data class LocalState(
        val sessionId: String? = null,
        val startedAtMillis: Long? = null,
    )

    private val local = MutableStateFlow(LocalState())

    val uiState: StateFlow<TeacherUiState> =
        combine(local, AdvertiserStatusBus.status) { l, status ->
            when (status) {
                AdvertiserStatus.Idle -> TeacherUiState(
                    sessionId = l.sessionId,
                    startedAtMillis = l.startedAtMillis,
                )

                AdvertiserStatus.Starting -> TeacherUiState(
                    isStarting = true,
                    sessionId = l.sessionId,
                    startedAtMillis = l.startedAtMillis,
                )

                is AdvertiserStatus.Advertising -> TeacherUiState(
                    isAdvertising = true,
                    sessionId = status.sessionId,
                    startedAtMillis = l.startedAtMillis,
                )

                is AdvertiserStatus.Error -> TeacherUiState(
                    sessionId = l.sessionId,
                    startedAtMillis = l.startedAtMillis,
                    errorMessage = status.reason,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TeacherUiState())

    fun startSession() {
        if (uiState.value.isAdvertising || uiState.value.isStarting) return
        val sessionId = SessionId.generate()
        local.value = LocalState(sessionId = sessionId, startedAtMillis = System.currentTimeMillis())
        BleAdvertiserService.start(getApplication(), sessionId)
        // TODO(Module 5): create the session document in Firestore
    }

    fun stopSession() {
        BleAdvertiserService.stop(getApplication())
        local.value = LocalState()
        AdvertiserStatusBus.update(AdvertiserStatus.Idle)
        // TODO(Module 5): call the "close session" Cloud Function
    }

    fun dismissError() {
        if (AdvertiserStatusBus.status.value is AdvertiserStatus.Error) {
            AdvertiserStatusBus.update(AdvertiserStatus.Idle)
        }
    }
}
