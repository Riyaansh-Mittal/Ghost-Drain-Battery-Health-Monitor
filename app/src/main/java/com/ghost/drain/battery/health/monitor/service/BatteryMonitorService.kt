package com.ghost.drain.battery.health.monitor.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ghost.drain.battery.health.monitor.MainActivity
import com.ghost.drain.battery.health.monitor.R
import com.ghost.drain.battery.health.monitor.data.AlarmPreferences
import com.ghost.drain.battery.health.monitor.data.BatteryDataSource
import com.ghost.drain.battery.health.monitor.model.BatteryState
import com.ghost.drain.battery.health.monitor.repository.BatteryRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*
import android.widget.RemoteViews

class BatteryMonitorService : Service() {

    companion object {
        const val CHANNEL_MONITOR     = "battery_monitor_channel"
        const val CHANNEL_ALARM       = "battery_alarm"
        const val CHANNEL_OEM_WARNING = "battery_oem_warning"
        const val CHANNEL_STREAK      = "battery_streak"

        const val NOTIF_ID_PERSISTENT  = 1
        const val NOTIF_ID_ALARM       = 2
        const val NOTIF_ID_OEM_WARNING = 3
        const val NOTIF_ID_STREAK      = 4

        const val ACTION_STOP_ALARM = "ACTION_STOP_ALARM"
        const val ACTION_APP_OPENED = "ACTION_APP_OPENED"

        private const val TAG               = "BatteryMonitorService"
        private const val ALARM_REPEAT_COUNT  = 3
        private const val ALARM_REPEAT_GAP_MS = 500L
        private const val BURST_REPEAT_MAX    = 3
        private const val BURST_INTERVAL_MS   = 60_000L

        private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        fun buildStartIntent(context: Context) =
            Intent(context, BatteryMonitorService::class.java)

        fun buildStopAlarmIntent(context: Context) =
            Intent(context, BatteryMonitorService::class.java).apply {
                action = ACTION_STOP_ALARM
            }

        const val EXTRA_OPEN_ALARM_SCREEN = "open_alarm_screen"

        fun buildOpenAlarmScreenIntent(context: Context) =
            Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_OPEN_ALARM_SCREEN, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var repository:  BatteryRepository
    private lateinit var alarmPrefs:  AlarmPreferences

    private var mediaPlayer: MediaPlayer? = null
    private var repeatJob:   Job?         = null

    private var sessionStartTime:    Long? = null
    private var sessionStartPercent: Int   = 0
    private var wasChargingLastTick        = false

    private var highAlarmLastFiredAt     = 0L
    private var lowAlarmLastFiredAt      = 0L
    private var overheatAlarmLastFiredAt = 0L
    private var highAlarmBurstCount      = 0
    private var lowAlarmBurstCount       = 0
    private var overheatAlarmBurstCount  = 0

    private var highAlarmSnoozedForSession     = false
    private var overheatAlarmSnoozedForSession = false
    private var lowAlarmSnoozedAtPercent: Int? = null
    private var lowAlarmSnoozedAtTime: Long    = 0L

    private val powerSaveReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            serviceScope.launch { checkAndNotifyOptimizationStatus() }
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        repository = BatteryRepository(BatteryDataSource(applicationContext))
        alarmPrefs = AlarmPreferences(applicationContext)
        createMonitorChannel()
        createAlarmChannel()
        createOemWarningChannel()
        createStreakChannel()
        startForeground(
            NOTIF_ID_PERSISTENT,
            buildPersistentNotification("Starting…", "Initializing battery monitor")
        )
        registerReceiver(
            powerSaveReceiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        )
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_ALARM -> stopAlarmFully()
            ACTION_APP_OPENED -> stopAlarmFully()
        }
        startMonitoringLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        releaseMediaPlayer()
        runCatching { unregisterReceiver(powerSaveReceiver) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Monitor loop ───────────────────────────────────────────────────────────

    private fun startMonitoringLoop() {
        serviceScope.launch {
            try {
                repository.batteryState.collectLatest { state ->
                    Log.d(TAG, buildLogString(state))
                    updatePersistentNotification(state)
                    trackSession(state)
                    checkAlarms(state)
                    checkAndNotifyOptimizationStatus()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Monitor loop crashed — restarting in 5s", e)
                delay(5_000)
                startMonitoringLoop()
            }
        }
    }

    // ── Optimization status ────────────────────────────────────────────────────

    private var oemWarningSentThisSession = false
    private var oemWarningVisible         = false

    private data class AlarmSeverity(
        val emoji:   String,
        val color:   String,
        val urgency: String
    )

    private fun severityFor(percent: Int): AlarmSeverity = when {
        percent <= 9  -> AlarmSeverity("🔴", "#FF3B30", "Battery may die soon")
        percent <= 15 -> AlarmSeverity("🟠", "#FF6B00", "Plug in now")
        else          -> AlarmSeverity("🟡", "#FFB800", "Charge soon")
    }

    private fun checkAndNotifyOptimizationStatus() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val exemptedNow = OemBatteryOptimizationHelper.isExempted(this)

        if (exemptedNow) {
            if (oemWarningVisible) {
                nm.cancel(NOTIF_ID_OEM_WARNING)
                oemWarningVisible = false
            }
            return
        }

        if (!oemWarningVisible && !oemWarningSentThisSession) {
            val openAppPi = PendingIntent.getActivity(
                this, 0,
                buildOpenAlarmScreenIntent(this),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val oemColorString = "#EA580C" // Visible safety Orange
            val colorInt = android.graphics.Color.parseColor(oemColorString)

            val collapsedView = RemoteViews(packageName, R.layout.notif_alarm).apply {
                setTextViewText(R.id.notif_title, "⚠️ ALERTS WON'T WORK")
                setTextViewText(R.id.notif_body, "System optimization is blocking protection. Tap to fix.")
                setTextColor(R.id.notif_title, colorInt)
            }

            val expandedView = RemoteViews(packageName, R.layout.notif_alarm_expanded).apply {
                setTextViewText(R.id.notif_header_alert, "⚠️ ALERTS WON'T WORK")
                setTextViewText(R.id.notif_title, "Permission Problem")
                setTextViewText(R.id.notif_body, "Android is aggressively optimizing background services. Without explicit exemption, Ghost Drain cannot wake your phone to fire safety alerts or 80% unplug reminders.\n\nTap 'Fix Now' to grant unrestricted background execution.")
                setTextColor(R.id.notif_header_alert, colorInt)
            }

            val notif = NotificationCompat.Builder(this, CHANNEL_OEM_WARNING)
                .setSmallIcon(R.drawable.ic_battery_notification)
                .setLargeIcon(android.graphics.BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(collapsedView)
                .setCustomBigContentView(expandedView)
                .setColor(colorInt)
                .setColorized(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(openAppPi)
                .addAction(0, "🔧 Fix Now", openAppPi)
                .setAutoCancel(true)
                .setOngoing(false)
                .build()

            nm.notify(NOTIF_ID_OEM_WARNING, notif)
            oemWarningVisible         = true
            oemWarningSentThisSession = true
        }
    }

    // ── Session tracking ───────────────────────────────────────────────────────

    private fun trackSession(state: BatteryState) {
        if (state.isCharging && !wasChargingLastTick) {
            sessionStartTime    = System.currentTimeMillis()
            sessionStartPercent = state.percent
            highAlarmSnoozedForSession     = false
            overheatAlarmSnoozedForSession = false
            Log.d(TAG, "Charging session started at ${state.percent}%")
        }

        if (!state.isCharging && wasChargingLastTick && sessionStartTime != null) {
            val unplugPercent = state.percent
            val today         = DATE_FMT.format(Date())
            lowAlarmSnoozedAtPercent = null
            lowAlarmSnoozedAtTime    = 0L 
            Log.d(TAG, "Charging session ended at $unplugPercent%")
            sessionStartTime = null

            // Evaluate streak on every charger disconnect
            serviceScope.launch {
                val result = alarmPrefs.evaluateChargeSession(unplugPercent, today)
                handleStreakResult(result)
            }
        }

        wasChargingLastTick = state.isCharging
    }

    // ── Streak result handling ─────────────────────────────────────────────────

    private fun handleStreakResult(result: AlarmPreferences.StreakResult) {
        when (result) {
            is AlarmPreferences.StreakResult.Success -> {
                val milestone = milestoneFor(result.newStreak)
                if (milestone != null) {
                    postStreakNotification(
                        title = "🔥 ${result.newStreak}-Day Streak!",
                        body  = milestone,
                        color = "#34C759"
                    )
                }
            }
            is AlarmPreferences.StreakResult.ChallengeComplete -> {
                post30DayChallengeNotification(result.streak)
            }
            is AlarmPreferences.StreakResult.Failure -> {
                val prev = result.previousStreak
                if (prev > 0) {
                    val (title, body) = when {
                        prev >= 21 -> "So close." to "Your $prev-day streak ended. Ready for another run?"
                        prev >= 10 -> "Streak ended at $prev days." to "Your next streak can be even longer."
                        else       -> "Streak ended at $prev days." to "Let's start another one tonight."
                    }
                    postStreakNotification(title = title, body = body, color = "#FF6B00")
                }
            }
            // AlreadyCounted, AlreadyFailed, NoChange → silent, no notification
            else -> Unit
        }
    }

    private fun postStreakNotification(title: String, body: String, color: String) {
        val openPi = PendingIntent.getActivity(
            this, 20,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_STREAK)
            .setSmallIcon(R.drawable.ic_battery_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setColor(android.graphics.Color.parseColor(color))
            .setColorized(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openPi)
            .build()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID_STREAK, notif)
    }

    private fun post30DayChallengeNotification(streak: Int) {
        val openPi = PendingIntent.getActivity(
            this, 21,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_STREAK)
            .setSmallIcon(R.drawable.ic_battery_notification)
            .setContentTitle("🏆 30-Day Challenge Complete")
            .setContentText("You unplugged on time for 30 consecutive charging days.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle("🏆 30-Day Challenge Complete")
                    .bigText(
                        "Okay, You Win 😅\n\n" +
                                "You've followed healthy charging habits for 30 days straight.\n" +
                                "You probably don't need Ghost Drain anymore."
                    )
            )
            .setColor(android.graphics.Color.parseColor("#FFD700"))
            .setColorized(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPi)
            .addAction(0, "Keep Protecting Battery", openPi)
            .build()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID_STREAK, notif)
    }

    private fun milestoneFor(streak: Int): String? = when (streak) {
        1    -> "Starter — great first step!"
        3    -> "Building Habit — 3 days strong"
        7    -> "One Week Complete 🎯"
        14   -> "Halfway to Mastery"
        21   -> "Battery Guardian 🛡️"
        25   -> "Final Stretch — 5 days to go"
        29   -> "One Day Remaining — almost there!"
        else -> null
    }

    // ── Alarm logic ────────────────────────────────────────────────────────────

    private suspend fun checkAlarms(state: BatteryState) {
        val highEnabled = alarmPrefs.highAlarmEnabled.first()
        val highPct     = alarmPrefs.highAlarmPercent.first()
        val lowEnabled  = alarmPrefs.lowAlarmEnabled.first()
        val lowPct      = alarmPrefs.lowAlarmPercent.first()
        val heatEnabled = alarmPrefs.overheatEnabled.first()
        val heatThresh  = alarmPrefs.overheatThreshold.first()
        val now         = System.currentTimeMillis()
        val today       = DATE_FMT.format(Date(now))

        // ── High alarm ────────────────────────────────────────────────────────
        val highCondition = highEnabled && state.isCharging && state.percent >= highPct &&
            !highAlarmSnoozedForSession

        if (highCondition &&
            (now - highAlarmLastFiredAt) >= BURST_INTERVAL_MS &&
            highAlarmBurstCount < BURST_REPEAT_MAX) {
            fireAlarm(
                title         = "🔴 ${state.percent}% — Unplug Now",
                body          = "Battery health drops above $highPct%",
                rawResId      = R.raw.alarm_high,
                minutesLeft   = state.minutesToFull,
                severityColor = "#FF3B30"
            )
            highAlarmLastFiredAt = now
            highAlarmBurstCount++
            if (highAlarmBurstCount >= BURST_REPEAT_MAX) stopAudioOnly()
        }

        // ── Low alarm ─────────────────────────────────────────────────────────
        val lowSnoozePassed = if (lowAlarmSnoozedAtPercent != null) {
            val percentDropped = (lowAlarmSnoozedAtPercent!! - state.percent) >= 3
            val timePassed     = (now - lowAlarmSnoozedAtTime) >= 15 * 60 * 1000L
            percentDropped || timePassed
        } else true

        val lowCondition = lowEnabled && !state.isCharging && state.percent <= lowPct &&
            lowSnoozePassed

        if (state.isCharging) {
            lowAlarmLastFiredAt = 0L
            lowAlarmBurstCount  = 0
        } else if (lowCondition &&
            (now - lowAlarmLastFiredAt) >= BURST_INTERVAL_MS &&
            lowAlarmBurstCount < BURST_REPEAT_MAX) {
            val mins = state.minutesToEmpty
            val sev  = severityFor(state.percent)
            val timeText = if (mins != null && mins > 0) {
                val h = mins / 60; val m = mins % 60
                if (h > 0) "~${h}h ${m}m left" else "~${m} min left"
            } else "Low battery"
            fireAlarm(
                title         = "${sev.emoji} ${state.percent}% Battery Remaining",
                body          = "$timeText • ${sev.urgency}",
                rawResId      = R.raw.alarm_low,
                minutesLeft   = mins,
                severityColor = sev.color
            )
            lowAlarmLastFiredAt = now
            lowAlarmBurstCount++
            if (lowAlarmBurstCount >= BURST_REPEAT_MAX) stopAudioOnly()
        }

        // ── Overheat alarm ────────────────────────────────────────────────────
        val heatCondition = heatEnabled && state.temperatureCelsius >= heatThresh &&
            !overheatAlarmSnoozedForSession

        if (heatCondition &&
            (now - overheatAlarmLastFiredAt) >= BURST_INTERVAL_MS &&
            overheatAlarmBurstCount < BURST_REPEAT_MAX) {
            fireAlarm(
                title         = "🔴 ${"%.0f".format(state.temperatureCelsius)}°C — Overheating",
                body          = "Unplug and let your device cool down",
                rawResId      = R.raw.alarm_high,
                minutesLeft   = null,
                severityColor = "#FF3B30"
            )
            overheatAlarmLastFiredAt = now
            overheatAlarmBurstCount++
            if (overheatAlarmBurstCount >= BURST_REPEAT_MAX) stopAudioOnly()
        }
    }

    // ── Fire ───────────────────────────────────────────────────────────────────

    private suspend fun fireAlarm(
        title: String,
        body: String,
        rawResId: Int,
        minutesLeft: Int?     = null,
        severityColor: String = "#FF3B30"
    ) {
        playAlarmRepeated(rawResId)
        showAlarmNotification(title, body, severityColor)
    }

    // ── Sound ──────────────────────────────────────────────────────────────────

    private fun playAlarmRepeated(rawResId: Int) {
        repeatJob?.cancel()
        repeatJob = serviceScope.launch {
            repeat(ALARM_REPEAT_COUNT) { index ->
                if (!isActive) return@launch
                playOnce(rawResId)
                while (isActive && mediaPlayer?.isPlaying == true) delay(50)
                if (isActive && index < ALARM_REPEAT_COUNT - 1) delay(ALARM_REPEAT_GAP_MS)
            }
            releaseMediaPlayer()
        }
    }

    private fun playOnce(rawResId: Int) {
        releaseMediaPlayer()
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = android.media.AudioFocusRequest.Builder(
                    android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                ).setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                ).build()
                audioManager.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    android.media.AudioManager.STREAM_ALARM,
                    android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
            mediaPlayer = MediaPlayer().apply {
                val afd = resources.openRawResourceFd(rawResId)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setVolume(1.0f, 1.0f)
                isLooping = false
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaPlayer failed to start", e)
        }
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.runCatching { if (isPlaying) stop(); reset(); release() }
        mediaPlayer = null
    }

    // ── Vibration ──────────────────────────────────────────────────────────────

    private fun vibrateDevice() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else
            @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val pattern = longArrayOf(0, 400, 200, 400, 200, 400)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        else
            @Suppress("DEPRECATION") vibrator.vibrate(pattern, -1)
    }

    // ── Alarm notification ─────────────────────────────────────────────────────

    private fun showAlarmNotification(title: String, body: String, severityColor: String = "#FF3B30") {
        val stopPi = PendingIntent.getService(
            this, 0,
            buildStopAlarmIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openHomePi = PendingIntent.getActivity(
            this, 10,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Parse system metrics dynamically
        val isHighAlarm = title.contains("Unplug Now", ignoreCase = true) || title.contains("80%", ignoreCase = true)
        val isOverheat  = title.contains("Overheating", ignoreCase = true) || title.contains("Hot", ignoreCase = true)
        val isLowAlarm   = title.contains("Low", ignoreCase = true) || title.contains("Battery Remaining", ignoreCase = true) || title.contains("%", ignoreCase = true)

        val collapsedTitleText: String
        val collapsedBodyText: String

        val expandedHeaderAlertText: String
        val expandedTitleText: String
        val expandedBodyText: String
        val finalColorString: String

        when {
            isHighAlarm -> {
                collapsedTitleText = "🟢 UNPLUG NOW"
                collapsedBodyText  = "Battery reached healthy 80% limit."

                expandedHeaderAlertText = "🟢 CHARGE LIMIT REACHED"
                expandedTitleText  = "Unplug the Charger"
                expandedBodyText   = "Your device has reached 80%.\n\nKeeping your battery level near or below 80% significantly avoids thermal stress and extends your hardware lifecycle."
                finalColorString   = "#22C55E" // Mid Emerald Green
            }
            isOverheat -> {
                collapsedTitleText = "🔥 TEMPERATURE CRITICAL"
                collapsedBodyText  = "Battery is running hot! Unplug now."

                expandedHeaderAlertText = "🔥 THERMAL ALERT"
                expandedTitleText  = "Cool Down Hardware"
                expandedBodyText   = "Excessive high temperatures accelerate irreversible battery degradation.\n\nPlease stop heavy apps, unplug the charger, and allow the device to cool off."
                finalColorString   = "#EF4444" // Deep warning Red
            }
            isLowAlarm -> {
                // Safely scrub original title to preserve exact runtime level numbers
                val currentPct = title.substringBefore("%").replace(Regex("[^0-9]"), "").trim().ifEmpty { "20" }

                collapsedTitleText = "🔋 $currentPct% Battery Remaining"
                collapsedBodyText  = "Connect your charger soon."

                expandedHeaderAlertText = "⚠️ LOW ENERGY LEVEL"
                expandedTitleText  = "Plug in Charger"
                expandedBodyText   = "Your phone has dropped to $currentPct%.\n\nDeep discharging below 15% stresses chemical cell bounds. Connect to power soon to keep your device running smoothly without interruption."
                finalColorString = "#F59E0B" // High Contrast Amber Yellow
            }
            else -> {
                collapsedTitleText = title
                collapsedBodyText  = body
                expandedHeaderAlertText = "⚠️ SYSTEM NOTICE"
                expandedTitleText  = title
                expandedBodyText   = body
                finalColorString   = severityColor
            }
        }

        val colorInt = android.graphics.Color.parseColor(finalColorString)

        val collapsedView = RemoteViews(packageName, R.layout.notif_alarm).apply {
            setTextViewText(R.id.notif_title, collapsedTitleText)
            setTextViewText(R.id.notif_body, collapsedBodyText)
            setTextColor(R.id.notif_title, colorInt)
        }

        val expandedView = RemoteViews(packageName, R.layout.notif_alarm_expanded).apply {
            setTextViewText(R.id.notif_header_alert, expandedHeaderAlertText)
            setTextViewText(R.id.notif_title, expandedTitleText)
            setTextViewText(R.id.notif_body, expandedBodyText)
            setTextColor(R.id.notif_header_alert, colorInt)
        }

        val notif = NotificationCompat.Builder(this, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_battery_notification)
            .setLargeIcon(android.graphics.BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsedView)
            .setCustomBigContentView(expandedView)
            .setColor(colorInt)
            .setColorized(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openHomePi)
            .setDeleteIntent(stopPi)
            .addAction(0, "⛔ Stop", stopPi)
            .addAction(0, "🔋 Open App", openHomePi)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()

        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID_ALARM, notif)
    }

    private fun stopAlarmFully() {
        repeatJob?.cancel()
        repeatJob = null
        releaseMediaPlayer()

        highAlarmSnoozedForSession     = true
        overheatAlarmSnoozedForSession = true

        lowAlarmSnoozedAtPercent = repository.batteryState.value.percent
        lowAlarmSnoozedAtTime    = System.currentTimeMillis()

        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(NOTIF_ID_ALARM)
        Log.d(TAG, "Alarm stopped and muted contextually by user")
    }

    private fun stopAudioOnly() {
        repeatJob?.cancel()
        repeatJob = null
        releaseMediaPlayer()
        Log.d(TAG, "Escalation: audio stopped, notification remains")
    }

    // ── Persistent notification ────────────────────────────────────────────────

    private fun buildLogString(s: BatteryState) =
        "Battery: ${s.percent}% | ${s.chargingLabel} | " +
                "Temp: ${s.temperatureCelsius}°C | ${s.currentMa}mA | " +
                "${"%.1f".format(s.wattage)}W | ${s.chargerVerdict}"

    private fun updatePersistentNotification(state: BatteryState) {
        val title = "${state.percent}% · ${state.chargingLabel}"
        val body  = "${"%.1f".format(state.temperatureCelsius)}°C · ${state.currentMa}mA · ${"%.1f".format(state.wattage)}W"
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID_PERSISTENT, buildPersistentNotification(title, body))
    }

    private fun buildPersistentNotification(title: String, body: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_MONITOR)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_battery_notification)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    // ── Notification channels ──────────────────────────────────────────────────

    private fun createMonitorChannel() {
        NotificationChannel(CHANNEL_MONITOR, "Battery Monitor", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Live battery status in the status bar"
            setShowBadge(false)
            setSound(null, null)
        }.also { (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(it) }
    }

    private fun createAlarmChannel() {
        NotificationChannel(CHANNEL_ALARM, "Battery Alarm", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Fires when battery is too high, too low, or overheating"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }.also { (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(it) }
    }

    private fun createOemWarningChannel() {
        NotificationChannel(
            CHANNEL_OEM_WARNING,
            "Background Access Warning",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Warns when battery optimization is blocking background alarms"
            setSound(
                android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()
            )
            enableVibration(true)
            vibrationPattern     = longArrayOf(0, 300, 200, 300)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }.also {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(it)
        }
    }

    private fun createStreakChannel() {
        NotificationChannel(
            CHANNEL_STREAK,
            "Streak Updates",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Streak milestones, failures, and the 30-day challenge"
            setSound(
                android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 200, 100, 200)
        }.also {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(it)
        }
    }
}