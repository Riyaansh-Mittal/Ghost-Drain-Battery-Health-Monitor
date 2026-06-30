package com.ghost.drain.battery.health.monitor.ui.home

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ghost.drain.battery.health.monitor.data.BatteryDataSource
import com.ghost.drain.battery.health.monitor.data.UserPreferences
import com.ghost.drain.battery.health.monitor.data.AlarmPreferences
import com.ghost.drain.battery.health.monitor.model.BatteryState
import com.ghost.drain.battery.health.monitor.model.ChargerVerdict
import com.ghost.drain.battery.health.monitor.repository.BatteryRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch


data class HomeUiState(
    // ── Gauge ───────────────────────────────────────────────────────────────
    val percent: Int = 0,
    val temperatureCelsius: Float = 0f,

    // ── Charging pill ────────────────────────────────────────────────────────
    val chargingPillText: String = "Reading…",
    val isCharging: Boolean = false,

    // ── Time to full / empty ─────────────────────────────────────────────────
    // "Full in ~38 min" shown below the charging pill when charging,
    // null/empty when discharging or not yet computed.
    val minutesToFullText: String = "",

    // ── Temperature card ─────────────────────────────────────────────────────
    val tempDisplayText: String = "—",          // "43°C / 109°F"
    val tempContextText: String = "Normal",     // "High temperature" / "Danger — unplug!" etc
    val tempColorLevel: TempLevel = TempLevel.NORMAL,

    // ── Charger card ─────────────────────────────────────────────────────────
    // verdictLine: "Fast — 18W" (tier + wattage merged with em-dash, matching design)
    val chargerVerdictLine: String = "—",
    val chargerQualityText: String = "",        // "Good charger" / "Slow charger" / "Check charger"
    val deviceModelText: String = "",           // phone's Build.MODEL (charger ID not available via API)
    val chargerColorLevel: ChargerLevel = ChargerLevel.NORMAL,

    // ── Alarm cards ──────────────────────────────────────────────────────────
    val highAlarmEnabled: Boolean = true,
    val highAlarmPercent: Int = 80,
    val lowAlarmEnabled: Boolean = true,
    val lowAlarmPercent: Int = 20,

    // ── Battery health card (stub until Part 7 / HealthRepository) ───────────
    // These are populated by real HealthRepository data in Part 7.
    // Until then they show a calibration stub so the card isn't blank.
    val healthPercent: Int? = null,             // null = calibrating
    val healthGradeText: String = "Calibrating…",
    val healthCurrentMah: Int? = null,
    val healthDesignMah: Int? = null,

    // ── Live mA card ─────────────────────────────────────────────────────────
    val currentText: String = "—",             // "+1,840 mA"
    val averageCurrentText: String = "—",      // "1,720 mA avg" — session average
    val netCurrentText: String = "",           // "Net: +0.312 mA"
    val currentIsPositive: Boolean = true,
    val voltageMvText: String = "",            // "4,198 mV"
    val sparklinePoints: List<Float> = emptyList(), // last N mA readings for mini chart

    // ── Ghost drain banner ───────────────────────────────────────────────────
    val showGhostDrainBanner: Boolean = false,

    // ── Identity ─────────────────────────────────────────────────────────────
    val isPowerUser: Boolean = false
)

