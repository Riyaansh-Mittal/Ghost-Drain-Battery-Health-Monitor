package com.ghost.drain.battery.health.monitor.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.ghost.drain.battery.health.monitor.model.AlarmConfig
import com.ghost.drain.battery.health.monitor.model.UserIdentity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

class PreferencesDataSource(private val context: Context) {

    companion object {
        private val KEY_IDENTITY           = stringPreferencesKey("user_identity")
        private val KEY_ONBOARDING_DONE    = booleanPreferencesKey("onboarding_done")
        private val KEY_HIGH_ENABLED       = booleanPreferencesKey("high_alarm_enabled")
        private val KEY_HIGH_THRESHOLD     = intPreferencesKey("high_threshold")
        private val KEY_LOW_ENABLED        = booleanPreferencesKey("low_alarm_enabled")
        private val KEY_LOW_THRESHOLD      = intPreferencesKey("low_threshold")
        private val KEY_OVERHEAT_ENABLED   = booleanPreferencesKey("overheat_alarm_enabled")
        private val KEY_OVERHEAT_THRESHOLD = intPreferencesKey("overheat_threshold_celsius")
        private val KEY_ALARM_SOUND        = stringPreferencesKey("alarm_sound")
        private val KEY_DESIGN_CAPACITY    = intPreferencesKey("design_capacity_mah")
        private val KEY_STREAK_DAYS        = intPreferencesKey("streak_days")
        private val KEY_LAST_STREAK_DATE   = longPreferencesKey("last_streak_date_ms")
        private val KEY_GHOST_DRAIN_LAST   = longPreferencesKey("ghost_drain_last_shown_ms")
    }

    // ── Read flows ────────────────────────────────────────────────────────────

    val isOnboardingDone: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_ONBOARDING_DONE] ?: false }

    val userIdentity: Flow<UserIdentity> = context.dataStore.data
        .map { UserIdentity.fromKey(it[KEY_IDENTITY] ?: UserIdentity.GENERAL.key) }

    val alarmConfig: Flow<AlarmConfig> = context.dataStore.data.map { prefs ->
        AlarmConfig(
            highAlarmEnabled       = prefs[KEY_HIGH_ENABLED] ?: true,
            highThreshold          = prefs[KEY_HIGH_THRESHOLD] ?: 80,
            lowAlarmEnabled        = prefs[KEY_LOW_ENABLED] ?: true,
            lowThreshold           = prefs[KEY_LOW_THRESHOLD] ?: 20,
            overheatAlarmEnabled   = prefs[KEY_OVERHEAT_ENABLED] ?: true,
            overheatThresholdCelsius = prefs[KEY_OVERHEAT_THRESHOLD] ?: 42,
            alarmSoundName         = prefs[KEY_ALARM_SOUND] ?: "chime_rise"
        )
    }

    val designCapacityMah: Flow<Int> = context.dataStore.data
        .map { it[KEY_DESIGN_CAPACITY] ?: 4000 }

    val streakDays: Flow<Int> = context.dataStore.data
        .map { it[KEY_STREAK_DAYS] ?: 0 }

    // ── Write functions ───────────────────────────────────────────────────────

    suspend fun setOnboardingDone() {
        context.dataStore.edit { it[KEY_ONBOARDING_DONE] = true }
    }

    suspend fun setUserIdentity(identity: UserIdentity) {
        context.dataStore.edit { it[KEY_IDENTITY] = identity.key }
    }

    suspend fun setAlarmConfig(config: AlarmConfig) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HIGH_ENABLED]        = config.highAlarmEnabled
            prefs[KEY_HIGH_THRESHOLD]      = config.highThreshold
            prefs[KEY_LOW_ENABLED]         = config.lowAlarmEnabled
            prefs[KEY_LOW_THRESHOLD]       = config.lowThreshold
            prefs[KEY_OVERHEAT_ENABLED]    = config.overheatAlarmEnabled
            prefs[KEY_OVERHEAT_THRESHOLD]  = config.overheatThresholdCelsius
            prefs[KEY_ALARM_SOUND]         = config.alarmSoundName
        }
    }

    suspend fun setDesignCapacity(mah: Int) {
        context.dataStore.edit { it[KEY_DESIGN_CAPACITY] = mah }
    }

    suspend fun incrementStreak() {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_STREAK_DAYS] ?: 0
            prefs[KEY_STREAK_DAYS] = current + 1
            prefs[KEY_LAST_STREAK_DATE] = System.currentTimeMillis()
        }
    }

    suspend fun resetStreak() {
        context.dataStore.edit {
            it[KEY_STREAK_DAYS] = 0
        }
    }

    suspend fun markGhostDrainShown() {
        context.dataStore.edit {
            it[KEY_GHOST_DRAIN_LAST] = System.currentTimeMillis()
        }
    }

    suspend fun getGhostDrainLastShownMs(): Long {
        var value = 0L
        context.dataStore.data.map { it[KEY_GHOST_DRAIN_LAST] ?: 0L }.collect {
            value = it
            return@collect
        }
        return value
    }
}