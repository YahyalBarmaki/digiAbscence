package sn.uadb.gesabscence.ble

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import sn.uadb.gesabscence.GesAbsenceApp
import sn.uadb.gesabscence.R

/**
 * Foreground service that keeps the Teacher's BLE advertisement alive while
 * the call is open, even if the screen is backgrounded.
 *
 * Controlled with explicit intents:
 *  - [BleContract.ACTION_START] + [BleContract.EXTRA_SESSION_ID]
 *  - [BleContract.ACTION_STOP]
 */
class BleAdvertiserService : Service() {

    private val advertiser by lazy { BleAdvertiser(applicationContext) }
    private var currentSessionId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            BleContract.ACTION_START -> {
                val sessionId = intent.getStringExtra(BleContract.EXTRA_SESSION_ID)
                if (sessionId.isNullOrBlank()) {
                    AdvertiserStatusBus.update(AdvertiserStatus.Error("session_id manquant."))
                    stopSelf()
                    return START_NOT_STICKY
                }
                startAdvertising(sessionId)
            }

            BleContract.ACTION_STOP -> {
                stopAdvertising()
                return START_NOT_STICKY
            }

            else -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun startAdvertising(sessionId: String) {
        currentSessionId = sessionId
        AdvertiserStatusBus.update(AdvertiserStatus.Starting)
        startAsForeground(sessionId)

        advertiser.start(
            sessionId = sessionId,
            onStarted = {
                AdvertiserStatusBus.update(AdvertiserStatus.Advertising(sessionId))
            },
            onError = { reason ->
                AdvertiserStatusBus.update(AdvertiserStatus.Error(reason))
                stopAdvertising()
            },
        )
    }

    private fun stopAdvertising() {
        advertiser.stop()
        currentSessionId = null
        if (AdvertiserStatusBus.status.value !is AdvertiserStatus.Error) {
            AdvertiserStatusBus.update(AdvertiserStatus.Idle)
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        advertiser.stop()
        super.onDestroy()
    }

    private fun startAsForeground(sessionId: String) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            BleContract.FOREGROUND_NOTIFICATION_ID,
            buildNotification(sessionId),
            type,
        )
    }

    private fun buildNotification(sessionId: String): Notification =
        NotificationCompat.Builder(this, GesAbsenceApp.CHANNEL_BLE_SERVICE)
            .setContentTitle(getString(R.string.teacher_session_label))
            .setContentText(getString(R.string.notif_advertising_text, sessionId))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    companion object {
        fun start(context: Context, sessionId: String) {
            val intent = Intent(context, BleAdvertiserService::class.java).apply {
                action = BleContract.ACTION_START
                putExtra(BleContract.EXTRA_SESSION_ID, sessionId)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, BleAdvertiserService::class.java).apply {
                action = BleContract.ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
