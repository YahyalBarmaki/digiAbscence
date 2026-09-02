package sn.uadb.gesabscence.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Thin wrapper around [BluetoothLeAdvertiser]. Broadcasts a session id in
 * manufacturer data (see [BleContract]). One instance advertises at most one
 * session at a time; call [stop] before starting another.
 */
class BleAdvertiser(private val context: Context) {

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var advertiser: BluetoothLeAdvertiser? = null
    private var activeCallback: AdvertiseCallback? = null

    /** Precondition check surfaced to the UI as a readable reason, or null if OK. */
    fun preflight(): String? = when {
        adapter == null -> "Bluetooth non disponible sur cet appareil."
        !adapter.isEnabled -> "Veuillez activer le Bluetooth."
        !hasAdvertisePermission() -> "Autorisation BLUETOOTH_ADVERTISE manquante."
        adapter.bluetoothLeAdvertiser == null ->
            "Cet appareil ne supporte pas la diffusion BLE (advertising)."
        else -> null
    }

    /**
     * @param onStarted invoked once the OS confirms advertising is live
     * @param onError   invoked with a readable reason on any failure
     */
    fun start(
        sessionId: String,
        onStarted: () -> Unit,
        onError: (String) -> Unit,
    ) {
        preflight()?.let { onError(it); return }
        val adv = adapter?.bluetoothLeAdvertiser ?: run {
            onError("Advertiser BLE indisponible."); return
        }
        if (activeCallback != null) stop()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false)
            .setTimeout(0)
            .build()

        // Keep the payload small: flags (3) + manufacturer data (2 company id
        // + 8 session id + 2 overhead) = 15 bytes, well under the 31-byte
        // legacy advertisement budget. No 128-bit service UUID here.
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addManufacturerData(
                BleContract.MANUFACTURER_ID,
                BleContract.sessionIdToBytes(sessionId),
            )
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.i(TAG, "Advertising session=$sessionId")
                onStarted()
            }

            override fun onStartFailure(errorCode: Int) {
                activeCallback = null
                onError(failureReason(errorCode))
            }
        }

        try {
            adv.startAdvertising(settings, data, callback)
            advertiser = adv
            activeCallback = callback
        } catch (e: SecurityException) {
            onError("Permission Bluetooth refusée : ${e.message}")
        }
    }

    fun stop() {
        val cb = activeCallback ?: return
        try {
            advertiser?.stopAdvertising(cb)
        } catch (e: SecurityException) {
            Log.w(TAG, "stopAdvertising denied", e)
        }
        activeCallback = null
        advertiser = null
    }

    val isAdvertising: Boolean get() = activeCallback != null

    private fun hasAdvertisePermission(): Boolean {
        // BLUETOOTH_ADVERTISE only exists / is enforced on API 31+.
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_ADVERTISE,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun failureReason(code: Int): String = when (code) {
        AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE ->
            "Données d'annonce trop volumineuses."
        AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS ->
            "Trop d'annonces BLE actives sur l'appareil."
        AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED ->
            "Diffusion déjà démarrée."
        AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR ->
            "Erreur interne du Bluetooth."
        AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED ->
            "Advertising BLE non supporté."
        else -> "Échec du démarrage de la diffusion (code $code)."
    }

    companion object {
        private const val TAG = "BleAdvertiser"
    }
}
