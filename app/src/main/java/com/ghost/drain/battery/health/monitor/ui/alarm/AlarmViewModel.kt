package com.ghost.drain.battery.health.monitor.ui.alarm

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ghost.drain.battery.health.monitor.data.AlarmPreferences
import com.ghost.drain.battery.health.monitor.service.BatteryMonitorService
import com.ghost.drain.battery.health.monitor.service.OemBatteryOptimizationHelper
import com.ghost.drain.battery.health.monitor.service.OemBatteryOptimizationHelper.OptimizationStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AlarmUiState(
    val highEnabled:         Boolean = true,
    val highPercent:         Int     = 80,
    val lowEnabled:          Boolean = true,
    val lowPercent:          Int     = 20,
    val overheatEnabled:     Boolean = true,
    val overheatThreshold:   Int     = 45,
    val soundUri:            String  = "",

    // ── Streak ──────────────────────────────────────────────────────────────
    val streakCount:         Int     = 0,
    val longestStreak:       Int     = 0,
    val streakTargetPercent: Int     = 80,
    val challengeCompleted:  Boolean = false,

    // true only when PowerManager confirms NOT exempted AND at least one alarm is on
    val showOemBanner:       Boolean = false
)

class AlarmViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = AlarmPreferences(app)

    private val _uiState = MutableStateFlow(AlarmUiState())
    val uiState: StateFlow<AlarmUiState> = _uiState.asStateFlow()

    init {
        // ── Combine 1: alarm toggles + OEM banner ──────────────────────────────
        combine(
            prefs.highAlarmEnabled,
            prefs.highAlarmPercent,
            prefs.lowAlarmEnabled,
            prefs.lowAlarmPercent,
            prefs.overheatEnabled
        ) { highEn, highPct, lowEn, lowPct, heatEn ->
            val anyEnabled = highEn || lowEn || heatEn
            _uiState.value.copy(
                highEnabled     = highEn,
                highPercent     = highPct,
                lowEnabled      = lowEn,
                lowPercent      = lowPct,
                overheatEnabled = heatEn,
                showOemBanner   = anyEnabled && isNotExempted()
            )
        }.onEach { _uiState.value = it }.launchIn(viewModelScope)

        // ── Combine 2: thresholds, sound, and streak fields ────────────────────
        // combine() supports max 5 args; use zip-style nesting for 6+ flows.
        combine(
            prefs.overheatThreshold,
            prefs.soundUri,
            prefs.streakCount,
            prefs.longestStreak,
            prefs.streakTargetPercent
        ) { thresh, sound, streak, longest, target ->
            _uiState.value.copy(
                overheatThreshold    = thresh,
                soundUri             = sound,
                streakCount          = streak,
                longestStreak        = longest,
                streakTargetPercent  = target
            )
        }.onEach { _uiState.value = it }.launchIn(viewModelScope)

        // ── challengeCompleted observed separately (Boolean, not Int) ──────────
        prefs.challengeCompleted
            .onEach { done -> _uiState.update { it.copy(challengeCompleted = done) } }
            .launchIn(viewModelScope)
    }

    /**
     * Call on every screen resume (via LifecycleEventObserver in AlarmScreen).
     * Makes the OEM banner disappear immediately after exemption is granted,
     * and re-appear if it is later revoked.
     */
    fun recheckOptimizationStatus() {
        val anyEnabled = with(_uiState.value) { highEnabled || lowEnabled || overheatEnabled }
        _uiState.update { it.copy(showOemBanner = anyEnabled && isNotExempted()) }
    }

    private fun isNotExempted() =
        OemBatteryOptimizationHelper.getStatus(getApplication()) == OptimizationStatus.NOT_EXEMPTED

    // ── Alarm toggles ──────────────────────────────────────────────────────────

    fun setHighEnabled(v: Boolean) = viewModelScope.launch {
        prefs.setHighAlarmEnabled(v)
        syncService(highEnabled = v)
    }

    fun setHighPercent(v: Int) = viewModelScope.launch { prefs.setHighAlarmPercent(v) }

    fun setLowEnabled(v: Boolean) = viewModelScope.launch {
        prefs.setLowAlarmEnabled(v)
        syncService(lowEnabled = v)
    }

    fun setLowPercent(v: Int) = viewModelScope.launch { prefs.setLowAlarmPercent(v) }

    fun setOverheatEnabled(v: Boolean) = viewModelScope.launch {
        prefs.setOverheatEnabled(v)
        syncService(overheatEnabled = v)
    }

    fun setOverheatThreshold(v: Int) = viewModelScope.launch { prefs.setOverheatThreshold(v) }

    fun setSoundUri(uri: String) = viewModelScope.launch { prefs.setSoundUri(uri) }

    // ── Streak setter ──────────────────────────────────────────────────────────

    fun setStreakTarget(pct: Int) = viewModelScope.launch { prefs.setStreakTarget(pct) }

    // ── Service sync ───────────────────────────────────────────────────────────

    /**
     * Pass the just-changed value directly — avoids reading stale DataStore state
     * on the same frame the toggle fires.
     */
    private fun syncService(
        highEnabled:     Boolean = _uiState.value.highEnabled,
        lowEnabled:      Boolean = _uiState.value.lowEnabled,
        overheatEnabled: Boolean = _uiState.value.overheatEnabled
    ) {
        val anyEnabled = highEnabled || lowEnabled || overheatEnabled
        val ctx = getApplication<Application>()
        if (anyEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ctx.startForegroundService(BatteryMonitorService.buildStartIntent(ctx))
            else
                ctx.startService(BatteryMonitorService.buildStartIntent(ctx))
        } else {
            ctx.stopService(BatteryMonitorService.buildStartIntent(ctx))
        }
    }

//    fun debugSetStreak(days: Int) {
//        viewModelScope.launch {
//            prefs.debugForceStreak(days)
//            // Nothing needed — DataStore flows emit automatically
//        }
//    }
//
//    fun debugResetStreak() {
//        viewModelScope.launch {
//            prefs.debugForceStreak(0)
//        }
//    }
}