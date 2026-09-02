package sn.uadb.gesabscence.ble

import android.Manifest
import android.os.Build

/**
 * Central list of the runtime permissions the BLE features need, resolved
 * per Android version. Used by both the Teacher (advertise) and Student
 * (scan) screens through Accompanist's permission state.
 */
object BlePermissions {

    /** Permissions required to run BLE advertising (Teacher mode, Module 2). */
    val advertise: List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            // Legacy: BLUETOOTH / BLUETOOTH_ADMIN are install-time on API <= 30.
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Permissions required to run BLE scanning (Student mode, Module 3). */
    val scan: List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
