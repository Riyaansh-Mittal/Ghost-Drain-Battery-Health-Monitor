package com.ghost.drain.battery.health.monitor.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "health_measurements")
data class HealthMeasurement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val healthPercent: Float,          // e.g. 88.0
    val currentCapacityMah: Int,       // measured this session
    val designCapacityMah: Int,        // user-set or system default
    val sessionId: Long                // links to ChargingSession
)