package com.immunoblockers.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("settings")
data class ThemeSettings(val theme: String = "system", val palette: String = "default", val seed: Long = 0xFF146B55, val reducedMotion: Boolean = false, val privateNotifications: Boolean = false, val reminders: Boolean = true)
class SettingsRepository(private val context: Context) {
    val flow = context.settingsStore.data.map { p -> ThemeSettings(p[THEME] ?: "system", p[PALETTE] ?: "default", p[SEED] ?: 0xFF146B55, p[REDUCED] ?: false, p[PRIVATE] ?: false, p[REMINDERS] ?: true) }
    suspend fun save(s: ThemeSettings) { context.settingsStore.edit { p -> p[THEME]=s.theme; p[PALETTE]=s.palette; p[SEED]=s.seed; p[REDUCED]=s.reducedMotion; p[PRIVATE]=s.privateNotifications; p[REMINDERS]=s.reminders } }
    companion object {
        val THEME = stringPreferencesKey("theme"); val PALETTE = stringPreferencesKey("palette"); val SEED = longPreferencesKey("seed")
        val REDUCED = booleanPreferencesKey("reduced"); val PRIVATE = booleanPreferencesKey("private"); val REMINDERS = booleanPreferencesKey("reminders")
    }
}
