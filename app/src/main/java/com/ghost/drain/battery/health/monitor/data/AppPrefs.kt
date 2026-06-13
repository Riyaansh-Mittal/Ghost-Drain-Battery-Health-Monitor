package com.ghost.drain.battery.health.monitor.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class AppPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("battery_prefs", Context.MODE_PRIVATE)

    // ─── Alarm settings ──────────────────────────────────────────────────
    var highAlarmEnabled: Boolean
        get() = prefs.getBoolean("high_alarm_enabled", true)
        set(v) = prefs.edit { putBoolean("high_alarm_enabled", v) }

    var highAlarmThreshold: Int
        get() = prefs.getInt("high_alarm_threshold", 80)
        set(v) = prefs.edit { putInt("high_alarm_threshold", v) }

    var lowAlarmEnabled: Boolean
        get() = prefs.getBoolean("low_alarm_enabled", true)
        set(v) = prefs.edit { putBoolean("low_alarm_enabled", v) }

    var lowAlarmThreshold: Int
        get() = prefs.getInt("low_alarm_threshold", 20)
        set(v) = prefs.edit { putInt("low_alarm_threshold", v) }

    var thermalAlarmEnabled: Boolean
        get() = prefs.getBoolean("thermal_alarm_enabled", true)
        set(v) = prefs.edit { putBoolean("thermal_alarm_enabled", v) }

    var alarmRepeatIntervalMs: Long
        get() = prefs.getLong("alarm_repeat_ms", 60_000L)
        set(v) = prefs.edit { putLong("alarm_repeat_ms", v) }

    var vibrationEnabled: Boolean
        get() = prefs.getBoolean("vibration_enabled", true)
        set(v) = prefs.edit { putBoolean("vibration_enabled", v) }

    // ─── Onboarding flags ────────────────────────────────────────────────
    var oemShown: Boolean
        get() = prefs.getBoolean("oem_shown", false)
        set(v) = prefs.edit { putBoolean("oem_shown", v) }

    var identitySelected: Boolean
        get() = prefs.getBoolean("identity_selected", false)
        set(v) = prefs.edit { putBoolean("identity_selected", v) }

    // 0=general, 1=gamer, 2=driver, 3=power user
    var userIdentity: Int
        get() = prefs.getInt("user_identity", 0)
        set(v) = prefs.edit { putInt("user_identity", v) }

    // ─── Ad safety ───────────────────────────────────────────────────────
    var installTimestamp: Long
        get() = prefs.getLong("install_timestamp", 0L)
        set(v) = prefs.edit { putLong("install_timestamp", v) }

    // ─── Streak tracking ─────────────────────────────────────────────────
    var alarmStreak: Int
        get() = prefs.getInt("alarm_streak", 0)
        set(v) = prefs.edit { putInt("alarm_streak", v) }

    var lastStreakDate: String
        get() = prefs.getString("last_streak_date", "") ?: ""
        set(v) = prefs.edit { putString("last_streak_date", v) }

    // ─── Alarm state (runtime) ───────────────────────────────────────────
    var highAlarmFiring: Boolean
        get() = prefs.getBoolean("high_alarm_firing", false)
        set(v) = prefs.edit { putBoolean("high_alarm_firing", v) }

    var lowAlarmFiring: Boolean
        get() = prefs.getBoolean("low_alarm_firing", false)
        set(v) = prefs.edit { putBoolean("low_alarm_firing", v) }

    // ─── Health / calibration ────────────────────────────────────────────
    var designCapacityMah: Int
        get() = prefs.getInt("design_capacity_mah", 0)
        set(v) = prefs.edit { putInt("design_capacity_mah", v) }

    var totalCycles: Float
        get() = prefs.getFloat("total_cycles", 0f)
        set(v) = prefs.edit { putFloat("total_cycles", v) }
}