package com.immunoblockers.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("settings")
data class ThemeSettings(
    val theme: String = "system", val palette: String = "default", val seed: Long = 0xFF146B55,
    val reducedMotion: Boolean = false, val privateNotifications: Boolean = false, val reminders: Boolean = true,
    val boldText: Boolean = true, val fieldStyle: String = "filled", val corners: String = "rounded",
    val todayBlocks: String = "progress,next,journal", val compactJournal: Boolean = false,
    val privateWidgets: Boolean = false
)
class SettingsRepository(private val context: Context) {
    private fun read(p: Preferences) = ThemeSettings(
        p[THEME] ?: "system", p[PALETTE] ?: "default", p[SEED] ?: 0xFF146B55,
        p[REDUCED] ?: false, p[PRIVATE] ?: false, p[REMINDERS] ?: true,
        p[BOLD] ?: true, p[FIELDS] ?: "filled", p[CORNERS] ?: "rounded",
        p[BLOCKS] ?: "progress,next,journal", p[COMPACT] ?: false, p[WIDGET_PRIVATE] ?: false
    )
    val flow = context.settingsStore.data.map(::read)
    suspend fun update(transform: (ThemeSettings) -> ThemeSettings) {
        context.settingsStore.edit { p ->
            val s = transform(read(p))
            p[THEME]=s.theme; p[PALETTE]=s.palette; p[SEED]=s.seed; p[REDUCED]=s.reducedMotion
            p[PRIVATE]=s.privateNotifications; p[REMINDERS]=s.reminders; p[BOLD]=s.boldText
            p[FIELDS]=s.fieldStyle; p[CORNERS]=s.corners; p[BLOCKS]=s.todayBlocks
            p[COMPACT]=s.compactJournal; p[WIDGET_PRIVATE]=s.privateWidgets
        }
    }
    suspend fun save(s: ThemeSettings) = update { s }
    companion object {
        val THEME = stringPreferencesKey("theme"); val PALETTE = stringPreferencesKey("palette"); val SEED = longPreferencesKey("seed")
        val REDUCED = booleanPreferencesKey("reduced"); val PRIVATE = booleanPreferencesKey("private"); val REMINDERS = booleanPreferencesKey("reminders")
        val BOLD = booleanPreferencesKey("bold"); val FIELDS = stringPreferencesKey("fields"); val CORNERS = stringPreferencesKey("corners")
        val BLOCKS = stringPreferencesKey("today_blocks"); val COMPACT = booleanPreferencesKey("compact_journal"); val WIDGET_PRIVATE = booleanPreferencesKey("private_widgets")
    }
}
