package sn.uadb.gesabscence

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

class GesAbsenceApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val presence = NotificationChannel(
            CHANNEL_PRESENCE_DETECTED,
            getString(R.string.student_title),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerte lorsqu'un cours est détecté à proximité."
        }

        val service = NotificationChannel(
            CHANNEL_BLE_SERVICE,
            "Service Bluetooth",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notification persistante du scan / de la diffusion BLE."
        }

        manager.createNotificationChannels(listOf(presence, service))
    }

    companion object {
        const val CHANNEL_PRESENCE_DETECTED = "presence_detected"
        const val CHANNEL_BLE_SERVICE = "ble_service"
    }
}
