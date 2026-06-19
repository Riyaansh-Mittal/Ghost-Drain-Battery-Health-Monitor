package com.ghost.drain.battery.health.monitor.repository

import com.ghost.drain.battery.health.monitor.data.BatteryDataSource
import com.ghost.drain.battery.health.monitor.model.BatteryState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*

class BatteryRepository(private val dataSource: BatteryDataSource) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Rolling 10-minute buffer (120 readings × 5s = 600s = 10 min)
    private val _currentMaBuffer = MutableStateFlow<List<Int>>(emptyList())
    val currentMaBuffer: StateFlow<List<Int>> = _currentMaBuffer.asStateFlow()

    // Shared live state — all screens collect this single flow
    val batteryState: StateFlow<BatteryState> = dataSource
        .batteryStateFlow(intervalMs = 5_000L)
        .onEach { state ->
            // Update rolling mA buffer
            val updated = (_currentMaBuffer.value + state.currentMa).takeLast(120)
            _currentMaBuffer.value = updated
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BatteryState()
        )

    // Convenience: one-shot read (for service startup, etc.)
    fun readOnce(): BatteryState = dataSource.readBatteryState()

    // Ghost drain: true if screen-off drain is above 3%/hour threshold
    // Evaluated by the service — this flag is set in BatteryState.isGhostDrainDetected
    // ViewModel reads batteryState.isGhostDrainDetected directly
}