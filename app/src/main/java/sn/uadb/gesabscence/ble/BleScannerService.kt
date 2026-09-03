package sn.uadb.gesabscence.ble

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import sn.uadb.gesabscence.GesAbsenceApp
import sn.uadb.gesabscence.MainActivity
import sn.uadb.gesabscence.R
import sn.uadb.gesabscence.data.RolePreferences

/**
 * Foreground service that scans for a Teacher's advertisement in the
 * background (screen off / app backgrounded). When a session clears the
 * RSSI threshold it raises the "Cours détecté — confirmer ma présence"
 * notification and publishes the detection on [ScannerStatusBus].
 */
class BleScannerService : Service() {

    private val scanner by lazy { BleScanner(applicationContext) }
    private val notifier = DetectionNotifier()
    private var threshold: Int = RolePreferences.DEFAULT_RSSI

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            BleContract.ACTION_START_SCAN -> {
                threshold = intent.getIntExtra(
                    BleContract.EXTRA_RSSI_THRESHOLD, RolePreferences.DEFAULT_RSSI,
                )
                startScanning()
            }

            BleContract.ACTION_STOP_SCAN -> {
                stopScanning()
                return START_NOT_STICKY
            }

            else -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun startScanning() {
        notifier.reset()
        ScannerStatusBus.update(ScannerStatus.Scanning)
        startAsForeground()

        scanner.start(
            threshold = threshold,
            listener = { sessionId, rssi -> onCourseInRange(sessionId, rssi) },
            onError = { reason ->
                ScannerStatusBus.update(ScannerStatus.Error(reason))
                stopScanning()
            },
        )
    }

    private fun onCourseInRange(sessionId: String, rssi: Int) {
        ScannerStatusBus.update(ScannerStatus.CourseDetected(sessionId, rssi))
        if (notifier.shouldNotify(sessionId, System.currentTimeMillis())) {
            raisePresenceNotification()
        }
    }

    private fun stopScanning() {
        scanner.stop()
        if (ScannerStatusBus.status.value !is ScannerStatus.Error) {
            ScannerStatusBus.update(ScannerStatus.Idle)
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scanner.stop()
        super.onDestroy()
    }

    // --- notifications ---

    private fun contentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun startAsForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        val notif: Notification =
            NotificationCompat.Builder(this, GesAbsenceApp.CHANNEL_BLE_SERVICE)
                .setContentTitle(getString(R.string.student_title))
                .setContentText(getString(R.string.notif_scanning_text))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(contentIntent())
                .build()
        ServiceCompat.startForeground(
            this, BleContract.SCANNER_FOREGROUND_NOTIFICATION_ID, notif, type,
        )
    }

    private fun raisePresenceNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val notif = NotificationCompat.Builder(this, GesAbsenceApp.CHANNEL_PRESENCE_DETECTED)
            .setContentTitle(getString(R.string.notif_detected_title))
            .setContentText(getString(R.string.notif_detected_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        NotificationManagerCompat.from(this)
            .notify(BleContract.PRESENCE_NOTIFICATION_ID, notif)
    }

    companion object {
        fun start(context: Context, rssiThreshold: Int) {
            val intent = Intent(context, BleScannerService::class.java).apply {
                action = BleContract.ACTION_START_SCAN
                putExtra(BleContract.EXTRA_RSSI_THRESHOLD, rssiThreshold)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, BleScannerService::class.java).apply {
                action = BleContract.ACTION_STOP_SCAN
            }
            context.startService(intent)
        }
    }
}
