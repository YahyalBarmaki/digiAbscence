package sn.uadb.gesabscence.ble

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Observable state of the Student-side BLE scanner. */
sealed interface ScannerStatus {
    data object Idle : ScannerStatus
    data object Scanning : ScannerStatus
    data class CourseDetected(val sessionId: String, val rssi: Int) : ScannerStatus
    data class Error(val reason: String) : ScannerStatus
}

/**
 * Process-wide bus: the foreground [BleScannerService] publishes here and
 * [sn.uadb.gesabscence.ui.student.StudentViewModel] observes it.
 */
object ScannerStatusBus {
    private val _status = MutableStateFlow<ScannerStatus>(ScannerStatus.Idle)
    val status: StateFlow<ScannerStatus> = _status.asStateFlow()

    fun update(value: ScannerStatus) {
        _status.value = value
    }
}

/**
 * Decides when a fresh "Cours détecté" notification should be raised, so the
 * student is not spammed while the scanner keeps seeing the same beacon.
 * Pure logic — unit tested.
 */
class DetectionNotifier(private val cooldownMs: Long = BleContract.NOTIFY_COOLDOWN_MS) {

    private var lastSessionId: String? = null
    private var lastNotifiedAt: Long = 0L

    fun shouldNotify(sessionId: String, nowMs: Long): Boolean {
        val changedSession = sessionId != lastSessionId
        val cooledDown = nowMs - lastNotifiedAt >= cooldownMs
        return if (changedSession || cooledDown) {
            lastSessionId = sessionId
            lastNotifiedAt = nowMs
            true
        } else {
            false
        }
    }

    fun reset() {
        lastSessionId = null
        lastNotifiedAt = 0L
    }
}
