package sn.uadb.gesabscence.ui.student

import androidx.lifecycle.ViewModel
import sn.uadb.gesabscence.data.RolePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class StudentUiState(
    val isScanning: Boolean = false,
    val detectedSessionId: String? = null,
    val lastRssi: Int? = null,
    val rssiThreshold: Int = RolePreferences.DEFAULT_RSSI,
    val presenceConfirmed: Boolean = false,
    val confirmInFlight: Boolean = false,
)

/**
 * Module 1: owns the Student-mode UI state. The real BLE scanner (Module 3)
 * feeds [onCourseDetected]; the confirm action (Module 4) calls
 * [confirmPresence] which will hit the backend.
 */
class StudentViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(StudentUiState())
    val uiState: StateFlow<StudentUiState> = _uiState.asStateFlow()

    fun startScan() {
        _uiState.update { it.copy(isScanning = true) }
        // TODO(Module 3): start BleScanner with rssiThreshold
    }

    fun stopScan() {
        _uiState.update { it.copy(isScanning = false, detectedSessionId = null, lastRssi = null) }
        // TODO(Module 3): stop BleScanner
    }

    /** Called by the scanner when a session clears the RSSI threshold. */
    fun onCourseDetected(sessionId: String, rssi: Int) {
        _uiState.update { it.copy(detectedSessionId = sessionId, lastRssi = rssi) }
    }

    fun confirmPresence() {
        val state = _uiState.value
        if (state.presenceConfirmed || state.confirmInFlight || state.detectedSessionId == null) return
        _uiState.update { it.copy(confirmInFlight = true) }
        // TODO(Module 4/5): POST student_id + session_id + timestamp, then:
        _uiState.update { it.copy(confirmInFlight = false, presenceConfirmed = true) }
    }
}
