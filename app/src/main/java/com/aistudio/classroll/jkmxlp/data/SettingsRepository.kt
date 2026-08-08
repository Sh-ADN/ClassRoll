package com.aistudio.classroll.jkmxlp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    companion object {
        val WEB_APP_URL = stringPreferencesKey("web_app_url")
        val ACADEMIC_YEAR = stringPreferencesKey("academic_year")
        val APPS_SCRIPT_TOKEN = stringPreferencesKey("apps_script_token")
        val APP_THEME = stringPreferencesKey("app_theme")
    }

    val appThemeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[APP_THEME] ?: "SYSTEM"
    }

    val webAppUrlFlow: Flow<String> = context.dataStore.data.map {
        "https://script.google.com/macros/s/AKfycbzTDiNJh4LEaIah19SVFaf6JlESbW5tf2ElwaMULTDENIAlXFOFI4QAXEmV1nYwrVdA/exec"
    }

    val academicYearFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[ACADEMIC_YEAR] ?: ""
    }

    val appsScriptTokenFlow: Flow<String> = context.dataStore.data.map {
        "Q8tZ2nLm5vX9aH1kPc4RrW7yNd3Fs6UbJe0MgKt8Vx2ApCn9YqLh5Di7SwBuE4oNz"
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
}
