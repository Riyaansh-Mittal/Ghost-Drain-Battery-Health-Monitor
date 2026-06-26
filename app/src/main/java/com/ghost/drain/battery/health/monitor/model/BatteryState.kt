package com.ghost.drain.battery.health.monitor.model

data class BatteryState(
    // Core
    val percent: Int = 0,                        // 0–100
    val isCharging: Boolean = false,
    val isPluggedUsb: Boolean = false,
    val isPluggedAc: Boolean = false,
    val isPluggedWireless: Boolean = false,
    val isFull: Boolean = false,

    // Current & power
    val currentMa: Int = 0,                      // positive = charging, negative = draining
    val voltageMv: Int = 0,
    val wattage: Float = 0f,                     // calculated: (|mA| * mV) / 1_000_000
    val netDrainMa: Int = 0,                     // positive = charger winning, negative = losing

    // Temperature
    val temperatureCelsius: Float = 0f,
    val temperatureFahrenheit: Float = 0f,       // derived

    // Time estimates
    val minutesToFull: Int? = null,              // null = measuring / not charging
    val minutesToEmpty: Int? = null,             // null = measuring / charging

    // Health (raw from BatteryManager — unreliable, used only as fallback)
    val systemHealthPercent: Int = 0,            // BATTERY_HEALTH_* constant mapped to %

    // Charger speed classification
    val chargerVerdict: ChargerVerdict = ChargerVerdict.UNKNOWN,

    // Ghost drain
    val isGhostDrainDetected: Boolean = false,

    // Charging state label (human-readable)
    val chargingLabel: String = "",

    // Timestamp
    val timestampMs: Long = 0L
)

/**
 * Charging SPEED classification only. Deliberately does NOT attempt to detect
 * counterfeit/fake chargers — that requires kHz-range voltage ripple measurement
 * (oscilloscope/multimeter territory), which is physically invisible to software
 * polling a fuel-gauge IC every few seconds. AccuBattery and BatteryGuru, the
 * reference apps this is matched against, don't make that claim either — they
 * only classify speed. A false "counterfeit" verdict on a genuine charger is
 * worse for user trust than not making the claim at all.
 */
enum class ChargerVerdict {
    UNKNOWN,
    SLOW,       // < 5W
    STANDARD,   // 5–14W
    FAST,       // 15–29W
    SUPER_FAST  // 30W+
}