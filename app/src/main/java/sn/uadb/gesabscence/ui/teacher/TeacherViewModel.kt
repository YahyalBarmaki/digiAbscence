package sn.uadb.gesabscence.ui.teacher

import androidx.lifecycle.ViewModel
import sn.uadb.gesabscence.util.SessionId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TeacherUiState(
    val isAdvertising: Boolean = false,
    val sessionId: String? = null,
    val startedAtMillis: Long? = null,
)

/**
 * Module 1: owns the Teacher-mode UI state and the session lifecycle
 * (generate id / start / stop). The real BLE advertiser is plugged in
 * here in Module 2.
 */
class TeacherViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(TeacherUiState())
    val uiState: StateFlow<TeacherUiState> = _uiState.asStateFlow()

    fun startSession() {
        if (_uiState.value.isAdvertising) return
        _uiState.update {
            it.copy(
                isAdvertising = true,
                sessionId = SessionId.generate(),
                startedAtMillis = System.currentTimeMillis(),
            )
        }
        // TODO(Module 2): start BleAdvertiser with uiState.value.sessionId
    }

    fun stopSession() {
        if (!_uiState.value.isAdvertising) return
        _uiState.update { it.copy(isAdvertising = false) }
        // TODO(Module 2): stop BleAdvertiser
        // TODO(Module 5): call the "close session" Cloud Function
    }
}
