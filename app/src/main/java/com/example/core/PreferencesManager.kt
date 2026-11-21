package com.example.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "projectx_prefs")

class PreferencesManager(private val context: Context) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val RECENT_TOOLS = stringPreferencesKey("recent_tools")
        val GUEST_NAME = stringPreferencesKey("guest_name")
    }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.THEME_MODE] ?: ThemeMode.DARK.name
        ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.DARK
    }

    val languageCodeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.LANGUAGE_CODE] ?: "en"
    }

    val onboardingDoneFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.ONBOARDING_DONE] ?: false
    }

    val recentToolsFlow: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.RECENT_TOOLS].orEmpty()
        if (raw.isBlank()) emptyList() else raw.split("|").filter { it.isNotBlank() }
    }

    val guestNameFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.GUEST_NAME] ?: "Guest Creator"
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setLanguageCode(code: String) {
        context.dataStore.edit { it[Keys.LANGUAGE_CODE] = code }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    suspend fun setGuestName(name: String) {
        val clean = name.trim().take(40).ifBlank { "Guest Creator" }
        context.dataStore.edit { it[Keys.GUEST_NAME] = clean }
    }

    suspend fun recordToolUsed(toolId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.RECENT_TOOLS]
                ?.split("|")
                ?.filter { it.isNotBlank() && it != toolId }
                ?: emptyList()
            val updated = (listOf(toolId) + current).take(8)
            prefs[Keys.RECENT_TOOLS] = updated.joinToString("|")
        }
    }

    suspend fun clearAllPreferences() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.RECENT_TOOLS)
            prefs.remove(Keys.GUEST_NAME)
        }
    }
}
