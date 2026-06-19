package com.ghost.drain.battery.health.monitor.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.ghost.drain.battery.health.monitor.model.BatteryState
import com.ghost.drain.battery.health.monitor.model.ChargerVerdict
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.abs
import kotlin.math.sqrt

class BatteryDataSource(private val context: Context) {

    private val batteryManager =
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    // Rolling buffer for counterfeit charger detection (last 60 readings = 60 seconds)
    private val currentMaHistory = ArrayDeque<Int>(60)

    /**
     * Emits a fresh BatteryState every [intervalMs] milliseconds.
     * Collect this in BatteryRepository.
     */
    fun batteryStateFlow(intervalMs: Long = 5_000L): Flow<BatteryState> = flow {
        while (true) {
            emit(readBatteryState())
            delay(intervalMs)
        }
    }

    fun readBatteryState(): BatteryState {
        val stickyIntent: Intent? = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val percent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .coerceIn(0, 100)

        val status = stickyIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        val isFull = status == BatteryManager.BATTERY_STATUS_FULL

        val plugged = stickyIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val isPluggedUsb = plugged == BatteryManager.BATTERY_PLUGGED_USB
        val isPluggedAc = plugged == BatteryManager.BATTERY_PLUGGED_AC
        val isPluggedWireless = plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS

        // Current: positive when charging, negative when draining (microamperes → mA)
        val currentMicroA = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentMa = currentMicroA / 1000

        val voltageMicroV = stickyIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val voltageMv = voltageMicroV  // EXTRA_VOLTAGE is already in mV on most devices

        // Wattage = (|mA| * mV) / 1,000,000 → watts
        val wattage = if (voltageMv > 0) {
            (abs(currentMa).toFloat() * voltageMv.toFloat()) / 1_000_000f
        } else 0f

        // Net drain (positive = charger delivering more than screen is consuming)
        val netDrainMa = currentMa // Already signed correctly from BatteryManager

        val tempTenths = stickyIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempCelsius = tempTenths / 10f
        val tempFahrenheit = tempCelsius * 9f / 5f + 32f

        // Time estimates from BatteryManager (API 28+)
        val chargeTimeMs = batteryManager.computeChargeTimeRemaining()
        val minutesToFull = if (isCharging && chargeTimeMs > 0) (chargeTimeMs / 60_000).toInt() else null
        val minutesToEmpty: Int? = null // Computed via rolling average in repository

        val systemHealthConstant = stickyIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0) ?: 0
        val systemHealthPercent = mapHealthConstantToPercent(systemHealthConstant)

        // Counterfeit detection
        updateCurrentHistory(abs(currentMa))
        val chargerVerdict = computeChargerVerdict(isCharging, wattage, currentMaHistory)

        val chargingLabel = buildChargingLabel(isCharging, isFull, wattage, isPluggedUsb, isPluggedAc, isPluggedWireless)

        return BatteryState(
            percent = percent,
            isCharging = isCharging,
            isPluggedUsb = isPluggedUsb,
            isPluggedAc = isPluggedAc,
            isPluggedWireless = isPluggedWireless,
            isFull = isFull,
            currentMa = currentMa,
            voltageMv = voltageMv,
            wattage = wattage,
            netDrainMa = netDrainMa,
            temperatureCelsius = tempCelsius,
            temperatureFahrenheit = tempFahrenheit,
            minutesToFull = minutesToFull,
            minutesToEmpty = minutesToEmpty,
            systemHealthPercent = systemHealthPercent,
            chargerVerdict = chargerVerdict,
            chargingLabel = chargingLabel,
            timestampMs = System.currentTimeMillis()
        )
    }

    private fun updateCurrentHistory(absCurrentMa: Int) {
        if (currentMaHistory.size >= 60) currentMaHistory.removeFirst()
        currentMaHistory.addLast(absCurrentMa)
    }

    private fun computeChargerVerdict(
        isCharging: Boolean,
        wattage: Float,
        history: ArrayDeque<Int>
    ): ChargerVerdict {
        if (!isCharging) return ChargerVerdict.UNKNOWN
        if (history.size >= 60) {
            val mean = history.average()
            val stdDev = sqrt(history.map { (it - mean) * (it - mean) }.average())
            if (stdDev > 200) return ChargerVerdict.COUNTERFEIT
        }
        return when {
            wattage >= 30f -> ChargerVerdict.SUPER_FAST
            wattage >= 15f -> ChargerVerdict.FAST
            wattage >= 5f  -> ChargerVerdict.STANDARD
            else           -> ChargerVerdict.SLOW
        }
    }

    private fun buildChargingLabel(
        isCharging: Boolean,
        isFull: Boolean,
        wattage: Float,
        usb: Boolean,
        ac: Boolean,
        wireless: Boolean
    ): String = when {
        isFull      -> "Full — great job unplugging!"
        isCharging && wattage >= 15f -> "Fast charging — ${String.format("%.0f", wattage)}W"
        isCharging && wattage >= 5f  -> "Charging — ${String.format("%.0f", wattage)}W"
        isCharging && usb            -> "Charging via USB — ${String.format("%.0f", wattage)}W"
        isCharging                   -> "Charging — ${String.format("%.0f", wattage)}W"
        else                         -> "Discharging"
    }

    private fun mapHealthConstantToPercent(constant: Int): Int = when (constant) {
        BatteryManager.BATTERY_HEALTH_GOOD          -> 95
        BatteryManager.BATTERY_HEALTH_OVERHEAT      -> 60
        BatteryManager.BATTERY_HEALTH_DEAD          -> 0
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE  -> 50
        BatteryManager.BATTERY_HEALTH_COLD          -> 70
        else                                         -> 80
    }
}