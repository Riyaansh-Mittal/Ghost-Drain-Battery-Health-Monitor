package com.ghost.drain.battery.health.monitor.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build

object BatteryStatsReader {

    data class BatterySnapshot(
        val levelPct: Int,
        val currentNowMa: Float,      // mA (positive = charging on most OEMs)
        val voltageV: Float,
        val temperatureC: Float,
        val temperatureF: Float,
        val wattage: Float,           // W = (|mA| × V) / 1000
        val chargeCounterMah: Int,    // mAh remaining
        val isCharging: Boolean,
        val isUsbCharging: Boolean,
        val isAcCharging: Boolean,
        val chargeStatusLabel: String // "Fast charging 18W", "Slow 2W", "Discharging"
    )

    fun readSnapshot(context: Context): BatterySnapshot {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        val intent: Intent? = context.registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val level  = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale  = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct    = if (scale > 0) (level * 100 / scale) else 0

        val rawTemp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC   = rawTemp / 10f
        val tempF   = tempC * 9f / 5f + 32f

        val voltMv  = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val voltV   = voltMv / 1000f

        val currentUa = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentMa = currentUa / 1000f

        val chargeCounter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            .coerceAtLeast(0)

        val status    = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged   = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                         status == BatteryManager.BATTERY_STATUS_FULL
        val isUsb = plugged == BatteryManager.BATTERY_PLUGGED_USB
        val isAc  = plugged == BatteryManager.BATTERY_PLUGGED_AC

        val watts = if (voltV > 0) (Math.abs(currentMa) * voltV) / 1000f else 0f

        val label = when {
            !isCharging      -> "Discharging"
            watts >= 15f     -> "Fast charging ${watts.toInt()}W"
            watts in 7f..15f -> "Standard ${watts.toInt()}W"
            watts > 0f       -> "Slow ${watts.toInt()}W"
            else             -> "Charging"
        }

        return BatterySnapshot(
            levelPct          = pct,
            currentNowMa      = currentMa,
            voltageV          = voltV,
            temperatureC      = tempC,
            temperatureF      = tempF,
            wattage           = watts,
            chargeCounterMah  = chargeCounter / 1000,
            isCharging        = isCharging,
            isUsbCharging     = isUsb,
            isAcCharging      = isAc,
            chargeStatusLabel = label
        )
    }
}