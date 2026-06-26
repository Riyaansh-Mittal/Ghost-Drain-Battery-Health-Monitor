package com.ghost.drain.battery.health.monitor.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.ghost.drain.battery.health.monitor.model.BatteryState
import com.ghost.drain.battery.health.monitor.model.ChargerVerdict
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlin.math.abs

class BatteryDataSource(private val context: Context) {

    private val batteryManager =
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    // --- Charging speed tier: smoothing + hysteresis state ---
    // Raw currentMa/wattage stay untouched everywhere else (Screen 3's live chart wants
    // the real jitter). This state exists ONLY to decide the displayed speed tier, and
    // is the SINGLE shared source for both the verdict label and the wattage tier text.
    //
    // IMPORTANT: window is defined in TIME (ms), not reading COUNT. With 1s polling
    // (down from 5s), a fixed reading-count window would silently shrink from a 30s
    // real-world smoothing window down to ~6s, reintroducing noise sensitivity. Storing
    // (timestamp, wattage) pairs and pruning by age keeps the smoothing window correct
    // regardless of poll interval.
    private data class TimedWattage(val timestampMs: Long, val watts: Float)
    private val wattageHistoryForTier = ArrayDeque<TimedWattage>()
    private val smoothingWindowMs = 30_000L

    private var lastCommittedTier: ChargerVerdict = ChargerVerdict.UNKNOWN
    private var pendingTier: ChargerVerdict? = null
    private var pendingTierSinceMs: Long = 0L

    // Tracks recent tier commits (timestamp only) to detect genuine flickering — a
    // charger swinging wide enough that BOTH sides individually look unambiguous
    // (e.g. 3W <-> 7W, straddling the fast-attack zone on each side) would otherwise
    // slip past the boundary-distance check below and still flicker every poll.
    private val recentTierChangeTimestamps = ArrayDeque<Long>()
    private val flickerWindowMs = 20_000L
    private val flickerCountThreshold = 2 // 2+ tier changes within the window = treat as noisy

    // A NEW charging session legitimately jumps through several tiers fast as PPS
    // ramps up from 0 to nominal wattage in the first few seconds after plug-in.
    // That's expected, real behavior — not the PPS-jitter problem flicker-detection
    // exists to catch. Without this, that initial ramp could itself trip the flicker
    // detector, forcing the FIRST real post-ramp transition into 8s+ hysteresis it
    // didn't need — which is what was likely producing the "10+ seconds" lag even
    // after fast-attack was added. So: ignore flicker tracking for this long after
    // a charging session starts.
    private var sessionStartMs: Long? = null
    private val flickerGracePeriodMs = 10_000L

    private val tierHysteresisMs = 8_000L

    /**
     * Emits a fresh BatteryState every [intervalMs] milliseconds, AND immediately whenever
     * the charger is physically plugged or unplugged (via ACTION_POWER_CONNECTED /
     * ACTION_POWER_DISCONNECTED), so the UI doesn't wait up to [intervalMs] to notice —
     * those broadcasts fire instantly from the OS, independent of our poll loop.
     *
     * Default interval is 1s (down from 5s) to match the update cadence of reference
     * apps like AccuBattery. Reading BatteryManager properties is cheap — this is not
     * GPS/sensor-grade polling, so 1s does not meaningfully affect battery drain.
     */
    fun batteryStateFlow(intervalMs: Long = 3_000L): Flow<BatteryState> {
        val pollFlow = flow {
            while (true) {
                emit(readBatteryState())
                delay(intervalMs)
            }
        }
        val plugEventFlow = powerConnectionEventFlow()
        return merge(pollFlow, plugEventFlow)
    }

