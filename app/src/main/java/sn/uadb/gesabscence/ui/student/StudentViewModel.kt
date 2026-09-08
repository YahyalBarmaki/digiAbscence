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
import sn.uadb.gesabscence.data.ConfirmResult
import sn.uadb.gesabscence.data.PresenceRepositoryProvider
import sn.uadb.gesabscence.data.RolePreferences
import sn.uadb.gesabscence.data.SyncingPresenceRepository

data class StudentUiState(
    val studentId: String? = null,
    val isScanning: Boolean = false,
    val detectedSessionId: String? = null,
    val lastRssi: Int? = null,
    val rssiThreshold: Int = RolePreferences.DEFAULT_RSSI,
    val presenceConfirmed: Boolean = false,
    val confirmedAtMillis: Long? = null,
    val confirmInFlight: Boolean = false,
    val confirmError: String? = null,
    val scanError: String? = null,
) {
    val canConfirm: Boolean
        get() = !studentId.isNullOrBlank() &&
            detectedSessionId != null &&
            !presenceConfirmed &&
            !confirmInFlight
}

/**
 * Module 4: real presence confirmation. On tap we send
 * student_id + session_id + timestamp to [PresenceRepository]; the button is
 * then locked for that session (anti-doublon), a lock that also survives a
 * relaunch because the repository persists confirmed keys.
 */
class StudentViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = RolePreferences(app)
    private val repo: SyncingPresenceRepository = PresenceRepositoryProvider.create(app)

    private data class Local(
        val scanRequested: Boolean = false,
        val presenceConfirmed: Boolean = false,
        val confirmedAtMillis: Long? = null,
        val confirmInFlight: Boolean = false,
        val confirmError: String? = null,
    )

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<StudentUiState> =
        combine(
            local,
            ScannerStatusBus.status,
            prefs.rssiThreshold,
            prefs.studentId,
        ) { l, status, threshold, studentId ->
            val base = StudentUiState(
                studentId = studentId,
                rssiThreshold = threshold,
                presenceConfirmed = l.presenceConfirmed,
                confirmedAtMillis = l.confirmedAtMillis,
                confirmInFlight = l.confirmInFlight,
                confirmError = l.confirmError,
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
                    scanError = status.reason,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudentUiState())

    private var lastResolvedSession: String? = null

    init {
        // When a (new) session comes into range, reconcile the confirm lock
        // with what the repository already knows for this student.
        combine(prefs.studentId, ScannerStatusBus.status) { sid, status -> sid to status }
            .onEach { (sid, status) ->
                if (status !is ScannerStatus.CourseDetected) return@onEach
                if (status.sessionId == lastResolvedSession) return@onEach
                lastResolvedSession = status.sessionId
                val already = !sid.isNullOrBlank() && repo.isConfirmed(sid, status.sessionId)
                local.value = local.value.copy(
                    presenceConfirmed = already,
                    confirmedAtMillis = null,
                    confirmError = null,
                )
            }
            .launchIn(viewModelScope)

        // Push any confirmations captured while offline.
        viewModelScope.launch { runCatching { repo.flushOutbox() } }
    }

    fun setStudentId(value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { prefs.setStudentId(trimmed) }
    }

    fun startScan() {
        local.value = local.value.copy(scanRequested = true)
        viewModelScope.launch { BleScannerService.start(getApplication(), uiState.value.rssiThreshold) }
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
            if (local.value.scanRequested) BleScannerService.start(getApplication(), clamped)
        }
    }

    fun confirmPresence() {
        val state = uiState.value
        val studentId = state.studentId
        val sessionId = state.detectedSessionId
        if (studentId.isNullOrBlank() || sessionId == null) return
        if (state.presenceConfirmed || state.confirmInFlight) return

        local.value = local.value.copy(confirmInFlight = true, confirmError = null)
        viewModelScope.launch {
            when (val result = repo.confirm(studentId, sessionId, System.currentTimeMillis())) {
                is ConfirmResult.Success -> local.value = local.value.copy(
                    confirmInFlight = false,
                    presenceConfirmed = true,
                    confirmedAtMillis = result.confirmation.timestampMillis,
                )

                ConfirmResult.AlreadyConfirmed -> local.value = local.value.copy(
                    confirmInFlight = false,
                    presenceConfirmed = true,
                )

                is ConfirmResult.Failed -> local.value = local.value.copy(
                    confirmInFlight = false,
                    confirmError = result.reason,
                )
            }
        }
    }

    fun dismissError() {
        local.value = local.value.copy(confirmError = null)
        if (ScannerStatusBus.status.value is ScannerStatus.Error) {
            ScannerStatusBus.update(ScannerStatus.Idle)
        }
    }

    companion object {
        const val MIN_THRESHOLD = -95
        const val MAX_THRESHOLD = -40
    }
}
