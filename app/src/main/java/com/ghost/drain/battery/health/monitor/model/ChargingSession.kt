package com.ghost.drain.battery.health.monitor.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "charging_sessions")
data class ChargingSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startPercent: Int,
    val endPercent: Int,
    val startTimestampMs: Long,
    val endTimestampMs: Long,
    val durationMinutes: Int,
    val maxTemperatureCelsius: Float,
    val peakCurrentMa: Int,
    val wasOverLimit: Boolean,         // exceeded high alarm threshold
    val estimatedWearCycles: Float,    // ~0.4 cycles for a normal session
    val chargerVerdict: String         // ChargerVerdict.name stored as string
)