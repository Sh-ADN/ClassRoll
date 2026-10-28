package com.aistudio.classroll.jkmxlp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    companion object {
        val ACADEMIC_YEAR = stringPreferencesKey("academic_year")
        val APP_THEME = stringPreferencesKey("app_theme")
        val LAST_BACKUP_TIMESTAMP = longPreferencesKey("last_backup_timestamp")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_TIME = stringPreferencesKey("reminder_time") // "HH:mm", 24h
        val CUSTOM_HOLIDAYS = stringSetPreferencesKey("custom_holidays")
        val OFFLINE_MODE = booleanPreferencesKey("offline_mode")
        val FINE_PER_ABSENT_DAY = intPreferencesKey("fine_per_absent_day")
    }

    val appThemeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[APP_THEME] ?: "SYSTEM"
    }

    val academicYearFlow: Flow<String> = context.dataStore.data.map { preferences ->
        val stored = preferences[ACADEMIC_YEAR]
        if (!stored.isNullOrBlank()) {
            stored
        } else {
            java.text.SimpleDateFormat("yyyy", java.util.Locale.US).format(java.util.Date())
        }
    }

    val lastBackupTimestampFlow: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[LAST_BACKUP_TIMESTAMP] ?: 0L
    }

    val reminderEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[REMINDER_ENABLED] ?: false
    }

    val reminderTimeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[REMINDER_TIME] ?: "10:00"
    }

    val holidaysFlow: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[CUSTOM_HOLIDAYS] ?: emptySet()
    }

    val offlineModeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[OFFLINE_MODE] ?: false
    }

    val finePerAbsentDayFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[FINE_PER_ABSENT_DAY] ?: 5
    }

    suspend fun updateFinePerAbsentDay(amount: Int) {
        context.dataStore.edit { preferences ->
            preferences[FINE_PER_ABSENT_DAY] = amount.coerceAtLeast(0)
        }
    }

    suspend fun updateAcademicYear(year: String) {
        context.dataStore.edit { preferences ->
            preferences[ACADEMIC_YEAR] = year
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
            val current = preferences[CUSTOM_HOLIDAYS] ?: emptySet()
            if (current.contains(date)) {
                preferences[CUSTOM_HOLIDAYS] = current - date
            } else {
                preferences[CUSTOM_HOLIDAYS] = current + date
            }
        }
    }

    suspend fun updateOfflineMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[OFFLINE_MODE] = enabled
        }
    }

    suspend fun setHolidays(dates: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[CUSTOM_HOLIDAYS] = dates
        }
    }
}
