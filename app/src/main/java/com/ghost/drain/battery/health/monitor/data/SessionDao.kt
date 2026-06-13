// SessionDao.kt
package com.ghost.drain.battery.health.monitor.data

import androidx.room.*

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: BatterySession): Long

    @Update
    suspend fun update(session: BatterySession)

    @Query("SELECT * FROM sessions WHERE isQualifying = 1 ORDER BY startTimestamp DESC LIMIT 7")
    suspend fun getLastQualifyingSessions(): List<BatterySession>

    @Query("SELECT COUNT(*) FROM sessions WHERE isQualifying = 1")
    suspend fun getQualifyingSessionCount(): Int

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()

    @Query("SELECT * FROM sessions ORDER BY startTimestamp DESC LIMIT 50")
    suspend fun getRecentSessions(): List<BatterySession>
}