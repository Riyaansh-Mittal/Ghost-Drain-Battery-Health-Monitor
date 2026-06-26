package com.ghost.drain.battery.health.monitor.repository

import com.ghost.drain.battery.health.monitor.data.BatteryDataSource
import com.ghost.drain.battery.health.monitor.model.BatteryState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*

class BatteryRepository(private val dataSource: BatteryDataSource) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _currentMaBuffer = MutableStateFlow<List<Int>>(emptyList())
    val currentMaBuffer: StateFlow<List<Int>> = _currentMaBuffer.asStateFlow()

    // --- Session-average charging speed (Part 4: in-memory, current session only) ---
    // "Session" = from the moment isCharging flips false->true until it flips back to
    // false (unplugged, or charger reports full). Resets on every new session.
    // This mirrors AccuBattery's "average" stat, which is the mean of all current
    // readings since plug-in, not a fixed trailing window like the charger-verdict
    // smoothing buffer in BatteryDataSource (that's a separate, unrelated concern).
    private var wasChargingLastTick = false
    private var sessionMaSum = 0L
    private var sessionMaCount = 0

    private val _averageChargingMa = MutableStateFlow<Int?>(null)
    val averageChargingMa: StateFlow<Int?> = _averageChargingMa.asStateFlow()

    val batteryState: StateFlow<BatteryState> = dataSource
        .batteryStateFlow()   // uses BatteryDataSource default of 1_000L
        .onEach { state ->
            _currentMaBuffer.value = (_currentMaBuffer.value + state.currentMa).takeLast(600)
            updateSessionAverage(state)
        }
        .stateIn(
            scope        = scope,
            started      = SharingStarted.WhileSubscribed(3_000),
            initialValue = BatteryState()
        )

    private fun updateSessionAverage(state: BatteryState) {
        if (state.isCharging && !wasChargingLastTick) {
            // New charging session started — reset accumulator
            sessionMaSum = 0L
            sessionMaCount = 0
        }

        if (state.isCharging) {
            sessionMaSum += kotlin.math.abs(state.currentMa)
            sessionMaCount += 1
            _averageChargingMa.value = (sessionMaSum / sessionMaCount).toInt()
        } else {
            // Not charging — no average to show
            _averageChargingMa.value = null
        }

        wasChargingLastTick = state.isCharging
    }

    fun readOnce(): BatteryState = dataSource.readBatteryState()
}