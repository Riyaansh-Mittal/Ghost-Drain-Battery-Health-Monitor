package com.ghost.drain.battery.health.monitor.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ghost.drain.battery.health.monitor.BatteryApp
import com.ghost.drain.battery.health.monitor.MainActivity
import com.ghost.drain.battery.health.monitor.R
import com.ghost.drain.battery.health.monitor.data.AppDatabase
import com.ghost.drain.battery.health.monitor.data.AppPrefs
import com.ghost.drain.battery.health.monitor.data.BatterySession
import com.ghost.drain.battery.health.monitor.util.BatteryStatsReader
import kotlinx.coroutines.*
import java.time.LocalDate

class BatteryMonitorService : Service() {

    companion object {
        const val NOTIF_ID_PERSISTENT = 1
        const val NOTIF_ID_ALARM      = 2
        const val NOTIF_ID_GHOST      = 3
        const val ACTION_DISMISS_HIGH = "dismiss_high_alarm"
        const val ACTION_DISMISS_LOW  = "dismiss_low_alarm"

        fun start(context: Context) {
            val intent = Intent(context, BatteryMonitorService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BatteryMonitorService::class.java))
        }
    }

    private lateinit var prefs: AppPrefs
    private lateinit var soundPlayer: AlarmSoundPlayer
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Alarm repeat handler
    private val alarmHandler = Handler(Looper.getMainLooper())
    private var alarmRunnable: Runnable? = null

    // Ghost drain tracking
    private var screenOffTimestamp = 0L
    private var screenOffStartPercent = 0

    // Session tracking
    private var currentSessionId = 0L
    private var sessionStartPercent = 0
    private var sessionStartTime = 0L
    private var isChargingSession = false
    private var peakTempThisSession = 0f
    private var maxCurrentThisSession = 0

    // Thermal alarm — fires once per event
    private var thermalAlarmFiredThisEvent = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> onBatteryChanged(intent)
                Intent.ACTION_POWER_CONNECTED -> onPowerConnected()
                Intent.ACTION_POWER_DISCONNECTED -> onPowerDisconnected()
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
                Intent.ACTION_SCREEN_ON -> onScreenOn()
            }
        }
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        prefs = AppPrefs(this)
        soundPlayer = AlarmSoundPlayer(this)
        startForeground(NOTIF_ID_PERSISTENT, buildPersistentNotification("Battery", "--", "--"))
        registerBatteryReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS_HIGH -> dismissHighAlarm()
            ACTION_DISMISS_LOW -> dismissLowAlarm()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batteryReceiver)
        stopAlarmLoop()
        soundPlayer.stop()
        serviceScope.cancel()
    }

    // ─── Receiver Registration ───────────────────────────────────────────

    private fun registerBatteryReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        // ACTION_BATTERY_CHANGED is sticky — registerReceiver returns current state immediately
        registerReceiver(batteryReceiver, filter)
    }

    // ─── Battery Events ──────────────────────────────────────────────────

    private fun onBatteryChanged(intent: Intent) {
        val stats = BatteryStatsReader.read(intent) ?: return

        // Update persistent notification
        val chargingText = if (stats.isCharging) stats.formattedWatts else ""
        updatePersistentNotification(
            percent = stats.percent.toString(),
            temp = stats.formattedTemp,
            extra = chargingText
        )

        // Track session peak values
        if (stats.tempCelsius > peakTempThisSession) peakTempThisSession = stats.tempCelsius
        val absCurrent = Math.abs(stats.currentMa)
        if (absCurrent > maxCurrentThisSession) maxCurrentThisSession = absCurrent

        // ── High charge alarm ─────────────────────────────────────────
        if (prefs.highAlarmEnabled &&
            stats.isCharging &&
            stats.percent >= prefs.highAlarmThreshold &&
            !prefs.highAlarmFiring
        ) {
            prefs.highAlarmFiring = true
            startHighAlarmLoop(stats.percent)
        }

        // ── Low discharge alarm ───────────────────────────────────────
        if (prefs.lowAlarmEnabled &&
            !stats.isCharging &&
            stats.percent <= prefs.lowAlarmThreshold &&
            !prefs.lowAlarmFiring
        ) {
            prefs.lowAlarmFiring = true
            fireOneShotAlarm(
                title = "Battery at ${stats.percent}% — find a charger",
                body = "Deep discharge below 15% causes permanent capacity loss.",
                alarmType = "low"
            )
            // Fire again at threshold - 5 as final warning
            if (stats.percent <= prefs.lowAlarmThreshold - 5) {
                fireOneShotAlarm(
                    title = "Critical: ${stats.percent}% — plug in now",
                    body = "Battery at critical level.",
                    alarmType = "low"
                )
            }
        }

        // ── Thermal alarm ─────────────────────────────────────────────
        if (prefs.thermalAlarmEnabled && stats.tempCelsius >= 42f) {
            if (!thermalAlarmFiredThisEvent) {
                thermalAlarmFiredThisEvent = true
                fireOneShotAlarm(
                    title = "Battery temperature ${stats.formattedTemp}",
                    body = "Remove your case, stop fast charging, move to a cooler surface.",
                    alarmType = "high"
                )
            }
        } else if (stats.tempCelsius < 40f) {
            // Reset thermal event so it can fire again if temp rises again
            thermalAlarmFiredThisEvent = false
        }
    }

    private fun onPowerConnected() {
        // Reset low alarm so it can fire again next discharge cycle
        prefs.lowAlarmFiring = false
        // Start a new charging session
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val percent = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        startNewSession(percent = percent, isCharging = true)
    }

    private fun onPowerDisconnected() {
        // ── STOP HIGH ALARM — cable removed ──────────────────────────
        if (prefs.highAlarmFiring) {
            dismissHighAlarm()
            // Update streak
            updateStreak()
            // Show review prompt (max once per 30 days — implement later)
        }
        // Reset high alarm for next charge cycle
        prefs.highAlarmFiring = false

        // Close the charging session
        closeCurrentSession(isCharging = true)

        // Start a new discharge session
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val percent = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        startNewSession(percent = percent, isCharging = false)
    }

    private fun onScreenOff() {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        screenOffStartPercent = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        screenOffTimestamp = System.currentTimeMillis()
    }

    private fun onScreenOn() {
        if (screenOffTimestamp == 0L) return
        val elapsedHours = (System.currentTimeMillis() - screenOffTimestamp) / 3_600_000f
        if (elapsedHours < 0.5f) return  // Less than 30 min — not worth measuring

        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val currentPercent = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val isCharging = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0

        if (!isCharging) {
            val drainPercent = screenOffStartPercent - currentPercent
            val drainPerHour = if (elapsedHours > 0) drainPercent / elapsedHours else 0f

            // Ghost drain threshold: more than 3% per hour with screen off
            if (drainPerHour >= 3f && elapsedHours >= 0.5f) {
                fireGhostDrainNotification(drainPerHour)
            }
        }
        screenOffTimestamp = 0L
    }

    // ─── Alarm Loop ───────────────────────────────────────────────────────

    private fun startHighAlarmLoop(percent: Int) {
        stopAlarmLoop()
        fireHighAlarmNotification(percent)
        soundPlayer.start("high", prefs.vibrationEnabled)

        val runnable = object : Runnable {
            override fun run() {
                if (prefs.highAlarmFiring) {
                    soundPlayer.start("high", prefs.vibrationEnabled)
                    alarmHandler.postDelayed(this, prefs.alarmRepeatIntervalMs)
                }
            }
        }
        alarmRunnable = runnable
        alarmHandler.postDelayed(runnable, prefs.alarmRepeatIntervalMs)
    }

    private fun stopAlarmLoop() {
        alarmRunnable?.let { alarmHandler.removeCallbacks(it) }
        alarmRunnable = null
        soundPlayer.stop()
    }

    private fun dismissHighAlarm() {
        prefs.highAlarmFiring = false
        stopAlarmLoop()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIF_ID_ALARM)
    }

    private fun dismissLowAlarm() {
        prefs.lowAlarmFiring = false
        stopAlarmLoop()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIF_ID_ALARM)
    }

    // ─── Session Tracking ─────────────────────────────────────────────────

    private fun startNewSession(percent: Int, isCharging: Boolean) {
        sessionStartPercent = percent
        sessionStartTime = System.currentTimeMillis()
        isChargingSession = isCharging
        peakTempThisSession = 0f
        maxCurrentThisSession = 0

        serviceScope.launch {
            val session = BatterySession(
                startTimestamp = sessionStartTime,
                startPercent = percent,
                isCharging = isCharging
            )
            currentSessionId = AppDatabase.get(applicationContext).sessionDao().insert(session)
        }
    }

    private fun closeCurrentSession(isCharging: Boolean) {
        if (currentSessionId == 0L) return
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val endPercent = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val range = Math.abs(endPercent - sessionStartPercent)
        val isQualifying = isCharging && range >= 15  // Health calibration threshold

        serviceScope.launch {
            val dao = AppDatabase.get(applicationContext).sessionDao()
            val existing = dao.getRecentSessions().firstOrNull { it.id == currentSessionId }
            existing?.let {
                dao.update(
                    it.copy(
                        endTimestamp = System.currentTimeMillis(),
                        endPercent = endPercent,
                        peakTempCelsius = peakTempThisSession,
                        maxCurrentMa = maxCurrentThisSession,
                        isQualifying = isQualifying
                    )
                )
            }
        }
        currentSessionId = 0L
    }

    // ─── Streak ───────────────────────────────────────────────────────────

    private fun updateStreak() {
        val today = LocalDate.now().toString()
        if (prefs.lastStreakDate != today) {
            prefs.alarmStreak = prefs.alarmStreak + 1
            prefs.lastStreakDate = today
        }
        // 30-day streak → "Delete My App" viral notification
        if (prefs.alarmStreak >= 30) {
            fireDeleteMyAppNotification()
            prefs.alarmStreak = 0  // Reset so it can fire again
        }
    }

    // ─── Notifications ────────────────────────────────────────────────────

    private fun buildPersistentNotification(
        percent: String, temp: String, extra: String
    ): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, BatteryApp.CHANNEL_PERSISTENT)
            .setSmallIcon(R.drawable.ic_battery_status)  // add this drawable in Week 2
            .setContentTitle("Battery $percent%  $temp")
            .setContentText(extra.ifBlank { "Monitoring active" })
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updatePersistentNotification(percent: String, temp: String, extra: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID_PERSISTENT, buildPersistentNotification(percent, temp, extra))
    }

    private fun fireHighAlarmNotification(percent: Int) {
        val dismissIntent = PendingIntent.getService(
            this, 1,
            Intent(this, BatteryMonitorService::class.java).apply {
                action = ACTION_DISMISS_HIGH
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, BatteryApp.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_battery_alert)
            .setContentTitle("Unplug now — battery reached $percent%")
            .setContentText("Charging past 80% nightly causes 30% extra wear per year.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Charging past 80% nightly causes 30% extra wear per year. Your battery will last longer if you unplug now.")
            )
            .addAction(0, "Dismiss alarm", dismissIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .build()

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID_ALARM, notif)
    }

    private fun fireOneShotAlarm(title: String, body: String, alarmType: String) {
        soundPlayer.start(alarmType, prefs.vibrationEnabled)
        val notif = NotificationCompat.Builder(this, BatteryApp.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_battery_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID_ALARM, notif)
    }

    private fun fireGhostDrainNotification(drainPerHour: Float) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(this, BatteryApp.CHANNEL_SILENT)
            .setSmallIcon(R.drawable.ic_battery_alert)
            .setContentTitle("Ghost drain detected")
            .setContentText("Your phone lost %.1f%%/hr with screen off. Expected under 1%. Something is preventing deep sleep.".format(drainPerHour))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_ID_GHOST, notif)
    }

    private fun fireDeleteMyAppNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(this, BatteryApp.CHANNEL_SILENT)
            .setSmallIcon(R.drawable.ic_battery_status)
            .setContentTitle("Your charging habits are perfect.")
            .setContentText("You genuinely don't need us right now. Delete this app to save 4MB.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        nm.notify(99, notif)
    }
}