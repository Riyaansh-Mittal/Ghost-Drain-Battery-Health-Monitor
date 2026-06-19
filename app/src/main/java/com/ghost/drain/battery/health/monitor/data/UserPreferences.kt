package com.ghost.drain.battery.health.monitor.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

object PreferencesKeys {
    val ONBOARDING_DONE  = booleanPreferencesKey("onboarding_done")
    val USER_IDENTITY    = stringPreferencesKey("user_identity")
}

class UserPreferences(private val context: Context) {

    val isOnboardingDone: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[PreferencesKeys.ONBOARDING_DONE] ?: false }

    val userIdentity: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[PreferencesKeys.USER_IDENTITY] ?: "" }

    suspend fun completeOnboarding(identity: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.ONBOARDING_DONE] = true
            prefs[PreferencesKeys.USER_IDENTITY]   = identity
        }
    }
}