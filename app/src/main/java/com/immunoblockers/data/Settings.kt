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
    val privateWidgets: Boolean = false, val interfaceStyle:String = "glass", val roundedFont:Boolean = true, val confirmWidgetActions:Boolean = true, val showHelpers:Boolean=true, val nickname:String="", val avatarPath:String="", val askWellbeing:Boolean=false, val surveyHours:Int=2, val widgetOpacity:Int=180, val widgetTheme:String="system", val widgetImage:String="", val wallpaperPath:String=""
)
class SettingsRepository(private val context: Context) {
    private fun read(p: Preferences) = ThemeSettings(
        p[THEME] ?: "system", p[PALETTE] ?: "default", p[SEED] ?: 0xFF146B55,
        p[REDUCED] ?: false, p[PRIVATE] ?: false, p[REMINDERS] ?: true,
        p[BOLD] ?: true, p[FIELDS] ?: "filled", p[CORNERS] ?: "rounded",
        p[BLOCKS] ?: "progress,next,journal", p[COMPACT] ?: false, p[WIDGET_PRIVATE] ?: false, p[STYLE] ?: "glass", p[ROUND_FONT] ?: true, p[CONFIRM_WIDGET] ?: true, p[HELPERS] ?: true, p[NICKNAME] ?: "", p[AVATARPATH] ?: "", p[ASKWELLBEING] ?: false, p[SURVEYHOURS] ?: 2, p[WIDGETOPACITY] ?: 180, p[WIDGETTHEME] ?: "system", p[WIDGETIMAGE] ?: "", p[WALLPAPER] ?: p[WIDGETIMAGE] ?: ""
    )
    val flow = context.settingsStore.data.map(::read)
    suspend fun update(transform: (ThemeSettings) -> ThemeSettings) {
        context.settingsStore.edit { p ->
            val s = transform(read(p))
            require(s.nickname.length<=32)
            require(s.surveyHours in listOf(1,2,4,6))
            require(s.widgetOpacity in 40..255)
            p[THEME]=s.theme; p[PALETTE]=s.palette; p[SEED]=s.seed; p[REDUCED]=s.reducedMotion
            p[PRIVATE]=s.privateNotifications; p[REMINDERS]=s.reminders; p[BOLD]=s.boldText
            p[FIELDS]=s.fieldStyle; p[CORNERS]=s.corners; p[BLOCKS]=s.todayBlocks
            p[COMPACT]=s.compactJournal; p[WIDGET_PRIVATE]=s.privateWidgets; p[STYLE]=s.interfaceStyle; p[ROUND_FONT]=s.roundedFont; p[CONFIRM_WIDGET]=s.confirmWidgetActions; p[HELPERS]=s.showHelpers; p[NICKNAME]=s.nickname; p[AVATARPATH]=s.avatarPath; p[ASKWELLBEING]=s.askWellbeing; p[SURVEYHOURS]=s.surveyHours; p[WIDGETOPACITY]=s.widgetOpacity; p[WIDGETTHEME]=s.widgetTheme; p[WIDGETIMAGE]=s.widgetImage; p[WALLPAPER]=s.wallpaperPath
        }
    }
    suspend fun save(s: ThemeSettings) = update { s }
    companion object {
val NICKNAME=stringPreferencesKey("nickname")
val AVATARPATH=stringPreferencesKey("avatarPath")
val ASKWELLBEING=booleanPreferencesKey("askWellbeing")
val SURVEYHOURS=intPreferencesKey("surveyHours")
val WIDGETOPACITY=intPreferencesKey("widgetOpacity")
val WIDGETTHEME=stringPreferencesKey("widgetTheme")
val WALLPAPER=stringPreferencesKey("wallpaperPath")
val WIDGETIMAGE=stringPreferencesKey("widgetImage")
        val THEME = stringPreferencesKey("theme"); val PALETTE = stringPreferencesKey("palette"); val SEED = longPreferencesKey("seed")
        val REDUCED = booleanPreferencesKey("reduced"); val PRIVATE = booleanPreferencesKey("private"); val REMINDERS = booleanPreferencesKey("reminders")
        val CONFIRM_WIDGET=booleanPreferencesKey("confirm_widget");val HELPERS=booleanPreferencesKey("helpers")
        val STYLE=stringPreferencesKey("interface_style");val ROUND_FONT=booleanPreferencesKey("round_font")
        val BOLD = booleanPreferencesKey("bold"); val FIELDS = stringPreferencesKey("fields"); val CORNERS = stringPreferencesKey("corners")
        val BLOCKS = stringPreferencesKey("today_blocks"); val COMPACT = booleanPreferencesKey("compact_journal"); val WIDGET_PRIVATE = booleanPreferencesKey("private_widgets")
    }
}