    /**
     * Emits one fresh BatteryState the instant the OS broadcasts a plug/unplug event.
     */
    private fun powerConnectionEventFlow(): Flow<BatteryState> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                trySend(readBatteryState())
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)   // <-- add this
        }
        context.registerReceiver(receiver, filter)
        awaitClose { context.unregisterReceiver(receiver) }
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

        // Current: positive when charging, negative when draining.
        // BATTERY_PROPERTY_CURRENT_NOW is NOT consistently in microamps across OEMs —
        // most AOSP/Pixel/OnePlus devices report µA, but many Samsung/Exynos and some
        // Xiaomi/MediaTek devices report mA directly. Detected here by magnitude: no
        // real phone draws under 100,000 µA (100 mA) while sampled, so a smaller raw
        // value can only mean the hardware already gave us mA.
        //
        // NOTE ON ACCURACY VS ACCUBATTERY: I can't guarantee this will always read
        // identically to AccuBattery's numbers. Some chipsets report CURRENT_NOW at
        // the CHARGER/PMIC side rather than strictly at the battery cell — meaning a
        // portion of that current may be powering the screen/SoC directly rather than
        // charging the battery, especially with the screen on. This is a hardware/
        // firmware behavior I can't verify or change from software, and reference apps
        // like AccuBattery explicitly recommend checking with the screen OFF for the
        // most accurate reading for this reason. If your numbers differ from
        // AccuBattery mainly with the screen on and converge with the screen off,
        // that confirms this is what's happening rather than a bug in this code.
        val currentRaw = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentMa = if (abs(currentRaw) >= 100_000) currentRaw / 1000 else currentRaw

        val voltageMicroV = stickyIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val voltageMv = voltageMicroV  // EXTRA_VOLTAGE is already in mV on most devices

        // Wattage = (|mA| * mV) / 1,000,000 → watts
        val wattage = if (voltageMv > 0) {
            (abs(currentMa).toFloat() * voltageMv.toFloat()) / 1_000_000f
        } else 0f

        val netDrainMa = currentMa

        val tempTenths = stickyIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempCelsius = tempTenths / 10f
        val tempFahrenheit = tempCelsius * 9f / 5f + 32f

        val chargeTimeMs = batteryManager.computeChargeTimeRemaining()
        val minutesToFull = if (isCharging && chargeTimeMs > 0) (chargeTimeMs / 60_000).toInt() else null
        val minutesToEmpty: Int? = null

        val systemHealthConstant = stickyIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0) ?: 0
        val systemHealthPercent = mapHealthConstantToPercent(systemHealthConstant)

        val speedTier = computeSpeedTier(isCharging, wattage)
        val chargingLabel = buildChargingLabel(isCharging, isFull, wattage, speedTier, isPluggedUsb)

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
            chargerVerdict = speedTier,
            chargingLabel = chargingLabel,
            timestampMs = System.currentTimeMillis()
        )
    }

    /**
     * Returns a charging-speed tier that reacts INSTANTLY to large, unambiguous power
     * changes, stays STABLE against PPS chargers flickering near a tier boundary, and
     * does NOT get fooled by the legitimate fast tier-hopping that happens during the
     * first few seconds of a new charging session as PPS ramps up to nominal wattage.
     */
    private fun computeSpeedTier(isCharging: Boolean, wattage: Float): ChargerVerdict {
        val now = System.currentTimeMillis()

        if (!isCharging) {
            wattageHistoryForTier.clear()
            recentTierChangeTimestamps.clear()
            lastCommittedTier = ChargerVerdict.UNKNOWN
            pendingTier = null
            sessionStartMs = null
            return ChargerVerdict.UNKNOWN
        }

        if (sessionStartMs == null) {
            // Charging just started this tick — begin the ramp-up grace period.
            sessionStartMs = now
            recentTierChangeTimestamps.clear()
        }
        val withinRampUpGrace = (now - (sessionStartMs ?: now)) < flickerGracePeriodMs

        wattageHistoryForTier.addLast(TimedWattage(now, wattage))
        while (wattageHistoryForTier.isNotEmpty() &&
            now - wattageHistoryForTier.first().timestampMs > smoothingWindowMs
        ) {
            wattageHistoryForTier.removeFirst()
        }

        purgeOldFlickerTimestamps(now)
        val isCurrentlyFlickering = !withinRampUpGrace &&
            recentTierChangeTimestamps.size >= flickerCountThreshold

        val instantTier = tierFor(wattage)
        if (!isNearAnyBoundary(wattage) && !isCurrentlyFlickering) {
            if (instantTier != lastCommittedTier) {
                recordTierChange(now)
                lastCommittedTier = instantTier
            }
            pendingTier = null
            wattageHistoryForTier.clear()
            wattageHistoryForTier.addLast(TimedWattage(now, wattage)) // reseed from this
                                                                        // confirmed point
            return lastCommittedTier
        }

        // Ambiguous zone (near a boundary, OR already flickering outside the ramp-up
        // grace period): smooth + hysteresis to damp it out.
        val smoothedWattage = wattageHistoryForTier.map { it.watts }.average().toFloat()
        val candidateTier = tierFor(smoothedWattage)

        if (candidateTier == lastCommittedTier) {
            pendingTier = null
            return lastCommittedTier
        }

        if (pendingTier != candidateTier) {
            pendingTier = candidateTier
            pendingTierSinceMs = now
            return lastCommittedTier
        }

        return if (now - pendingTierSinceMs >= tierHysteresisMs) {
            recordTierChange(now)
            lastCommittedTier = candidateTier
            pendingTier = null
            lastCommittedTier
        } else {
            lastCommittedTier
        }
    }

    private fun recordTierChange(now: Long) {
        recentTierChangeTimestamps.addLast(now)
        while (recentTierChangeTimestamps.size > 5) recentTierChangeTimestamps.removeFirst()
    }

    private fun purgeOldFlickerTimestamps(now: Long) {
        while (recentTierChangeTimestamps.isNotEmpty() &&
            now - recentTierChangeTimestamps.first() > flickerWindowMs
        ) {
            recentTierChangeTimestamps.removeFirst()
        }
    }

    private fun tierFor(watts: Float): ChargerVerdict = when {
        watts >= 30f -> ChargerVerdict.SUPER_FAST
        watts >= 15f -> ChargerVerdict.FAST
        watts >= 5f  -> ChargerVerdict.STANDARD
        else          -> ChargerVerdict.SLOW
    }

    private val boundaryZoneWatts = 1.5f
    private val tierBoundaries = floatArrayOf(5f, 15f, 30f)

    private fun isNearAnyBoundary(watts: Float): Boolean =
        tierBoundaries.any { boundary -> kotlin.math.abs(watts - boundary) <= boundaryZoneWatts }

    private fun buildChargingLabel(
        isCharging: Boolean,
        isFull: Boolean,
        wattage: Float,
        speedTier: ChargerVerdict,
        usb: Boolean
    ): String {
        val wattText = String.format("%.1f", wattage)
        return when {
            isFull -> "Full — great job unplugging!"
            isCharging -> when (speedTier) {
                ChargerVerdict.SUPER_FAST -> "Super fast charging — ${wattText}W"
                ChargerVerdict.FAST       -> "Fast charging — ${wattText}W"
                ChargerVerdict.STANDARD   -> "Charging — ${wattText}W"
                ChargerVerdict.SLOW       -> if (usb) "Charging via USB — ${wattText}W" else "Slow charging — ${wattText}W"
                ChargerVerdict.UNKNOWN    -> "Charging — ${wattText}W"
            }
            else -> "Discharging"
        }
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