enum class TempLevel   { NORMAL, WARNING, DANGER }
enum class ChargerLevel { NORMAL, WARNING, DANGER }

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo      = BatteryRepository(BatteryDataSource(app))
    private val userPrefs = UserPreferences(app)
    private val alarmPrefs = AlarmPreferences(app)

    // Phone model used in charger card — read once, doesn't change at runtime.
    // Note: Android provides NO API for charger manufacturer/model. Build.MODEL
    // is the phone model, used as a proxy the way reference apps like BatteryGuru
    // do, since it's the closest piece of device identity available.
    private val phoneModel: String = Build.MODEL

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        repo.batteryState
            .onEach { state -> _uiState.value = state.toUiState() }
            .launchIn(viewModelScope)

        repo.averageChargingMa
            .onEach { avgMa ->
                _uiState.value = _uiState.value.copy(
                    averageCurrentText = avgMa?.let { "${it.formatWithComma()} mA avg" } ?: "—"
                )
            }
            .launchIn(viewModelScope)

        // Sparkline: expose the rolling mA buffer to the UI for the mini chart.
        // currentMaBuffer holds the last 120 readings (2 min at 1s polling).
        // We only show the last 30 in the sparkline (30s window) to keep it readable.
        repo.currentMaBuffer
            .onEach { buffer ->
                _uiState.value = _uiState.value.copy(
                    sparklinePoints = buffer.takeLast(30).map { it.toFloat() }
                )
            }
            .launchIn(viewModelScope)

        userPrefs.userIdentity
            .onEach { key ->
                _uiState.value = _uiState.value.copy(isPowerUser = key == "power_user")
            }
            .launchIn(viewModelScope)
        
        alarmPrefs.highAlarmEnabled
            .onEach { enabled -> _uiState.value = _uiState.value.copy(highAlarmEnabled = enabled) }
            .launchIn(viewModelScope)

        alarmPrefs.highAlarmPercent
            .onEach { pct -> _uiState.value = _uiState.value.copy(highAlarmPercent = pct) }
            .launchIn(viewModelScope)

        alarmPrefs.lowAlarmEnabled
            .onEach { enabled -> _uiState.value = _uiState.value.copy(lowAlarmEnabled = enabled) }
            .launchIn(viewModelScope)

        alarmPrefs.lowAlarmPercent
            .onEach { pct -> _uiState.value = _uiState.value.copy(lowAlarmPercent = pct) }
            .launchIn(viewModelScope)
    }

    // Replace toggle functions:
    fun toggleHighAlarm() {
        viewModelScope.launch {
            alarmPrefs.setHighAlarmEnabled(!_uiState.value.highAlarmEnabled)
        }
    }

    fun toggleLowAlarm() {
        viewModelScope.launch {
            alarmPrefs.setLowAlarmEnabled(!_uiState.value.lowAlarmEnabled)
        }
    }

    fun dismissGhostDrainBanner() {
        _uiState.value = _uiState.value.copy(showGhostDrainBanner = false)
    }

    // TODO Part 7: inject HealthRepository and map real health data here.
    // When Part 7 is built, call healthRepo.latestHealth.onEach { measurement ->
    //   _uiState.value = _uiState.value.copy(
    //     healthPercent    = measurement.healthPercent,
    //     healthGradeText  = measurement.gradeLabel,
    //     healthCurrentMah = measurement.currentMah,
    //     healthDesignMah  = measurement.designMah
    //   )
    // }.launchIn(viewModelScope)

    private fun BatteryState.toUiState(): HomeUiState {
        val existing = _uiState.value

        // ── Charging pill ────────────────────────────────────────────────────
        val chargingPill = when {
            isFull     -> "Full · Great job unplugging!"
            isCharging -> chargingLabel   // e.g. "Fast charging — 18W" from BatteryDataSource
            else       -> "Discharging"
        }

        // ── Time to full ─────────────────────────────────────────────────────
        val timeToFull = when {
            !isCharging || isFull -> ""
            minutesToFull == null -> ""
            minutesToFull < 1    -> "Full in < 1 min"
            minutesToFull < 60   -> "Full in ~${minutesToFull} min"
            else -> {
                val h = minutesToFull / 60
                val m = minutesToFull % 60
                if (m == 0) "Full in ~${h}h" else "Full in ~${h}h ${m}min"
            }
        }

        // ── Temperature card ─────────────────────────────────────────────────
        val tempLevel = when {
            temperatureCelsius > 45f -> TempLevel.DANGER
            temperatureCelsius > 40f -> TempLevel.WARNING
            else                     -> TempLevel.NORMAL
        }
        val tempContext = when (tempLevel) {
            TempLevel.NORMAL  -> if (isCharging) "Normal charging temp" else "Normal"
            TempLevel.WARNING -> "High temperature · Tap for tips"
            TempLevel.DANGER  -> "Danger — unplug now!"
        }
        val tempDisplay = "${temperatureCelsius.fmt(1)}°C / ${celsiusToF(temperatureCelsius).fmt(1)}°F"

        // ── Charger card ─────────────────────────────────────────────────────
        // Design format: "Fast — 18W" (tier name + em-dash + wattage on one line).
        // Both come from the same smoothed tier in BatteryDataSource, so they can
        // never show conflicting data.
        val wattText = wattage.fmt(0) + "W"
        val verdictLine = when {
            !isCharging -> "No charger"
            isFull      -> "Full"
            else -> when (chargerVerdict) {
                ChargerVerdict.SUPER_FAST -> "Super fast — $wattText"
                ChargerVerdict.FAST       -> "Fast — $wattText"
                ChargerVerdict.STANDARD   -> "Standard — $wattText"
                ChargerVerdict.SLOW       -> "Slow — $wattText"
                ChargerVerdict.UNKNOWN    -> "Charging — $wattText"
            }
        }

        val chargerQuality = when {
            !isCharging -> ""
            chargerVerdict == ChargerVerdict.SUPER_FAST ||
                    chargerVerdict == ChargerVerdict.FAST       -> "Good charger"
            chargerVerdict == ChargerVerdict.STANDARD   -> "Standard charger"
            chargerVerdict == ChargerVerdict.SLOW       -> "Slow charger"
            else                                         -> ""
        }

        val chargerLevel = when (chargerVerdict) {
            ChargerVerdict.SLOW -> ChargerLevel.WARNING
            else                -> ChargerLevel.NORMAL
        }

        // ── Live mA card ─────────────────────────────────────────────────────
        val maCurrent = when {
            isCharging -> "+${abs(currentMa).formatWithComma()} mA"
            isFull     -> "${currentMa.formatWithComma()} mA"
            else       -> "−${abs(currentMa).formatWithComma()} mA"
        }

        // Net drain: positive when charger delivers more than device consumes.
        // Format matches the design: "Net: +0.312 mA" or "Net: −45 mA"
        val netText = if (isCharging && netDrainMa != 0) {
            val sign = if (netDrainMa > 0) "+" else "−"
            "Net: ${sign}${abs(netDrainMa).formatWithComma()} mA"
        } else ""

        val voltageText = if (voltageMv > 0) "${voltageMv.formatWithComma()} mV" else ""

        return existing.copy(
            percent            = percent,
            temperatureCelsius = temperatureCelsius,
            chargingPillText   = chargingPill,
            isCharging         = isCharging,
            minutesToFullText  = timeToFull,
            tempDisplayText    = tempDisplay,
            tempContextText    = tempContext,
            tempColorLevel     = tempLevel,
            chargerVerdictLine = verdictLine,
            chargerQualityText = chargerQuality,
            deviceModelText    = phoneModel,
            chargerColorLevel  = chargerLevel,
            currentText        = maCurrent,
            netCurrentText     = netText,
            currentIsPositive  = isCharging || isFull,
            voltageMvText      = voltageText,
            showGhostDrainBanner = isGhostDrainDetected
        )
    }

    private fun abs(n: Int) = kotlin.math.abs(n)
    private fun Float.fmt(decimals: Int) = "%.${decimals}f".format(this)
    private fun celsiusToF(c: Float) = c * 9f / 5f + 32f
    private fun Int.formatWithComma() = "%,d".format(kotlin.math.abs(this))
}