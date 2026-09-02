package sn.uadb.gesabscence.ble

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Observable state of the Teacher-side BLE advertiser. */
sealed interface AdvertiserStatus {
    data object Idle : AdvertiserStatus
    data object Starting : AdvertiserStatus
    data class Advertising(val sessionId: String) : AdvertiserStatus
    data class Error(val reason: String) : AdvertiserStatus
}

/**
 * Process-wide bus so the foreground [BleAdvertiserService] can publish its
 * state and [sn.uadb.gesabscence.ui.teacher.TeacherViewModel] can observe it
 * without service binding.
 */
object AdvertiserStatusBus {
    private val _status = MutableStateFlow<AdvertiserStatus>(AdvertiserStatus.Idle)
    val status: StateFlow<AdvertiserStatus> = _status.asStateFlow()

    fun update(value: AdvertiserStatus) {
        _status.value = value
    }
}
