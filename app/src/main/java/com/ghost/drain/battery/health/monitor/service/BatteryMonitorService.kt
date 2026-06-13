package com.ghost.drain.battery.health.monitor.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.ghost.drain.battery.health.monitor.R

/**
 * Persistent foreground service. Priority #1 in build order.
 * Monitors battery every ~5s via sticky broadcast.
 * HIGH alarm loops every 60s — stops ONLY on cable removed or Dismiss tap.
 * Screen lock does NOT stop it. Notification swipe does NOT stop it.
 */
class BatteryMonitorService : LifecycleService() {

    companion object {
        const val CHANNEL_MONITOR   = "battery_monitor_channel"
        const val CHANNEL_ALARM     = "battery_alarm_channel"
        const val CHANNEL_GHOST     = "ghost_drain_channel"

        const val NOTIF_ID_FOREGROUND = 1
        const val NOTIF_ID_HIGH_ALARM = 2
        const val NOTIF_ID_LOW_ALARM  = 3
        const val NOTIF_ID_HEAT_ALARM = 4
        const val NOTIF_ID_GHOST      = 5

        const val ACTION_DISMISS_ALARM = "com.ghost.drain.DISMISS_ALARM"

        fun start(context: Context) {
            val intent = Intent(context, BatteryMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) =
            context.stopService(Intent(context, BatteryMonitorService::class.java))
    }

    // Load from SharedPreferences in production
    private var highAlarmPct  = 80
    private var lowAlarmPct   = 20
    private var heatAlarmC    = 42
    private var highAlarmOn   = true
    private var lowAlarmOn    = true
    private var heatAlarmOn   = true
    private var alarmRepeatMs = 60_000L

    private var highAlarmFiring   = false
    private var lowAlarmFired     = false
    private var lowFinalFired     = false
    private var lastHeatEventTemp = 0f

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var notificationManager: NotificationManager

    private val highAlarmRunnable = object : Runnable {
        override fun run() {
            if (highAlarmFiring) {
                playAlarmNotification()
                handler.postDelayed(this, alarmRepeatMs)
            }
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED    -> handleBatteryChanged(intent)
                Intent.ACTION_POWER_DISCONNECTED -> stopHighAlarm()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannels()
        startForeground(NOTIF_ID_FOREGROUND, buildPersistentNotification("Monitoring battery…"))
        registerBatteryReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_DISMISS_ALARM) stopHighAlarm()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        handler.removeCallbacksAndMessages(null)
    }

    private fun handleBatteryChanged(intent: Intent) {
        val level  = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale  = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val pct    = if (scale > 0) (level * 100 / scale) else return
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val tempC  = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        updatePersistentNotification(pct, tempC, isCharging)

        // HIGH alarm
        if (highAlarmOn && isCharging && pct >= highAlarmPct && !highAlarmFiring)
            startHighAlarm()

        // LOW alarm
        if (lowAlarmOn && !isCharging) {
            if (pct <= lowAlarmPct && !lowAlarmFired) {
                fireLowAlarm(pct); lowAlarmFired = true
            }
            if (pct <= (lowAlarmPct - 5) && !lowFinalFired && lowAlarmFired) {
                fireLowAlarm(pct, isFinalWarning = true); lowFinalFired = true
            }
            if (pct > lowAlarmPct + 5) { lowAlarmFired = false; lowFinalFired = false }
        }

        // Heat alarm
        if (heatAlarmOn && tempC >= heatAlarmC && lastHeatEventTemp < heatAlarmC)
            fireHeatAlarm(tempC)
        if (tempC < 40f) lastHeatEventTemp = 0f
        if (tempC >= heatAlarmC) lastHeatEventTemp = tempC
    }

    private fun startHighAlarm() {
        highAlarmFiring = true
        handler.post(highAlarmRunnable)
    }

    private fun stopHighAlarm() {
        highAlarmFiring = false
        handler.removeCallbacks(highAlarmRunnable)
        notificationManager.cancel(NOTIF_ID_HIGH_ALARM)
    }

    private fun playAlarmNotification() {
        val dismissIntent = PendingIntent.getBroadcast(
            this, 0, Intent(ACTION_DISMISS_ALARM),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_battery_alert)
            .setContentTitle("Unplug now — battery reached $highAlarmPct%")
            .setContentText("Charging past $highAlarmPct% nightly adds 30% extra wear per year.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .addAction(R.drawable.ic_close, "Dismiss alarm", dismissIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
        notificationManager.notify(NOTIF_ID_HIGH_ALARM, notif)
    }

    private fun fireLowAlarm(pct: Int, isFinalWarning: Boolean = false) {
        val msg = if (isFinalWarning)
            "Final warning! Deep discharge below 15% causes permanent capacity loss."
        else
            "Battery at $pct% — find a charger soon."
        val notif = NotificationCompat.Builder(this, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_battery_low)
            .setContentTitle("Battery at $pct%")
            .setContentText(msg)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIF_ID_LOW_ALARM, notif)
    }

    private fun fireHeatAlarm(tempC: Float) {
        val notif = NotificationCompat.Builder(this, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_thermostat)
            .setContentTitle("Battery temperature ${tempC.toInt()}°C")
            .setContentText("Consider removing your case or stopping fast charging.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIF_ID_HEAT_ALARM, notif)
    }

    private fun buildPersistentNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_battery_monitor)
            .setContentTitle("Battery Health Monitor")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()

    private fun updatePersistentNotification(pct: Int, tempC: Float, charging: Boolean) {
        val status = if (charging) "Charging" else "Discharging"
        notificationManager.notify(
            NOTIF_ID_FOREGROUND,
            buildPersistentNotification("$pct% · ${tempC.toInt()}°C · $status")
        )
    }

    private fun registerBatteryReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(batteryReceiver, filter)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val monitorChannel = NotificationChannel(
            CHANNEL_MONITOR, "Battery Monitor", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Persistent battery monitoring status" }

        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val alarmChannel = NotificationChannel(
            CHANNEL_ALARM, "Battery Alarms", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "80% charge alarm and low battery alerts"
            setSound(alarmUri, AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val ghostChannel = NotificationChannel(
            CHANNEL_GHOST, "Ghost Drain Alerts", NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Background drain detection alerts" }

        notificationManager.createNotificationChannels(
            listOf(monitorChannel, alarmChannel, ghostChannel)
        )
    }
}