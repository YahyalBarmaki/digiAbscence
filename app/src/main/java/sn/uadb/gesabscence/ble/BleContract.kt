package sn.uadb.gesabscence.ble

/**
 * Constants shared by the Teacher advertiser (Module 2) and the Student
 * scanner (Module 3).
 *
 * The session id is carried in BLE **manufacturer data**: 2 bytes of
 * company id followed by the 8 ASCII chars of [sn.uadb.gesabscence.util.SessionId].
 * We use the reserved test company id `0xFFFF`, which is fine for a POC and
 * needs no Bluetooth SIG registration.
 */
object BleContract {

    /** Reserved "for internal/testing use" company identifier. */
    const val MANUFACTURER_ID: Int = 0xFFFF

    /** Session id length in bytes == chars (see SessionId). */
    const val SESSION_ID_BYTES: Int = 8

    // --- Foreground service control : advertiser (Module 2) ---
    const val ACTION_START = "sn.uadb.gesabscence.ble.action.START_ADVERTISING"
    const val ACTION_STOP = "sn.uadb.gesabscence.ble.action.STOP_ADVERTISING"
    const val EXTRA_SESSION_ID = "sn.uadb.gesabscence.ble.extra.SESSION_ID"

    // --- Foreground service control : scanner (Module 3) ---
    const val ACTION_START_SCAN = "sn.uadb.gesabscence.ble.action.START_SCAN"
    const val ACTION_STOP_SCAN = "sn.uadb.gesabscence.ble.action.STOP_SCAN"
    const val EXTRA_RSSI_THRESHOLD = "sn.uadb.gesabscence.ble.extra.RSSI_THRESHOLD"

    const val FOREGROUND_NOTIFICATION_ID = 4201
    const val SCANNER_FOREGROUND_NOTIFICATION_ID = 4202
    const val PRESENCE_NOTIFICATION_ID = 4203

    /** Re-notify about the same session at most this often. */
    const val NOTIFY_COOLDOWN_MS = 60_000L

    fun sessionIdToBytes(sessionId: String): ByteArray =
        sessionId.toByteArray(Charsets.US_ASCII)

    fun sessionIdFromBytes(bytes: ByteArray?): String? {
        if (bytes == null || bytes.size < SESSION_ID_BYTES) return null
        return String(bytes, 0, SESSION_ID_BYTES, Charsets.US_ASCII)
            .takeIf { it.all { c -> c in '0'..'9' || c in 'A'..'F' } }
    }
}
