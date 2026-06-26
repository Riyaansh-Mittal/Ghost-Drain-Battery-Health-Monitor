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
import com.ghost.drain.battery.health.monitor.repository.BatteryRepository
import kotlinx.coroutines.*

class BatteryMonitorService : Service() {

    companion object {
        const val CHANNEL_ID          = "battery_monitor_channel"
        const val NOTIF_ID_PERSISTENT = 1
        private const val TAG         = "BatteryMonitorService"

        fun buildStartIntent(context: android.content.Context) =
            Intent(context, BatteryMonitorService::class.java)
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var repository: BatteryRepository

    private var sessionStartTime: Long?      = null
    private var sessionStartPercent: Int     = 0
    private var wasChargingLastTick: Boolean = false

    override fun onCreate() {
        super.onCreate()
        // Uses model.BatteryState throughout — no data.BatteryState reference here
        repository = BatteryRepository(BatteryDataSource(applicationContext))
        createNotificationChannel()
        startForeground(
            NOTIF_ID_PERSISTENT,
            buildPersistentNotification("Starting…", "Initializing battery monitor")
        )
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startMonitoringLoop()
        return START_STICKY
    }

    private fun startMonitoringLoop() {
        serviceScope.launch {
            repository.batteryState.collect { state ->
                Log.d(TAG, buildLogString(state))
                updatePersistentNotification(state)
                trackSession(state)
            }
        }
    }

    private fun trackSession(state: BatteryState) {
        if (state.isCharging && !wasChargingLastTick) {
            sessionStartTime    = System.currentTimeMillis()
            sessionStartPercent = state.percent
            Log.d(TAG, "Session started at ${state.percent}%")
        }
        if (!state.isCharging && wasChargingLastTick && sessionStartTime != null) {
            val mins = (System.currentTimeMillis() - sessionStartTime!!) / 60_000
            Log.d(TAG, "Session ended. ${mins}min, +${state.percent - sessionStartPercent}%")
            // TODO Part 5: sessionDao.insert(...)
            sessionStartTime = null
        }
        wasChargingLastTick = state.isCharging
    }

    private fun buildLogString(s: BatteryState): String =
        "Battery: ${s.percent}% | ${s.chargingLabel} | " +
        "Temp: ${s.temperatureCelsius}°C | ${s.currentMa}mA | " +
        "${"%.1f".format(s.wattage)}W | ${s.chargerVerdict}"

    private fun updatePersistentNotification(state: BatteryState) {
        val title = "${state.percent}% · ${state.chargingLabel}"
        val body  = "${"%.1f".format(state.temperatureCelsius)}°C · " +
                    "${state.currentMa}mA · " +
                    "${"%.1f".format(state.wattage)}W"
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID_PERSISTENT, buildPersistentNotification(title, body))
    }

    private fun buildPersistentNotification(title: String, body: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_battery_notification)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Battery Monitor", NotificationManager.IMPORTANCE_LOW
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