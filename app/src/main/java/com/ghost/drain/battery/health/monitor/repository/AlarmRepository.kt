package com.ghost.drain.battery.health.monitor.repository

import com.ghost.drain.battery.health.monitor.data.PreferencesDataSource
import com.ghost.drain.battery.health.monitor.model.AlarmConfig
import kotlinx.coroutines.flow.Flow

class AlarmRepository(private val prefs: PreferencesDataSource) {

    val alarmConfig: Flow<AlarmConfig> = prefs.alarmConfig

    suspend fun saveAlarmConfig(config: AlarmConfig) {
        prefs.setAlarmConfig(config)
    }
}