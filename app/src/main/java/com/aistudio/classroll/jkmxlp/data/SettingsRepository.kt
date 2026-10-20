package com.aistudio.classroll.jkmxlp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aistudio.classroll.jkmxlp.ui.screens.Routes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = Routes.Settings)

class SettingsRepository(private val context: Context) {

    val webAppUrlFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[WEB_APP_URL] ?: ""
    }

    val academicYearFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[ACADEMIC_YEAR] ?: "2025"
    }

    val appsScriptTokenFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[APPS_SCRIPT_TOKEN] ?: ""
    }

    val appThemeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[APP_THEME] ?: "system"
    }

    val lastBackupTimestampFlow: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[LAST_BACKUP_TIMESTAMP] ?: 0L
    }

    val reminderEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[REMINDER_ENABLED] ?: false
    }

    val reminderTimeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[REMINDER_TIME] ?: "09:00"
    }

    val holidaysFlow: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[CUSTOM_HOLIDAYS] ?: emptySet()
    }

    val offlineModeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[OFFLINE_MODE] ?: false
    }

    suspend fun updateWebAppUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[WEB_APP_URL] = url
        }
    }

    suspend fun updateAcademicYear(year: String) {
        context.dataStore.edit { preferences ->
            preferences[ACADEMIC_YEAR] = year
        }
    }

    suspend fun updateAppsScriptToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[APPS_SCRIPT_TOKEN] = token
        }
    }

    suspend fun updateAppTheme(theme: String) {
        context.dataStore.edit { preferences ->
            preferences[APP_THEME] = theme
        }
    }

    suspend fun updateLastBackupTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_BACKUP_TIMESTAMP] = timestamp
        }
    }

    suspend fun updateReminderEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[REMINDER_ENABLED] = enabled
        }
    }

    suspend fun updateReminderTime(time: String) {
        context.dataStore.edit { preferences ->
            preferences[REMINDER_TIME] = time
        }
    }

    suspend fun toggleHoliday(date: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[CUSTOM_HOLIDAYS]?.toMutableSet() ?: mutableSetOf()
            if (current.contains(date)) {
                current.remove(date)
            } else {
                current.add(date)
            }
            preferences[CUSTOM_HOLIDAYS] = current
        }
    }

    suspend fun setHolidays(dates: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[CUSTOM_HOLIDAYS] = dates
        }
    }

    suspend fun updateOfflineMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[OFFLINE_MODE] = enabled
        }
    }

    companion object {
        val WEB_APP_URL = stringPreferencesKey("web_app_url")
        val ACADEMIC_YEAR = stringPreferencesKey("academic_year")
        val APPS_SCRIPT_TOKEN = stringPreferencesKey("apps_script_token")
        val APP_THEME = stringPreferencesKey("app_theme")
        val LAST_BACKUP_TIMESTAMP = longPreferencesKey("last_backup_timestamp")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_TIME = stringPreferencesKey("reminder_time")
        val CUSTOM_HOLIDAYS = stringSetPreferencesKey("custom_holidays")
        val OFFLINE_MODE = booleanPreferencesKey("offline_mode")
    }
}
