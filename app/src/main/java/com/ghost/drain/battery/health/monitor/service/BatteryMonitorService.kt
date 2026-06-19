package com.ghost.drain.battery.health.monitor.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ghost.drain.battery.health.monitor.R
import com.ghost.drain.battery.health.monitor.data.BatteryDataSource
import com.ghost.drain.battery.health.monitor.model.BatteryState
import kotlinx.coroutines.*

class BatteryMonitorService : Service() {

    companion object {
        const val CHANNEL_ID = "battery_monitor_channel"
        const val NOTIF_ID_PERSISTENT = 1
        private const val TAG = "BatteryMonitorService"

        fun buildStartIntent(context: android.content.Context) =
            Intent(context, BatteryMonitorService::class.java)
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var dataSource: BatteryDataSource

    override fun onCreate() {
        super.onCreate()
        dataSource = BatteryDataSource(applicationContext)
        createNotificationChannel()
        startForeground(NOTIF_ID_PERSISTENT, buildPersistentNotification("Starting…", "Initializing battery monitor"))
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startMonitoringLoop()
        return START_STICKY
    }

    private fun startMonitoringLoop() {
        serviceScope.launch {
            dataSource.batteryStateFlow(intervalMs = 5_000L).collect { state ->
                Log.d(TAG, buildLogString(state))
                updatePersistentNotification(state)
            }
        }
    }

    private fun buildLogString(s: BatteryState): String =
        "Battery: ${s.percent}% | ${s.chargingLabel} | " +
        "Temp: ${s.temperatureCelsius}°C | " +
        "${s.currentMa}mA | ${s.wattage}W | " +
        "Charger: ${s.chargerVerdict}"

    private fun updatePersistentNotification(state: BatteryState) {
        val title = "${state.percent}% · ${state.chargingLabel}"
        val body = "${state.temperatureCelsius}°C · ${state.currentMa}mA · ${state.chargerVerdict.name}"
        val notification = buildPersistentNotification(title, body)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID_PERSISTENT, notification)
    }

    private fun buildPersistentNotification(title: String, body: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_battery_notification) // create this drawable next step
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Battery Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Live battery status"
            setShowBadge(false)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}