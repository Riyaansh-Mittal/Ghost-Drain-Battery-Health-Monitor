package com.ghost.drain.battery.health.monitor.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.alarmDataStore by preferencesDataStore("alarm_prefs")

class AlarmPreferences(private val context: Context) {

    companion object {
        val HIGH_ENABLED           = booleanPreferencesKey("high_alarm_enabled")
        val HIGH_PERCENT           = intPreferencesKey("high_alarm_percent")
        val LOW_ENABLED            = booleanPreferencesKey("low_alarm_enabled")
        val LOW_PERCENT            = intPreferencesKey("low_alarm_percent")
        val OVERHEAT_ENABLED       = booleanPreferencesKey("overheat_alarm_enabled")
        val OVERHEAT_THRESHOLD     = intPreferencesKey("overheat_threshold_celsius")
        val SOUND_URI              = stringPreferencesKey("alarm_sound_uri")

        // ── Streak keys ────────────────────────────────────────────────────────
        // STREAK_COUNT      — consecutive calendar days user unplugged in success zone
        // STREAK_LAST_DATE  — "yyyy-MM-dd" of last day streak was incremented
        //                     (prevents double-counting same day)
        // LONGEST_STREAK    — all-time best streak (never decreases)
        // TODAY_STATUS      — "NONE" | "SUCCESS" | "FAIL" (resets each new day via
        //                     evaluateChargeSession — the first event of a new day
        //                     always starts fresh)
        // STREAK_TARGET     — user-configurable unplug target (70–95, default 80)
        // CHALLENGE_DONE    — true once streak_count hits 30 (never resets)
        // PREVIOUS_STREAK   — streak value just before a reset (for failure notifications)
        val STREAK_COUNT           = intPreferencesKey("alarm_streak_days")
        val STREAK_LAST_DATE       = stringPreferencesKey("alarm_streak_last_date")
        val LONGEST_STREAK         = intPreferencesKey("longest_streak")
        val TODAY_STATUS           = stringPreferencesKey("today_status")
        val STREAK_TARGET          = intPreferencesKey("streak_target_percent")
        val CHALLENGE_DONE         = booleanPreferencesKey("challenge_completed_30")
        val PREVIOUS_STREAK        = intPreferencesKey("previous_streak")
    }

    // ── Flows ──────────────────────────────────────────────────────────────────

    val highAlarmEnabled:    Flow<Boolean> = context.alarmDataStore.data.map { it[HIGH_ENABLED]       ?: true }
    val highAlarmPercent:    Flow<Int>     = context.alarmDataStore.data.map { it[HIGH_PERCENT]       ?: 80 }
    val lowAlarmEnabled:     Flow<Boolean> = context.alarmDataStore.data.map { it[LOW_ENABLED]        ?: true }
    val lowAlarmPercent:     Flow<Int>     = context.alarmDataStore.data.map { it[LOW_PERCENT]        ?: 20 }
    val overheatEnabled:     Flow<Boolean> = context.alarmDataStore.data.map { it[OVERHEAT_ENABLED]   ?: true }
    val overheatThreshold:   Flow<Int>     = context.alarmDataStore.data.map { it[OVERHEAT_THRESHOLD] ?: 45 }
    val soundUri:            Flow<String>  = context.alarmDataStore.data.map { it[SOUND_URI]          ?: "" }

    val streakCount:         Flow<Int>     = context.alarmDataStore.data.map { it[STREAK_COUNT]       ?: 0 }
    val streakLastDate:      Flow<String>  = context.alarmDataStore.data.map { it[STREAK_LAST_DATE]   ?: "" }
    val longestStreak:       Flow<Int>     = context.alarmDataStore.data.map { it[LONGEST_STREAK]     ?: 0 }
    val todayStatus:         Flow<String>  = context.alarmDataStore.data.map { it[TODAY_STATUS]       ?: "NONE" }
    val streakTargetPercent: Flow<Int>     = context.alarmDataStore.data.map { it[STREAK_TARGET]      ?: 80 }
    val challengeCompleted:  Flow<Boolean> = context.alarmDataStore.data.map { it[CHALLENGE_DONE]     ?: false }

