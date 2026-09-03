package sn.uadb.gesabscence.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Thin wrapper around [BluetoothLeScanner]. Filters on our manufacturer id,
 * decodes the session id, applies the RSSI threshold, and reports matches.
 */
class BleScanner(private val context: Context) {

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var scanner: BluetoothLeScanner? = null
    private var callback: ScanCallback? = null

    fun interface Listener {
        /** A course cleared [threshold] — [rssi] >= threshold. */
        fun onCourseInRange(sessionId: String, rssi: Int)
    }

    fun preflight(): String? = when {
        adapter == null -> "Bluetooth non disponible sur cet appareil."
        !adapter.isEnabled -> "Veuillez activer le Bluetooth."
        !hasScanPermission() -> "Autorisation de scan Bluetooth manquante."
        adapter.bluetoothLeScanner == null -> "Scan BLE indisponible sur cet appareil."
        else -> null
    }

    fun start(threshold: Int, listener: Listener, onError: (String) -> Unit) {
        preflight()?.let { onError(it); return }
        val s = adapter?.bluetoothLeScanner ?: run { onError("Scanner BLE indisponible."); return }
        if (callback != null) stop()

        val filters = listOf(
            ScanFilter.Builder()
                .setManufacturerData(BleContract.MANUFACTURER_ID, ByteArray(0), ByteArray(0))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setReportDelay(0)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                    setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                }
            }
            .build()

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handle(result, threshold, listener)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { handle(it, threshold, listener) }
            }

            override fun onScanFailed(errorCode: Int) {
                callback = null
                onError("Échec du scan BLE (code $errorCode).")
            }
        }

        try {
            s.startScan(filters, settings, cb)
            scanner = s
            callback = cb
        } catch (e: SecurityException) {
            onError("Permission Bluetooth refusée : ${e.message}")
        }
    }

    fun stop() {
        val cb = callback ?: return
        try {
            scanner?.stopScan(cb)
        } catch (e: SecurityException) {
            Log.w(TAG, "stopScan denied", e)
        }
        callback = null
        scanner = null
    }

    val isScanning: Boolean get() = callback != null

    private fun handle(result: ScanResult, threshold: Int, listener: Listener) {
        val mfg = result.scanRecord?.getManufacturerSpecificData(BleContract.MANUFACTURER_ID)
        val sessionId = BleContract.sessionIdFromBytes(mfg) ?: return
        val rssi = result.rssi
        Log.d(TAG, "seen session=$sessionId rssi=$rssi (threshold=$threshold)")
        if (rssi >= threshold) listener.onCourseInRange(sessionId, rssi)
    }

    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            granted(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            granted(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "BleScanner"
    }
}
