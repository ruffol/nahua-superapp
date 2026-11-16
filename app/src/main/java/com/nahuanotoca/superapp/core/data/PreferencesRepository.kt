package com.nahuanotoca.superapp.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val notificationsEnabled: Boolean = true,
    val biometricsEnabled: Boolean = false,
)

class PreferencesRepository(private val context: Context) {

    private val store = context.appPreferencesStore

    val preferences: Flow<AppPreferences> = store.data.map { prefs ->
        AppPreferences(
            themeMode = prefs[THEME_KEY]?.let { stored ->
                ThemeMode.entries.firstOrNull { it.name == stored }
            } ?: ThemeMode.SYSTEM,
            notificationsEnabled = prefs[NOTIFICATIONS_KEY] ?: true,
            biometricsEnabled = prefs[BIOMETRICS_KEY] ?: false,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME_KEY] = mode.name }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        store.edit { it[NOTIFICATIONS_KEY] = enabled }
    }

    suspend fun setBiometricsEnabled(enabled: Boolean) {
        store.edit { it[BIOMETRICS_KEY] = enabled }
    }

    private companion object {
        val THEME_KEY = stringPreferencesKey("theme_mode")
        val NOTIFICATIONS_KEY = booleanPreferencesKey("notifications_enabled")
        val BIOMETRICS_KEY = booleanPreferencesKey("biometrics_enabled")
    }
}