    // ── Simple setters ─────────────────────────────────────────────────────────

    suspend fun setHighAlarmEnabled(v: Boolean)  = context.alarmDataStore.edit { it[HIGH_ENABLED]       = v }
    suspend fun setHighAlarmPercent(v: Int)       = context.alarmDataStore.edit { it[HIGH_PERCENT]       = v }
    suspend fun setLowAlarmEnabled(v: Boolean)    = context.alarmDataStore.edit { it[LOW_ENABLED]        = v }
    suspend fun setLowAlarmPercent(v: Int)        = context.alarmDataStore.edit { it[LOW_PERCENT]        = v }
    suspend fun setOverheatEnabled(v: Boolean)    = context.alarmDataStore.edit { it[OVERHEAT_ENABLED]   = v }
    suspend fun setOverheatThreshold(v: Int)      = context.alarmDataStore.edit { it[OVERHEAT_THRESHOLD] = v }
    suspend fun setSoundUri(v: String)            = context.alarmDataStore.edit { it[SOUND_URI]          = v }
    suspend fun setStreakTarget(pct: Int)          = context.alarmDataStore.edit { it[STREAK_TARGET]      = pct }
    suspend fun setLongestStreak(v: Int)          = context.alarmDataStore.edit { it[LONGEST_STREAK]     = v }
    suspend fun setTodayStatus(s: String)         = context.alarmDataStore.edit { it[TODAY_STATUS]       = s }
    suspend fun setChallengeCompleted(v: Boolean) = context.alarmDataStore.edit { it[CHALLENGE_DONE]     = v }

    /** One-shot read — for use in suspend contexts only. */
    suspend fun getPreviousStreak(): Int =
        context.alarmDataStore.data.map { it[PREVIOUS_STREAK] ?: 0 }.first()

    // ── Legacy: kept so BatteryMonitorService call-sites still compile ─────────
    /**
     * No-op — streak is now driven entirely by [evaluateChargeSession] on
     * charger-disconnect. The high-alarm path no longer increments the streak
     * directly; this stub prevents compile errors in existing code.
     */
    suspend fun incrementStreakIfNewDay(todayDate: String): Boolean = false

    // ── Core streak logic ──────────────────────────────────────────────────────

