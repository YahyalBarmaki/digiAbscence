package sn.uadb.gesabscence.ui.student

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sn.uadb.gesabscence.ble.BleScannerService
import sn.uadb.gesabscence.ble.ScannerStatus
import sn.uadb.gesabscence.ble.ScannerStatusBus
import sn.uadb.gesabscence.data.RolePreferences

data class StudentUiState(
    val isScanning: Boolean = false,
    val detectedSessionId: String? = null,
    val lastRssi: Int? = null,
    val rssiThreshold: Int = RolePreferences.DEFAULT_RSSI,
    val presenceConfirmed: Boolean = false,
    val confirmInFlight: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Module 3: drives the foreground [BleScannerService], exposes the tunable
 * RSSI threshold (persisted in [RolePreferences]), and reflects detections
 * coming back on [ScannerStatusBus].
 */
class StudentViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = RolePreferences(app)

    private data class Local(
        val scanRequested: Boolean = false,
        val presenceConfirmed: Boolean = false,
        val confirmInFlight: Boolean = false,
    )

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<StudentUiState> =
        combine(
            local,
            ScannerStatusBus.status,
            prefs.rssiThreshold,
        ) { l, status, threshold ->
            val base = StudentUiState(
                rssiThreshold = threshold,
                presenceConfirmed = l.presenceConfirmed,
                confirmInFlight = l.confirmInFlight,
            )
            when (status) {
                ScannerStatus.Idle -> base.copy(isScanning = false)
                ScannerStatus.Scanning -> base.copy(isScanning = true)
                is ScannerStatus.CourseDetected -> base.copy(
                    isScanning = true,
                    detectedSessionId = status.sessionId,
                    lastRssi = status.rssi,
                )

                is ScannerStatus.Error -> base.copy(
                    isScanning = false,
                    errorMessage = status.reason,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudentUiState())

    init {
        // Reset the one-shot confirm gate whenever a different session appears.
        ScannerStatusBus.status
            .onEach { status ->
                if (status is ScannerStatus.CourseDetected &&
                    status.sessionId != lastKnownSession
                ) {
                    lastKnownSession = status.sessionId
                    local.value = local.value.copy(presenceConfirmed = false)
                }
            }
            .launchIn(viewModelScope)
    }

    private var lastKnownSession: String? = null

    fun startScan() {
        local.value = local.value.copy(scanRequested = true)
        viewModelScope.launch {
            val threshold = uiState.value.rssiThreshold
            BleScannerService.start(getApplication(), threshold)
        }
    }

    fun stopScan() {
        local.value = local.value.copy(scanRequested = false)
        BleScannerService.stop(getApplication())
        ScannerStatusBus.update(ScannerStatus.Idle)
    }

    fun setRssiThreshold(value: Int) {
        val clamped = value.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
        viewModelScope.launch {
            prefs.setRssiThreshold(clamped)
            // Apply live if a scan is running.
            if (local.value.scanRequested) {
                BleScannerService.start(getApplication(), clamped)
            }
        }
    }

    fun confirmPresence() {
        val state = uiState.value
        if (state.presenceConfirmed || state.confirmInFlight || state.detectedSessionId == null) return
        local.value = local.value.copy(confirmInFlight = true)
        // TODO(Module 4/5): POST student_id + session_id + timestamp to the backend.
        local.value = local.value.copy(confirmInFlight = false, presenceConfirmed = true)
    }

    fun dismissError() {
        if (ScannerStatusBus.status.value is ScannerStatus.Error) {
            ScannerStatusBus.update(ScannerStatus.Idle)
        }
    }

    companion object {
        const val MIN_THRESHOLD = -95
        const val MAX_THRESHOLD = -40
    }
}
