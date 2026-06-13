// BatterySession.kt
package com.ghost.drain.battery.health.monitor.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class BatterySession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTimestamp: Long,
    val endTimestamp: Long = 0L,
    val startPercent: Int,
    val endPercent: Int = 0,
    val peakTempCelsius: Float = 0f,
    val maxCurrentMa: Int = 0,
    val isCharging: Boolean,
    // For health calibration — only qualifying sessions (>=15% range) count
    val isQualifying: Boolean = false,
    val estimatedCapacityMah: Int = 0
)