    /**
     * Called by BatteryMonitorService the moment the charger is physically
     * disconnected. Determines which zone the unplug falls into relative to
     * the user's target, then updates streak state accordingly.
     *
     * Zone definitions for a target T:
     *   Perfect   T-2 … T+2   → SUCCESS, streak +1
     *   Good      T-5 … T-3   → SUCCESS, streak +1
     *   Warning   T+3 … T+5   → SUCCESS (with banner), streak +1
     *   Fail      T+6 and above → FAIL, streak reset to 0
     *   Below good zone        → no change (early unplug, not penalised)
     *
     * Maximum one streak increment per calendar day — subsequent successful
     * sessions the same day are acknowledged but do not move the counter.
     *
     * Returns a [StreakResult] so the caller can decide which notification to fire.
     */
    suspend fun evaluateChargeSession(
        unplugPercent: Int,
        today: String
    ): StreakResult {
        val target    = context.alarmDataStore.data.map { it[STREAK_TARGET]    ?: 80 }.first()
        val todaySt   = context.alarmDataStore.data.map { it[TODAY_STATUS]     ?: "NONE" }.first()
        val lastDay   = context.alarmDataStore.data.map { it[STREAK_LAST_DATE] ?: "" }.first()
        val streak    = context.alarmDataStore.data.map { it[STREAK_COUNT]     ?: 0 }.first()
        val longest   = context.alarmDataStore.data.map { it[LONGEST_STREAK]   ?: 0 }.first()
        val alreadyWon= context.alarmDataStore.data.map { it[CHALLENGE_DONE]   ?: false }.first()

        val perfectLow  = target - 2
        val perfectHigh = target + 2
        val goodLow     = target - 5
        val warnHigh    = target + 5
        val failThresh  = target + 6

        val zone = when {
            unplugPercent in perfectLow..perfectHigh          -> Zone.PERFECT
            unplugPercent in goodLow until perfectLow         -> Zone.GOOD
            unplugPercent in (perfectHigh + 1)..warnHigh      -> Zone.WARNING
            unplugPercent >= failThresh                        -> Zone.FAIL
            else                                               -> Zone.EARLY  // unplugged early — no penalty
        }

        return when (zone) {
            Zone.PERFECT, Zone.GOOD, Zone.WARNING -> {
                if (todaySt == "SUCCESS") {
                    // Already counted today — acknowledge but don't double-count
                    StreakResult.AlreadyCounted(streak)
                } else {
                    // First success this calendar day
                    val isNewDay   = lastDay != today
                    val newStreak  = if (isNewDay) streak + 1 else streak
                    val newLongest = longest.coerceAtLeast(newStreak)
                    val hit30      = newStreak >= 30 && !alreadyWon

                    context.alarmDataStore.edit { prefs ->
                        prefs[STREAK_COUNT]     = newStreak
                        prefs[LONGEST_STREAK]   = newLongest
                        prefs[STREAK_LAST_DATE] = today
                        prefs[TODAY_STATUS]     = "SUCCESS"
                        if (hit30) prefs[CHALLENGE_DONE] = true
                    }

                    when {
                        hit30              -> StreakResult.ChallengeComplete(newStreak)
                        isNewDay           -> StreakResult.Success(newStreak, zone)
                        else               -> StreakResult.AlreadyCounted(newStreak)
                    }
                }
            }

            Zone.FAIL -> {
                if (todaySt == "FAIL") {
                    // Already failed today — don't spam
                    StreakResult.AlreadyFailed
                } else {
                    val prev = streak
                    context.alarmDataStore.edit { prefs ->
                        prefs[PREVIOUS_STREAK] = prev
                        prefs[STREAK_COUNT]    = 0
                        prefs[TODAY_STATUS]    = "FAIL"
                        // Do NOT clear STREAK_LAST_DATE or LONGEST_STREAK
                    }
                    StreakResult.Failure(prev)
                }
            }

            Zone.EARLY -> StreakResult.NoChange
        }
    }

    suspend fun resetStreak() = context.alarmDataStore.edit {
        it[STREAK_COUNT]     = 0
        it[STREAK_LAST_DATE] = ""
        it[TODAY_STATUS]     = "NONE"
    }

    // ── Supporting types ───────────────────────────────────────────────────────

    enum class Zone { PERFECT, GOOD, WARNING, FAIL, EARLY }

    sealed class StreakResult {
        data class Success(val newStreak: Int, val zone: Zone) : StreakResult()
        data class ChallengeComplete(val streak: Int)          : StreakResult()
        data class Failure(val previousStreak: Int)            : StreakResult()
        data class AlreadyCounted(val streak: Int)             : StreakResult()
        object AlreadyFailed                                   : StreakResult()
        object NoChange                                        : StreakResult()
    }
    //for streak testing only
//    suspend fun debugForceStreak(days: Int) {
//        context.alarmDataStore.edit { prefs ->
//            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
//            prefs[STREAK_COUNT]         = days
//            prefs[LONGEST_STREAK]       = maxOf(days, prefs[LONGEST_STREAK] ?: 0)
//            prefs[STREAK_LAST_DATE]     = today
//            prefs[CHALLENGE_DONE]       = (days >= 30)
//            prefs[PREVIOUS_STREAK]      = 0
//        }
//    }
}